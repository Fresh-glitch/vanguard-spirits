package io.github.freshglitch.vanguardspirits.entity

import io.github.freshglitch.vanguardspirits.registry.ModParticles
import io.github.freshglitch.vanguardspirits.registry.ModSounds
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

/**
 * The thing on the altar.
 *
 * One entity in three states rather than a block that swaps for a mob: dormant
 * it is a statue with no AI, waking it plays a fixed sequence nothing can
 * interrupt, and awake it hunts. The model never changes, so the statue that
 * was standing there *is* the thing now walking at you.
 *
 * Waking is deliberately slow -- [WAKE_TICKS] is three and a half seconds. The
 * point is that the player hears it start and has time to understand what is
 * about to happen, which is also long enough to run.
 */
class StoneSentinel(type: EntityType<out StoneSentinel>, level: Level) : Monster(type, level) {

	/**
	 * The spot it is set to watch, in world coordinates.
	 *
	 * Set by the structure to the mouth of the crypt stairwell. Null for one
	 * spawned any other way, which then wakes when someone approaches it instead.
	 */
	private var wardPos: BlockPos? = null

	/** Server-side cooldowns, in ticks. Not synced -- the client never asks. */
	private var slamCooldown = 0
	private var sweepCooldown = 0

	override fun registerGoals() {
		goalSelector.addGoal(0, FloatGoal(this))
		goalSelector.addGoal(1, MeleeAttackGoal(this, 1.0, true))
		goalSelector.addGoal(6, WaterAvoidingRandomStrollGoal(this, 0.7))
		goalSelector.addGoal(7, LookAtPlayerGoal(this, Player::class.java, 16.0f))
		goalSelector.addGoal(8, RandomLookAroundGoal(this))

		targetSelector.addGoal(1, HurtByTargetGoal(this))
		targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
	}

	override fun defineSynchedData(builder: SynchedEntityData.Builder) {
		super.defineSynchedData(builder)
		builder.define(DATA_DORMANT, true)
		builder.define(DATA_WAKE, 0)
		builder.define(DATA_ATTACK_KIND, NO_ATTACK)
		builder.define(DATA_ATTACK_TICK, 0)
	}

	// ------------------------------------------------------------------ state

	var isDormant: Boolean
		get() = entityData.get(DATA_DORMANT)
		private set(value) = entityData.set(DATA_DORMANT, value)

	var wakeTick: Int
		get() = entityData.get(DATA_WAKE)
		private set(value) = entityData.set(DATA_WAKE, value)

	val isWaking: Boolean
		get() = wakeTick in 1..WAKE_TICKS

	var attackKind: Byte
		get() = entityData.get(DATA_ATTACK_KIND)
		private set(value) = entityData.set(DATA_ATTACK_KIND, value)

	var attackTick: Int
		get() = entityData.get(DATA_ATTACK_TICK)
		private set(value) = entityData.set(DATA_ATTACK_TICK, value)

	fun setWard(pos: BlockPos) {
		wardPos = pos
	}

	/** Called by the structure, so a freshly generated one starts as stone. */
	fun sleep() {
		isDormant = true
		wakeTick = 0
		isNoAi = true
	}

	// ------------------------------------------------------------------ ticks

	override fun tick() {
		super.tick()

		val level = level()
		if (level !is ServerLevel) return

		when {
			isDormant -> if (tickCount % WATCH_INTERVAL == 0) watch(level)
			isWaking -> advanceWake(level)
			else -> {
				if (slamCooldown > 0) slamCooldown--
				if (sweepCooldown > 0) sweepCooldown--
				if (attackKind != NO_ATTACK) advanceAttack(level)
			}
		}
	}

