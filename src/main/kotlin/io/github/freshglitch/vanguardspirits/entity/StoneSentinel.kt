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
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation
import net.minecraft.world.entity.ai.navigation.PathNavigation
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.PathNavigationRegion
import net.minecraft.world.level.gamerules.GameRules
import net.minecraft.world.level.pathfinder.PathFinder
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator
import net.minecraft.world.phys.AABB
import kotlin.math.abs
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

	/** The altar it was set on. It walks back here once there is nothing to fight. */
	private var homePos: BlockPos? = null

	/** Counts out the fade back to stone. Zero unless it is settling. */
	private var settleTick = 0

	/** How long it has been trying to walk back to the altar. */
	private var homingTicks = 0

	/** Tick of the last blow it actually landed, for telling stuck from merely high. */
	private var lastLanded = 0

	/** Server-side cooldowns, in ticks. Not synced -- the client never asks. */
	private var slamCooldown = 0
	private var sweepCooldown = 0
	private var reckoningCooldown = 0

	/**
	 * Hits taken from somewhere the sentinel cannot answer.
	 *
	 * Two blocks of pillar puts a player above everything it can swing at, and
	 * from up there the fight becomes free. This counts those hits; [RECKONING]
	 * is what it does about them.
	 */
	private var unanswered = 0

	override fun registerGoals() {
		goalSelector.addGoal(0, FloatGoal(this))
		goalSelector.addGoal(1, MeleeAttackGoal(this, 1.0, true))
		// No strolling goal on purpose. A guardian that wanders has nowhere to go
		// back to, and worse, it keeps the navigator permanently busy -- the walk
		// home below would never get a path issued.
		goalSelector.addGoal(7, LookAtPlayerGoal(this, Player::class.java, 16.0f))
		goalSelector.addGoal(8, RandomLookAroundGoal(this))

		targetSelector.addGoal(1, HurtByTargetGoal(this))
		targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
	}

	/**
	 * Pathfinding that knows how tall it actually is.
	 *
	 * [NodeEvaluator.prepare] sets `entityHeight` with `Mth.floor`, so a 2.6
	 * block sentinel is planned for as though it were 2 -- it walks confidently
	 * into two-block gaps and ends up standing inside the blocks. Rounding up
	 * instead makes it path only through openings it genuinely fits.
	 */
	override fun createNavigation(level: Level): PathNavigation = object : GroundPathNavigation(this, level) {
		override fun createPathFinder(maxVisitedNodes: Int): PathFinder {
			nodeEvaluator = object : WalkNodeEvaluator() {
				override fun prepare(region: PathNavigationRegion, mob: Mob) {
					super.prepare(region, mob)
					entityHeight = Mth.ceil(mob.bbHeight)
				}
			}
			return PathFinder(nodeEvaluator, maxVisitedNodes)
		}
	}

	override fun defineSynchedData(builder: SynchedEntityData.Builder) {
		super.defineSynchedData(builder)
		builder.define(DATA_DORMANT, true)
		builder.define(DATA_WAKE, 0)
		builder.define(DATA_ATTACK_KIND, NO_ATTACK)
		builder.define(DATA_ATTACK_TICK, 0)
		builder.define(DATA_GLOW, GLOW_EMBER)
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

	/** 0..100. Drives the eye brightness on the client and nothing else. */
	var glow: Int
		get() = entityData.get(DATA_GLOW)
		private set(value) = entityData.set(DATA_GLOW, value)

	fun setWard(pos: BlockPos) {
		wardPos = pos
	}

	fun setHome(pos: BlockPos) {
		homePos = pos
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
			settleTick > 0 -> settle()
			isDormant -> if (tickCount % WATCH_INTERVAL == 0) watch(level)
			isWaking -> advanceWake(level)
			else -> {
				if (slamCooldown > 0) slamCooldown--
				if (sweepCooldown > 0) sweepCooldown--
				if (reckoningCooldown > 0) reckoningCooldown--

				when {
					attackKind != NO_ATTACK -> advanceAttack(level)
					hasQuarry() -> homingTicks = 0
					else -> goHome()
				}
			}
		}

		updateGlow()
	}

	private fun hasQuarry(): Boolean = target?.isAlive == true

	/**
	 * Walks back to the altar once there is nothing left to fight.
	 *
	 * The ruin is supposed to look untouched when a player returns to it, so a
	 * sentinel that killed someone does not stand around where the fight ended.
	 */
	private fun goHome() {
		val home = homePos ?: return
		val hx = home.x + 0.5
		val hy = home.y.toDouble()
		val hz = home.z + 0.5

		if (distanceToSqr(hx, hy, hz) <= ARRIVED_SQ) {
			arrive(home, hx, hy, hz)
			return
		}

		homingTicks++

		// Re-issued on a timer rather than only when the navigator reports done.
		// A path that stops short of the altar -- around a pillar, or at the lip
		// of the stairwell -- would otherwise leave it standing there lit forever.
		if (homingTicks % REPATH_EVERY == 1) navigation.moveTo(hx, hy, hz, RETURN_SPEED)

		// Last resort. A sentinel stranded somewhere with its eyes still burning
		// is worse than one that steps back onto its plinth unobserved.
		if (homingTicks > HOMING_PATIENCE) arrive(home, hx, hy, hz)
	}

	/**
	 * Back onto the plinth, facing what it was set to watch.
	 *
	 * Turns to stone immediately -- the rest pose *is* the statue pose, so there
	 * is nothing to animate. Only the light in its eyes takes time to go out.
	 */
	private fun arrive(home: BlockPos, hx: Double, hy: Double, hz: Double) {
		homingTicks = 0
		navigation.stop()
		target = null

		// Only step onto the plinth if there is room for it. Someone who walled
		// the altar in gets a sentinel that turns to stone where it stands,
		// rather than one embedded halfway through their blocks.
		if (fits(hx, hy, hz)) snapTo(hx, hy, hz, homeYaw(home), 0.0f)

		sleep()
		unanswered = 0
		settleTick = 1
	}

	/** Whether the sentinel's whole bulk clears the blocks at a position. */
	private fun fits(px: Double, py: Double, pz: Double): Boolean {
		val half = bbWidth / 2.0
		val box = AABB(px - half, py, pz - half, px + half, py + bbHeight, pz + half)
		return level().noCollision(this, box)
	}

	/** Faces whatever it was set to watch, so it ends up as it was found. */
	private fun homeYaw(home: BlockPos): Float {
		val ward = wardPos ?: return yRot
		val dx = (ward.x - home.x).toDouble()
		val dz = (ward.z - home.z).toDouble()
		if (dx == 0.0 && dz == 0.0) return yRot
		return (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG).toFloat() - 90.0f
	}

	private fun settle() {
		settleTick++
		if (settleTick >= SETTLE_TICKS) settleTick = 0
	}

	/**
	 * Eyes glow white only while it is up.
	 *
	 * Dormant they hold a dim amber ember rather than going out entirely -- the
	 * statue still has to read as a warning a player can walk past, which is the
	 * whole reason the eyes are lit in the first place.
	 */
	private fun updateGlow() {
		val next = when {
			settleTick > 0 ->
				Mth.lerp(settleTick.toFloat() / SETTLE_TICKS, GLOW_FULL.toFloat(), GLOW_EMBER.toFloat()).toInt()
			isDormant -> GLOW_EMBER
			isWaking ->
				Mth.lerp(wakeTick.toFloat() / WAKE_TICKS, GLOW_EMBER.toFloat(), GLOW_FULL.toFloat()).toInt()
			else -> GLOW_FULL
		}
		if (glow != next) glow = next
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
				hurl(level)
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

	/**
	 * The shout throws anyone standing too close off their feet.
	 *
	 * Deliberately no damage. The point is to break up whatever the player was
	 * doing at the stairwell and put distance between them, so the sentinel gets
	 * to finish standing up and they get to see what they woke.
	 */
	private fun hurl(level: ServerLevel) {
		for (player in level.players()) {
			if (player.isSpectator || player.isCreative) continue
			if (player.distanceToSqr(this) > ROAR_RANGE_SQ) continue
			throwBack(player, ROAR_PUSH, ROAR_LIFT)
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
		when (kind) {
			SLAM -> say(ModSounds.SENTINEL_STIR, 1.0f, 0.7f)
			SWEEP -> say(ModSounds.SENTINEL_SWEEP, 1.0f, 0.7f)
			else -> say(ModSounds.SENTINEL_REACH, 1.6f, 0.6f)
		}
	}

	private fun advanceAttack(level: ServerLevel) {
		val t = attackTick
		val kind = attackKind
		val impactAt = windupOf(kind)
		val endAt = impactAt + RECOVER

		// Rooted through the wind-up. This is what makes the attacks readable:
		// it stops, it raises, then it lands, and a player who is watching has
		// time to get out of the way.
		if (t < impactAt) navigation.stop()

		if (t == impactAt) {
			when (kind) {
				SLAM -> slam(level)
				SWEEP -> sweep(level)
				else -> reckon(level)
			}
		}

		attackTick = t + 1
		if (attackTick > endAt) {
			attackKind = NO_ATTACK
			attackTick = 0
			when (kind) {
				SLAM -> slamCooldown = SLAM_COOLDOWN
				SWEEP -> sweepCooldown = SWEEP_COOLDOWN
				else -> reckoningCooldown = RECKONING_COOLDOWN
			}
		}
	}

	/** Both fists into the floor: everything around it is thrown outward and up. */
	private fun slam(level: ServerLevel) {
		say(ModSounds.SENTINEL_SLAM, 1.6f, 0.7f)
		say(ModSounds.SENTINEL_RUMBLE, 1.4f, 0.5f)
		shake(level, SHAKE_MAX)
		ring(level, SLAM_RADIUS)

		for (victim in around(SLAM_RADIUS)) {
			breakGuard(victim, SLAM_GUARD_LOCK)
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

			breakGuard(victim, SWEEP_GUARD_LOCK)
			victim.hurtServer(level, damageSources().mobAttack(this), SWEEP_DAMAGE)
			throwBack(victim, SWEEP_PUSH, SWEEP_LIFT)
		}
	}

	/**
	 * The answer to being fought from a pillar.
	 *
	 * It does not try to reach up -- it reaches out and hauls the player down,
	 * which both lands the damage and destroys the position that made the fight
	 * free. Standing somewhere it cannot follow stops being a strategy, because
	 * height is exactly what this attack takes away.
	 */
	private fun reckon(level: ServerLevel) {
		say(ModSounds.SENTINEL_RECKONING, 2.0f, 0.7f)
		say(ModSounds.SENTINEL_RUMBLE, 1.4f, 0.45f)
		shake(level, SHAKE_MAX)
		burst(level)

		for (victim in level.players()) {
			if (victim.isSpectator || victim.isCreative) continue
			if (victim.distanceToSqr(this) > RECKONING_RANGE_SQ) continue

			// Order matters. Sneaking on the lip of a block survives any amount
			// of knockback, so the footing has to go before the pull -- otherwise
			// a player can simply hold shift and stay exactly where they are.
			shatterFooting(level, victim)
			breakGuard(victim, RECKONING_GUARD_LOCK)
			victim.hurtServer(level, damageSources().mobAttack(this), RECKONING_DAMAGE)
			haul(victim)
		}
	}

	/**
	 * Takes out the ground under a victim.
	 *
	 * Three by three and two deep, which is enough that stepping to the edge of
	 * a pillar does not save them. Gated on mobGriefing like every other mob
	 * that rearranges the world, and it refuses to chew on anything unbreakable.
	 */
	private fun shatterFooting(level: ServerLevel, victim: LivingEntity) {
		if (!level.gameRules.get(GameRules.MOB_GRIEFING)) return

		val feet = victim.blockPosition()

		// Above the sentinel, the floor under them goes. Below it, the ceiling
		// over them does -- otherwise the haul just presses them into the crypt
		// roof and the whole attack accomplishes nothing.
		val band = if (victim.y > y) -2..-1 else 2..3

		for (dy in band) {
			for (dx in -1..1) {
				for (dz in -1..1) {
					val at = feet.offset(dx, dy, dz)
					val state = level.getBlockState(at)
					if (state.isAir) continue
					if (state.getDestroySpeed(level, at) < 0.0f) continue
					level.destroyBlock(at, true, this, DESTROY_RECURSION)
				}
			}
		}
	}

	/**
	 * Puts a raised shield out of action.
	 *
	 * Blocking otherwise turns the whole fight into a stalemate: every attack is
	 * absorbed, the knockback never lands, and the player trades hits on their
	 * own schedule. A shield still works -- it just cannot work continuously.
	 */
	private fun breakGuard(victim: LivingEntity, ticks: Int) {
		if (victim !is Player || !victim.isBlocking) return

		val guard = victim.useItem
		victim.stopUsingItem()
		victim.cooldowns.addCooldown(guard, ticks)
		victim.level().playSound(
			null,
			victim.x, victim.y, victim.z,
			SoundEvents.SHIELD_BREAK,
			SoundSource.PLAYERS,
			0.9f,
			0.55f,
		)
	}

	/** Yanks a victim off whatever it is standing on and down to the sentinel. */
	private fun haul(victim: LivingEntity) {
		val dx = x - victim.x
		val dy = (y + 0.5) - victim.y
		val dz = z - victim.z
		val len = Mth.sqrt((dx * dx + dy * dy + dz * dz).toFloat()).toDouble()
		if (len < 0.001) return

		victim.setDeltaMovement(
			dx / len * RECKONING_PULL,
			dy / len * RECKONING_PULL - RECKONING_DROP,
			dz / len * RECKONING_PULL,
		)
		victim.hurtMarked = true
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
		if (victim is LivingEntity) breakGuard(victim, MELEE_GUARD_LOCK)

		val landed = super.doHurtTarget(level, victim)
		if (landed) lastLanded = tickCount
		return landed
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

		val hurt = super.hurtServer(level, source, amount)
		if (hurt) (source.entity as? Player)?.let { resent(it) }
		return hurt
	}

	/**
	 * Counts hits landed from out of reach, and eventually answers them.
	 *
	 * "Out of reach" is a height gap, not a distance: the sentinel stands 2.6
	 * blocks and swings level, so anyone whose feet clear [OUT_OF_REACH] above
	 * its own can hit it freely from a pillar it has no way to climb. Two such
	 * hits is enough to establish that it is being done on purpose rather than
	 * that someone happens to be on a slope.
	 */
	private fun resent(attacker: Player) {
		// Either direction. Being too tall to follow a player down the crypt
		// stair is the same problem as being too short to climb their pillar --
		// what matters is the gap, not which way it runs.
		val gap = abs(attacker.y - y)

		// And it has to genuinely be stuck. A height difference alone is not
		// enough: the sanctum has steps, and a sentinel that can still land its
		// own hits is not being cheesed.
		val stranded = gap >= OUT_OF_REACH && tickCount - lastLanded > RETALIATION_WINDOW
		if (!stranded) {
			unanswered = 0
			return
		}

		target = attacker
		unanswered++
		if (unanswered < FREE_HITS_ALLOWED) return
		if (attackKind != NO_ATTACK || reckoningCooldown > 0) return
		if (attacker.distanceToSqr(this) > RECKONING_RANGE_SQ) return

		unanswered = 0
		begin(RECKONING)
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
		// Saved for the same reason the ward is: chunks unload constantly, and a
		// sentinel that forgets where it stood has nowhere to go back to.
		homePos?.let {
			output.putInt(TAG_HOME_X, it.x)
			output.putInt(TAG_HOME_Y, it.y)
			output.putInt(TAG_HOME_Z, it.z)
		}
	}

	override fun readAdditionalSaveData(input: ValueInput) {
		super.readAdditionalSaveData(input)
		isDormant = input.getBooleanOr(TAG_DORMANT, false)
		isNoAi = isDormant

		// A sentinel saved mid-sequence comes back fully awake rather than
		// resuming: the cues would be out of step with the animation anyway.
		wakeTick = 0

		wardPos = readPos(input, TAG_WARD_X, TAG_WARD_Y, TAG_WARD_Z)
		homePos = readPos(input, TAG_HOME_X, TAG_HOME_Y, TAG_HOME_Z)
	}

	private fun readPos(input: ValueInput, xKey: String, yKey: String, zKey: String): BlockPos? {
		val x = input.getInt(xKey)
		val y = input.getInt(yKey)
		val z = input.getInt(zKey)
		return if (x.isPresent && y.isPresent && z.isPresent) {
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
		private val DATA_GLOW: EntityDataAccessor<Int> =
			SynchedEntityData.defineId(StoneSentinel::class.java, EntityDataSerializers.INT)

		private const val TAG_DORMANT = "Dormant"
		private const val TAG_WARD_X = "WardX"
		private const val TAG_WARD_Y = "WardY"
		private const val TAG_WARD_Z = "WardZ"
		private const val TAG_HOME_X = "HomeX"
		private const val TAG_HOME_Y = "HomeY"
		private const val TAG_HOME_Z = "HomeZ"

		const val NO_ATTACK: Byte = 0
		const val SLAM: Byte = 1
		const val SWEEP: Byte = 2
		const val RECKONING: Byte = 3

		/** Ticks each attack spends winding up before it lands. */
		fun windupOf(kind: Byte): Int = when (kind) {
			SLAM -> SLAM_WINDUP
			SWEEP -> SWEEP_WINDUP
			else -> RECKONING_WINDUP
		}

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

		/** The shout's reach and shove. Far enough to catch someone at the stairs. */
		private const val ROAR_RANGE_SQ = 12.0 * 12.0
		private const val ROAR_PUSH = 1.35
		private const val ROAR_LIFT = 0.55

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

		// ---- reckoning: the anti-pillar answer ----

		/**
		 * Longer than the slam. It has to be, or being dragged off a wall would
		 * feel like something that simply happened rather than something the
		 * player watched coming and failed to escape.
		 */
		const val RECKONING_WINDUP: Int = 28

		private const val RECKONING_COOLDOWN = 90

		/** Height gap, either way, that puts a player past anything it can swing at. */
		private const val OUT_OF_REACH = 2.0

		/** How long without landing a blow before it counts itself stuck. */
		private const val RETALIATION_WINDOW = 60

		/** Free hits from up there before it stops tolerating the arrangement. */
		private const val FREE_HITS_ALLOWED = 2

		private const val RECKONING_RANGE_SQ = 16.0 * 16.0
		private const val RECKONING_DAMAGE = 20.0f

		/** Speed the victim is dragged in at, plus a little extra straight down. */
		private const val RECKONING_PULL = 1.6
		private const val RECKONING_DROP = 0.35

		private const val DESTROY_RECURSION = 512

		// ---- shields ----

		/**
		 * Ticks a raised shield is put out of action for.
		 *
		 * Scaled to how hard the blow was. Even the ordinary swing costs two
		 * seconds of guard, which is what stops block-hit-block being a rhythm.
		 */
		private const val MELEE_GUARD_LOCK = 40
		private const val SWEEP_GUARD_LOCK = 60
		private const val SLAM_GUARD_LOCK = 100
		private const val RECKONING_GUARD_LOCK = 140

		// ---- going back to sleep ----

		/** Close enough to the altar to call it home. */
		private const val ARRIVED_SQ = 2.25

		private const val RETURN_SPEED = 0.8

		/** Two seconds for the light to leave its eyes. */
		private const val SETTLE_TICKS = 40

		/**
		 * Eye brightness, 0..100.
		 *
		 * Dark at rest, so a statue is genuinely just a statue and the light is
		 * something the player only ever sees on a sentinel that is up. The eyes
		 * come on through the waking sequence and drain out again as it settles.
		 */
		const val GLOW_EMBER: Int = 0
		const val GLOW_FULL: Int = 100

		/** Ticks between re-issuing the path home. */
		private const val REPATH_EVERY = 20

		/** After this long failing to walk home, it simply steps back onto the plinth. */
		private const val HOMING_PATIENCE = 200

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