	/**
	 * Wakes if anyone has come close enough to whatever it is guarding.
	 *
	 * Measured from [wardPos] rather than from the sentinel, so the trigger is
	 * the stairwell the player is heading for -- crossing the sanctum toward the
	 * crypt is what sets it off, not walking past the altar.
	 */
	private fun watch(level: ServerLevel) {
		val ward = wardPos ?: blockPosition()
		val victim = level.getNearestPlayer(
			ward.x + 0.5,
			ward.y + 0.5,
			ward.z + 0.5,
			TRIGGER_RANGE,
			/* filter = */ null,
		) ?: return

		if (victim.isSpectator || victim.isCreative) return
		beginWake()
		target = victim
	}

	/** Starts the sequence. Everything after this is driven by [advanceWake]. */
	fun beginWake() {
		if (!isDormant) return
		isDormant = false
		wakeTick = 1
		isNoAi = true
	}

	/**
	 * The waking sequence.
	 *
	 * Cues are keyed off exact ticks rather than fractions so the audio and the
	 * animation stay locked together -- the model reads the same counter, so a
	 * sound and the movement that causes it cannot drift apart.
	 */
	private fun advanceWake(level: ServerLevel) {
		val t = wakeTick

		when (t) {
			TWITCH_AT -> {
				// The first sign: one sharp turn of the head, and grit falling.
				say(ModSounds.SENTINEL_STIR, 0.9f, 0.9f)
				dust(level, y + bbHeight * 0.85, 12, 0.4)
			}
			STIR_AT -> {
				say(ModSounds.SENTINEL_STIR, 1.2f, 0.5f)
				say(ModSounds.SENTINEL_RUMBLE, 1.6f, 0.5f)
				dust(level, y + bbHeight * 0.5, 30, 0.8)
			}
			ROAR_AT -> {
				say(ModSounds.SENTINEL_ROAR, 2.0f, 0.7f)
				say(ModSounds.SENTINEL_BELLOW, 1.6f, 0.6f)
				burst(level)
			}
		}

		// A pulse every half second through the middle of the sequence, so the
		// rumble reads as continuous rather than as three separate noises.
		if (t in STIR_AT..ROAR_AT && (t - STIR_AT) % RUMBLE_EVERY == 0) {
			say(ModSounds.SENTINEL_RUMBLE, 1.2f, 0.45f)
			dust(level, y + 0.2, 8, 0.6)
		}

		// Ground shake. There is no camera-shake API in vanilla, so the shake is
		// literal: nearby players get nudged, which moves their view.
		if (t >= STIR_AT) {
			val ramp = Mth.clamp((t - STIR_AT).toFloat() / (ROAR_AT - STIR_AT).toFloat(), 0.0f, 1.0f)
			shake(level, SHAKE_MIN + (SHAKE_MAX - SHAKE_MIN) * ramp)
		}

		wakeTick = t + 1
		if (wakeTick > WAKE_TICKS) {
			wakeTick = 0
			isNoAi = false
		}
	}

	// ---------------------------------------------------------------- attacks

	/**
	 * Picks a special attack when one is off cooldown and the target is in range.
	 *
	 * Called from the melee goal's swing rather than on a timer, so the sentinel
	 * has to actually reach the player before anything happens.
	 */
	private fun considerSpecial(victim: LivingEntity): Boolean {
		if (attackKind != NO_ATTACK) return true

		val reach = distanceToSqr(victim)
		if (slamCooldown <= 0 && reach <= SLAM_TRIGGER_SQ) {
			begin(SLAM)
			return true
		}
		if (sweepCooldown <= 0 && reach <= SWEEP_TRIGGER_SQ) {
			begin(SWEEP)
			return true
		}
		return false
	}

	private fun begin(kind: Byte) {
		attackKind = kind
		attackTick = 0
		navigation.stop()
		say(if (kind == SLAM) ModSounds.SENTINEL_STIR else ModSounds.SENTINEL_SWEEP, 1.0f, 0.7f)
	}

	private fun advanceAttack(level: ServerLevel) {
		val t = attackTick
		val kind = attackKind
		val impactAt = if (kind == SLAM) SLAM_WINDUP else SWEEP_WINDUP
		val endAt = impactAt + RECOVER

		// Rooted through the wind-up. This is what makes the attacks readable:
		// it stops, it raises, then it lands, and a player who is watching has
		// time to get out of the way.
		if (t < impactAt) navigation.stop()

		if (t == impactAt) {
			if (kind == SLAM) slam(level) else sweep(level)
		}

		attackTick = t + 1
		if (attackTick > endAt) {
			attackKind = NO_ATTACK
			attackTick = 0
			if (kind == SLAM) slamCooldown = SLAM_COOLDOWN else sweepCooldown = SWEEP_COOLDOWN
		}
	}

	/** Both fists into the floor: everything around it is thrown outward and up. */
	private fun slam(level: ServerLevel) {
		say(ModSounds.SENTINEL_SLAM, 1.6f, 0.7f)
		say(ModSounds.SENTINEL_RUMBLE, 1.4f, 0.5f)
		shake(level, SHAKE_MAX)
		ring(level, SLAM_RADIUS)

		for (victim in around(SLAM_RADIUS)) {
			victim.hurtServer(level, damageSources().mobAttack(this), SLAM_DAMAGE)
			throwBack(victim, SLAM_PUSH, SLAM_LIFT)
		}
	}

	/**
	 * A backhand across the front arc.
	 *
	 * Cheaper and faster than the slam, and it only covers what the sentinel is
	 * facing -- circling behind it is the counter.
	 */
	private fun sweep(level: ServerLevel) {
		say(ModSounds.SENTINEL_SWEEP, 1.3f, 0.6f)
		dust(level, y + bbHeight * 0.5, 20, 1.2)

		val facing = yRot * Mth.DEG_TO_RAD
		val fx = -Mth.sin(facing.toDouble())
		val fz = Mth.cos(facing.toDouble())

		for (victim in around(SWEEP_RADIUS)) {
			val dx = victim.x - x
			val dz = victim.z - z
			val len = Mth.sqrt((dx * dx + dz * dz).toFloat()).toDouble()
			if (len > 0.001 && (dx / len * fx + dz / len * fz) < SWEEP_ARC) continue

			victim.hurtServer(level, damageSources().mobAttack(this), SWEEP_DAMAGE)
			throwBack(victim, SWEEP_PUSH, SWEEP_LIFT)
		}
	}

	private fun around(radius: Double): List<LivingEntity> =
		level().getEntitiesOfClass(LivingEntity::class.java, boundingBox.inflate(radius)) {
			it !== this && it.isAlive && it !is StoneSentinel
		}

	private fun throwBack(victim: LivingEntity, push: Double, lift: Double) {
		val dx = victim.x - x
		val dz = victim.z - z
		val len = Mth.sqrt((dx * dx + dz * dz).toFloat()).toDouble()
		val nx = if (len > 0.001) dx / len else 0.0
		val nz = if (len > 0.001) dz / len else 1.0

		victim.push(nx * push, lift, nz * push)
		victim.hurtMarked = true
	}

	/**
	 * Suppresses the ordinary swing while a special is winding up, and takes the
	 * chance to start one.
	 */
	override fun doHurtTarget(level: ServerLevel, victim: net.minecraft.world.entity.Entity): Boolean {
		if (victim is LivingEntity && considerSpecial(victim)) return false
		return super.doHurtTarget(level, victim)
	}

	// ------------------------------------------------------------ presentation

	private fun say(sound: SoundEvent, volume: Float, pitch: Float) {
		level().playSound(null, x, y, z, sound, SoundSource.HOSTILE, volume, pitch)
	}

	/**
	 * Nudges nearby players in a random direction.
	 *
	 * This is the earth shaking. Vanilla has no camera-shake hook, so the only
	 * way to move the view is to move the player -- small enough that it does
	 * not fight their control, big enough to feel.
	 */
	private fun shake(level: ServerLevel, strength: Double) {
		for (player in level.players()) {
			if (player.isSpectator || player.distanceToSqr(this) > SHAKE_RANGE_SQ) continue
			val angle = random.nextDouble() * Mth.TWO_PI
			player.push(Mth.cos(angle) * strength, 0.0, Mth.sin(angle) * strength)
			player.hurtMarked = true
		}
	}

	private fun dust(level: ServerLevel, atY: Double, count: Int, spread: Double) {
		level.sendParticles(
			BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_BRICKS.defaultBlockState()),
			x, atY, z,
			count,
			spread, spread * 0.5, spread,
			0.1,
		)
	}

	private fun burst(level: ServerLevel) {
		dust(level, y + bbHeight * 0.4, 90, 1.6)
		level.sendParticles(
			ModParticles.MEMORY_MOTE,
			x, y + bbHeight * 0.6, z,
			70,
			bbWidth.toDouble(), bbHeight * 0.45, bbWidth.toDouble(),
			0.0,
		)
		level.sendParticles(
			ModParticles.ECHO_RUNE,
			x, y + bbHeight * 0.8, z,
			8,
			0.8, 0.5, 0.8,
			0.0,
		)
	}

	/** A ring of debris at the sentinel's feet, marking exactly what the slam hit. */
	private fun ring(level: ServerLevel, radius: Double) {
		val steps = 40
		for (i in 0 until steps) {
			val angle = i.toDouble() / steps * Mth.TWO_PI
			level.sendParticles(
				BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_TILES.defaultBlockState()),
				x + Mth.cos(angle) * radius,
				y + 0.2,
				z + Mth.sin(angle) * radius,
				2,
				0.1, 0.25, 0.1,
				0.15,
			)
		}
	}

	override fun playStepSound(pos: BlockPos, state: BlockState) {
		playSound(ModSounds.SENTINEL_STEP, 1.0f, 0.8f + random.nextFloat() * 0.15f)

		val level = level()
		if (level is ServerLevel) dust(level, y + 0.1, 4, 0.35)
	}

	override fun getHurtSound(source: DamageSource): SoundEvent = ModSounds.SENTINEL_HURT

	override fun getDeathSound(): SoundEvent = ModSounds.SENTINEL_DEATH

	// -------------------------------------------------------------- durability

	/**
	 * Hitting a statue wakes it rather than chipping it, and nothing lands while
	 * it is getting up.
	 *
	 * Letting a player whittle one down before it ever moves would waste the
	 * whole encounter, and letting them burst it during the waking sequence
	 * would waste the sequence.
	 */
	override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
		if (isDormant) {
			beginWake()
			(source.entity as? Player)?.let { target = it }
			return false
		}
		if (isWaking) return false
		return super.hurtServer(level, source, amount)
	}

	/** Placed deliberately; it should still be there when the player comes back. */
	override fun removeWhenFarAway(distance: Double): Boolean = false

	override fun addAdditionalSaveData(output: ValueOutput) {
		super.addAdditionalSaveData(output)
		output.putBoolean(TAG_DORMANT, isDormant)
		wardPos?.let {
			output.putInt(TAG_WARD_X, it.x)
			output.putInt(TAG_WARD_Y, it.y)
			output.putInt(TAG_WARD_Z, it.z)
		}
	}

	override fun readAdditionalSaveData(input: ValueInput) {
		super.readAdditionalSaveData(input)
		isDormant = input.getBooleanOr(TAG_DORMANT, false)
		isNoAi = isDormant

		// A sentinel saved mid-sequence comes back fully awake rather than
		// resuming: the cues would be out of step with the animation anyway.
		wakeTick = 0

		val x = input.getInt(TAG_WARD_X)
		val y = input.getInt(TAG_WARD_Y)
		val z = input.getInt(TAG_WARD_Z)
		wardPos = if (x.isPresent && y.isPresent && z.isPresent) {
			BlockPos(x.get(), y.get(), z.get())
		} else {
			null
		}
	}

	companion object {
		private val DATA_DORMANT: EntityDataAccessor<Boolean> =
			SynchedEntityData.defineId(StoneSentinel::class.java, EntityDataSerializers.BOOLEAN)
		private val DATA_WAKE: EntityDataAccessor<Int> =
			SynchedEntityData.defineId(StoneSentinel::class.java, EntityDataSerializers.INT)
		private val DATA_ATTACK_KIND: EntityDataAccessor<Byte> =
			SynchedEntityData.defineId(StoneSentinel::class.java, EntityDataSerializers.BYTE)
		private val DATA_ATTACK_TICK: EntityDataAccessor<Int> =
			SynchedEntityData.defineId(StoneSentinel::class.java, EntityDataSerializers.INT)

		private const val TAG_DORMANT = "Dormant"
		private const val TAG_WARD_X = "WardX"
		private const val TAG_WARD_Y = "WardY"
		private const val TAG_WARD_Z = "WardZ"

		const val NO_ATTACK: Byte = 0
		const val SLAM: Byte = 1
		const val SWEEP: Byte = 2

		/** How close to the guarded spot a player has to get. */
		private const val TRIGGER_RANGE = 6.0

		/** Ticks between checks while dormant. A statue can afford to be slow. */
		private const val WATCH_INTERVAL = 10

		// ---- waking. The model reads these too, via StoneSentinelModel. ----

		/** Three and a half seconds, start to finish. */
		const val WAKE_TICKS: Int = 70

		/** The head twitch: the first thing that moves. */
		const val TWITCH_AT: Int = 4

		/** The body starts to straighten and the rumble begins. */
		const val STIR_AT: Int = 18

		/** Head snaps up, and it announces itself. */
		const val ROAR_AT: Int = 48

		private const val RUMBLE_EVERY = 10

		private const val SHAKE_MIN = 0.012
		private const val SHAKE_MAX = 0.055
		private const val SHAKE_RANGE_SQ = 24.0 * 24.0

		// ---- attacks ----

		const val SLAM_WINDUP: Int = 20
		const val SWEEP_WINDUP: Int = 8
		const val RECOVER: Int = 10

		private const val SLAM_COOLDOWN = 140
		private const val SWEEP_COOLDOWN = 70

		private const val SLAM_TRIGGER_SQ = 6.0 * 6.0
		private const val SWEEP_TRIGGER_SQ = 4.5 * 4.5

		private const val SLAM_RADIUS = 5.0
		private const val SWEEP_RADIUS = 4.0

		private const val SLAM_DAMAGE = 14.0f
		private const val SWEEP_DAMAGE = 9.0f

		private const val SLAM_PUSH = 1.5
		private const val SLAM_LIFT = 0.85
		private const val SWEEP_PUSH = 1.1
		private const val SWEEP_LIFT = 0.35

		/** Dot-product threshold for the sweep's front arc -- roughly 120 degrees. */
		private const val SWEEP_ARC = -0.5

		/**
		 * A wall that followed you.
		 *
		 * The first build gave it 60 health and armour 12, which a player in full
		 * diamond cut through in seconds. Health and toughness are what buy the
		 * fight time -- toughness in particular, because it blunts exactly the
		 * high-damage hits a diamond sword lands.
		 */
		fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 150.0)
			.add(Attributes.MOVEMENT_SPEED, 0.30)
			.add(Attributes.ATTACK_DAMAGE, 12.0)
			.add(Attributes.ATTACK_KNOCKBACK, 2.0)
			.add(Attributes.ARMOR, 15.0)
			.add(Attributes.ARMOR_TOUGHNESS, 8.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 40.0)
			.add(Attributes.STEP_HEIGHT, 1.0)
	}
}
