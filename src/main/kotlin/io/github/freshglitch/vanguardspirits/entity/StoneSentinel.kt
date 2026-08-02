package io.github.freshglitch.vanguardspirits.entity

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.registry.ModParticles
import io.github.freshglitch.vanguardspirits.registry.ModSounds
import io.github.freshglitch.vanguardspirits.worldgen.RuinSeal
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.BlockParticleOption
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.Identifier
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
import net.minecraft.world.entity.ai.attributes.AttributeModifier
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
import net.minecraft.world.entity.projectile.Projectile
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
class StoneSentinel(type: EntityType<out StoneSentinel>, level: Level) : Monster(type, level), RuinDefender {

	/**
	 * Never turns on the other things guarding this place.
	 *
	 * Its heavy attacks are area effects, so without this a single slam would
	 * set every Remnant in the room against it and the ruin would clear itself
	 * out while the player watched from the doorway.
	 */
	override fun canAttack(victim: LivingEntity): Boolean =
		victim !is RuinDefender && super.canAttack(victim)

	override fun setTarget(victim: LivingEntity?) {
		if (victim is RuinDefender) return
		super.setTarget(victim)
	}

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
	private var sunderCooldown = 0

	/**
	 * Hits taken from somewhere the sentinel cannot answer.
	 *
	 * Two blocks of pillar puts a player above everything it can swing at, and
	 * from up there the fight becomes free. This counts those hits; [RECKONING]
	 * and [SUNDER] are what it does about them.
	 */
	private var unanswered = 0

	/** Where it stood at the last pin sample, and how long it has gone nowhere. */
	private var pinX = 0.0
	private var pinY = 0.0
	private var pinZ = 0.0
	private var pinnedTicks = 0

	/**
	 * How long it has failed to get any closer to what it is chasing.
	 *
	 * A separate question from [pinnedTicks], and the one the shell cares about.
	 * Standing still means walled in; failing to close means the player is
	 * somewhere walking cannot help with. A sentinel milling about at the foot of
	 * a tower is not still -- it shuffles, and the position counter kept resetting
	 * on it -- but it never gets nearer, which is the fact that matters.
	 */
	private var lastGap = 0.0f
	private var stalledTicks = 0

	/**
	 * How far around the sentinel its attacker has travelled while hitting it.
	 *
	 * Kiting is a shape, not a position: the player is never anywhere the
	 * sentinel cannot reach, they are simply never there when it arrives. What
	 * gives it away is the bearing sweeping round, so that is what is measured.
	 */
	private var lastBearing = Float.NaN
	private var swept = 0.0f
	private var lastCircled = 0

	private var gyreCooldown = 0

	/**
	 * Projectiles caught this tick, to be thrown back on the next one.
	 *
	 * Not reflected where they are caught. A projectile that fails to damage its
	 * target is bounced by vanilla immediately afterwards, which would undo any
	 * velocity set from inside the damage hook -- so the throw waits a tick and
	 * has the last word.
	 */
	private val caught = mutableListOf<Projectile>()

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
		builder.define(DATA_GYRE, 0)
		builder.define(DATA_BULWARK, 0)
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

	/** 0 when it is not spinning, otherwise 1..[GYRE_TICKS]. */
	var gyreTick: Int
		get() = entityData.get(DATA_GYRE)
		private set(value) = entityData.set(DATA_GYRE, value)

	/**
	 * 0 when it is standing, otherwise how long it has been curled up.
	 *
	 * Unbounded, unlike the gyre. The shell lasts as long as the threat does.
	 */
	var bulwarkTick: Int
		get() = entityData.get(DATA_BULWARK)
		private set(value) = entityData.set(DATA_BULWARK, value)

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
		if (level !is ServerLevel) {
			// Only the client knows how far round the spin has come, so only the
			// client can put the wake where the fists actually are.
			if (gyreTick > GYRE_FLARE) shedWake(level)
			return
		}

		when {
			settleTick > 0 -> settle()
			isDormant -> if (tickCount % WATCH_INTERVAL == 0) watch(level)
			isWaking -> advanceWake(level)
			else -> {
				if (slamCooldown > 0) slamCooldown--
				if (sweepCooldown > 0) sweepCooldown--
				if (reckoningCooldown > 0) reckoningCooldown--
				if (sunderCooldown > 0) sunderCooldown--
				if (gyreCooldown > 0) gyreCooldown--

				checkPinned()

				// The shell takes the whole tick. Nothing else runs while it is up,
				// which is what stops a curled sentinel trying to walk home
				// mid-siege -- and once it opens, the ordinary order below picks
				// straight back up with no special case.
				if (bulwarkTick > 0) {
					advanceBulwark(level)
				} else {
					if (gyreTick > 0) advanceGyre(level)

					when {
						attackKind != NO_ATTACK -> advanceAttack(level)
						hasQuarry() -> homingTicks = 0
						else -> goHome()
					}
				}
			}
		}

		updateGlow()
	}

	private fun hasQuarry(): Boolean = target?.isAlive == true

	/**
	 * Notices when it has stopped going anywhere.
	 *
	 * Walling a sentinel in works on exactly the same principle as standing on a
	 * pillar: it cannot reach, so the fight costs nothing. Height is not the tell
	 * in that case though -- the player can be stood level with it, one block of
	 * cobble away -- so what gets measured is displacement instead.
	 *
	 * Sampled over half a second rather than per tick, because a sentinel pressed
	 * against a wall still jitters and would otherwise never look still.
	 */
	private fun checkPinned() {
		if (tickCount % PIN_SAMPLE != 0) return

		val drift = distanceToSqr(pinX, pinY, pinZ)
		pinX = x
		pinY = y
		pinZ = z

		// Rooted on purpose through a wind-up, which is not the same as being held.
		pinnedTicks = if (attackKind == NO_ATTACK && drift < PIN_DRIFT_SQ) pinnedTicks + PIN_SAMPLE else 0

		checkStalled()
	}

	/**
	 * Notices when walking has stopped helping.
	 *
	 * Measured as distance to the quarry rather than distance travelled, because
	 * those come apart in exactly the case that matters. Under a tower the
	 * sentinel shuffles about after a player who keeps moving, so it is never
	 * still -- but it never gets any nearer either, and a counter that resets on
	 * every shuffle misses the siege entirely.
	 *
	 * Losing ground counts the same as holding station: a player building higher
	 * is not a reason to keep hoping.
	 */
	private fun checkStalled() {
		val quarry = target
		if (quarry == null || !quarry.isAlive) {
			stalledTicks = 0
			lastGap = 0.0f
			return
		}

		val now = distanceTo(quarry)
		if (lastGap <= 0.0f) {
			lastGap = now
			return
		}

		stalledTicks = if (now < lastGap - CLOSING) 0 else stalledTicks + PIN_SAMPLE
		lastGap = now
	}

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

		endGyre()
		endBulwark(release = true)
		sleep()
		unanswered = 0
		pinnedTicks = 0
		swept = 0.0f
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

		// Nothing special while it is spinning. Catching up to the player is the
		// whole point of the gyre, so what it does on arrival is an ordinary
		// swing -- and the spin stays unbroken, which is what sells it.
		if (gyreTick > 0) return false

		// Curled up it does not attack at all. Reaching it is what opens it, and
		// that happens in hurtServer.
		if (bulwarkTick > 0) return true

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
			SUNDER -> {
				// Square up first. The punch goes wherever the model is pointing,
				// so the turn has to happen before the arm starts coming back or
				// the wall it breaks will not be the one in the way.
				target?.let(::squareUp)
				say(ModSounds.SENTINEL_BRACE, 1.5f, 0.6f)
			}
			else -> say(ModSounds.SENTINEL_REACH, 1.6f, 0.6f)
		}
	}

	/** Turns the whole body, not just the head, to face a victim. */
	private fun squareUp(victim: LivingEntity) {
		val dx = victim.x - x
		val dz = victim.z - z
		if (dx * dx + dz * dz < 1.0E-4) return

		val yaw = (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG).toFloat() - 90.0f
		yRot = yaw
		yBodyRot = yaw
		yHeadRot = yaw
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
				SUNDER -> sunder(level)
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
				SUNDER -> sunderCooldown = SUNDER_COOLDOWN
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
			lastLanded = tickCount
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
			lastLanded = tickCount
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

			// From below, taking the ceiling is not enough on its own: the crypt
			// stair is two blocks high and the sentinel needs three, so even
			// after the haul it has no route down if the player gets back there.
			// Smashing a corridor through gives it one.
			if (victim.y < y) carveApproach(level, victim)
			breakGuard(victim, RECKONING_GUARD_LOCK)
			victim.hurtServer(level, damageSources().mobAttack(this), RECKONING_DAMAGE)
			haul(victim)
			lastLanded = tickCount
		}
	}

	// ---------------------------------------------------------------- bulwark

	/**
	 * The answer to being shot at from somewhere it cannot even reply to.
	 *
	 * Everything else the sentinel does assumes it can eventually get its hands
	 * on the player. From forty blocks up a tower that is simply untrue: the
	 * Reckoning cannot span it, the Sunder has nothing to break, and the Gyre
	 * would only carry it to the foot of a wall. So it stops pretending and
	 * shuts itself away instead -- arrows come off the shell, and the stone
	 * knits back together faster than a bow can open it.
	 *
	 * Which makes it a refusal rather than an attack. The player is not punished
	 * for shooting from up there; they are told that it will not work, and left
	 * to either come down or leave.
	 */
	private fun beginBulwark() {
		bulwarkTick = 1
		navigation.stop()
		say(ModSounds.SENTINEL_SHELL, 1.6f, 0.4f)
		say(ModSounds.SENTINEL_RUMBLE, 1.2f, 0.4f)
	}

	private fun advanceBulwark(level: ServerLevel) {
		// Held down every tick. The melee goal is still running underneath and
		// would otherwise walk the shell across the room.
		navigation.stop()
		bulwarkTick++

		if (bulwarkTick % MEND_EVERY == 0 && health < maxHealth) {
			heal(MEND_AMOUNT)
			say(ModSounds.SENTINEL_MEND, 0.9f, 0.65f)
			dust(level, y + bbHeight * 0.35, 8, 0.5)
		}

		if (threatGone(level)) endBulwark(release = true)
	}

	/**
	 * Opens back up.
	 *
	 * [release] is the difference between the two ways out. A siege that ended
	 * because the player left has nothing to fight, so the quarry is dropped and
	 * the walk home starts on the next tick. One that ended because somebody
	 * finally came within arm's reach keeps its target and goes back to work.
	 */
	private fun endBulwark(release: Boolean) {
		if (bulwarkTick == 0) return
		bulwarkTick = 0
		unanswered = 0
		swept = 0.0f
		stalledTicks = 0
		lastGap = 0.0f

		// A fresh grace period, or the very next arrow would find the retaliation
		// window already expired and slam the shell straight back down.
		lastLanded = tickCount

		if (release) target = null
		say(ModSounds.SENTINEL_STIR, 1.1f, 0.55f)
	}

	/**
	 * Whether there is still anyone worth staying shut for.
	 *
	 * Measured at the same range the target selector uses, so the moment this
	 * says yes there is also nothing left to re-acquire -- the sentinel opens,
	 * finds no quarry, and walks back to the altar under the ordinary rules.
	 */
	private fun threatGone(level: ServerLevel): Boolean {
		for (player in level.players()) {
			if (player.isSpectator || player.isCreative) continue
			if (player.distanceToSqr(this) <= BULWARK_WATCH_SQ) return false
		}
		return true
	}

	// ------------------------------------------------------------------- gyre

	/**
	 * The answer to being run rings around.
	 *
	 * Not a strike, so it is not an [attackKind] -- it is a state the sentinel
	 * spends several seconds in, and everything else it can do carries on
	 * underneath. Both arms go out level and it turns on the spot, faster and
	 * faster, closing at nearly twice its usual pace with a face of moving stone
	 * that arrows come off.
	 *
	 * Kiting works because the sentinel is slow and answers only what it can
	 * touch. This takes away both halves of that at once.
	 */
	private fun beginGyre() {
		gyreTick = 1
		swept = 0.0f
		lastBearing = Float.NaN

		say(ModSounds.SENTINEL_WIND, 1.8f, 0.6f)
		say(ModSounds.SENTINEL_STIR, 1.2f, 0.8f)

		getAttribute(Attributes.MOVEMENT_SPEED)?.addTransientModifier(
			AttributeModifier(GYRE_HASTE, GYRE_SPEED_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
		)
	}

	private fun advanceGyre(level: ServerLevel) {
		throwBackCaught(level)

		// Nothing left to chase. Spinning on the way back to the altar would look
		// like a sentinel that had forgotten why it started.
		if (!hasQuarry()) {
			endGyre()
			return
		}

		val t = gyreTick

		// The whirl is re-announced rather than looped, because a looping sound
		// would keep playing for a second after the spin stops.
		if (t >= GYRE_FLARE && (t - GYRE_FLARE) % WHIRL_EVERY == 0) {
			say(ModSounds.SENTINEL_WIND, 1.4f, 0.75f + random.nextFloat() * 0.1f)
			level.sendParticles(
				BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_TILES.defaultBlockState()),
				x, y + bbHeight * 0.5, z,
				14,
				bbWidth.toDouble(), bbHeight * 0.35, bbWidth.toDouble(),
				0.05,
			)
		}

		gyreTick = t + 1
		if (gyreTick > GYRE_TICKS) endGyre()
	}

	/**
	 * Trails torn air off the fists and the greaves.
	 *
	 * The four points ride the same circle the model turns the bones around, so
	 * the wake sits on the limbs rather than near them. Both fists are one radius
	 * apart on opposite sides, and the greaves the same at ankle height, which is
	 * why one signed offset covers all four.
	 */
	private fun shedWake(level: Level) {
		val psi = (spinAngle(gyreTick, tickCount.toFloat()) + yBodyRot * Mth.DEG_TO_RAD).toDouble()
		val cos = Mth.cos(psi).toDouble()
		val sin = Mth.sin(psi).toDouble()

		for (side in SIDES) {
			fling(level, side, cos, sin, FIST_REACH, FIST_HEIGHT, FIST_WHIP, WAKE_PER_FIST)
			fling(level, side, cos, sin, GREAVE_REACH, GREAVE_HEIGHT, GREAVE_WHIP, WAKE_PER_GREAVE)
		}
	}

	/** One limb's worth: a few crescents thrown off along the way it is going. */
	private fun fling(
		level: Level,
		side: Double,
		cos: Double,
		sin: Double,
		reach: Double,
		height: Double,
		whip: Double,
		count: Int,
	) {
		val px = x + side * reach * cos
		val py = y + height
		val pz = z + side * reach * sin

		// Tangent to the circle at that point, which is the direction the limb is
		// travelling and so the direction the air comes off it.
		val tx = side * -sin * whip
		val tz = side * cos * whip

		repeat(count) {
			level.addParticle(
				ModParticles.STONE_WAKE,
				px + (random.nextDouble() - 0.5) * WAKE_SCATTER,
				py + (random.nextDouble() - 0.5) * WAKE_SCATTER,
				pz + (random.nextDouble() - 0.5) * WAKE_SCATTER,
				tx + (random.nextDouble() - 0.5) * WAKE_DRIFT,
				(random.nextDouble() - 0.3) * WAKE_DRIFT,
				tz + (random.nextDouble() - 0.5) * WAKE_DRIFT,
			)
		}
	}

	/** Stops the spin and puts it back to its usual pace. */
	private fun endGyre() {
		if (gyreTick == 0) return
		gyreTick = 0
		gyreCooldown = GYRE_COOLDOWN
		caught.clear()
		getAttribute(Attributes.MOVEMENT_SPEED)?.removeModifier(GYRE_HASTE)
	}

	/**
	 * Sends last tick's caught projectiles back where they came from.
	 *
	 * Aimed at whoever loosed it rather than simply reversed, so a shot that
	 * would have missed on the way back still finds them -- the point of the
	 * spin is that shooting it is worse than not shooting it.
	 */
	private fun throwBackCaught(level: ServerLevel) {
		if (caught.isEmpty()) return

		for (bolt in caught) {
			if (!bolt.isAlive) continue

			val shooter = bolt.owner
			val speed = bolt.deltaMovement.length().coerceAtLeast(REFLECT_MIN_SPEED)

			val aim = if (shooter != null) {
				shooter.position().add(0.0, shooter.bbHeight * 0.5, 0.0).subtract(bolt.position()).normalize()
			} else {
				bolt.deltaMovement.normalize().scale(-1.0)
			}

			// Out of its own bulk first. The projectile is sitting inside the
			// sentinel at this point and would otherwise strike it on the way out,
			// now that the sentinel is no longer its owner.
			bolt.snapTo(
				bolt.x + aim.x * REFLECT_CLEARANCE,
				bolt.y + aim.y * REFLECT_CLEARANCE,
				bolt.z + aim.z * REFLECT_CLEARANCE,
				bolt.yRot,
				bolt.xRot,
			)
			bolt.deltaMovement = aim.scale(speed * REFLECT_GAIN)
			// Forces the new velocity out to clients this tick, so the arrow is
			// seen turning round rather than teleporting a moment later.
			bolt.hurtMarked = true
			bolt.setOwner(this)

			level.playSound(null, bolt.x, bolt.y, bolt.z, SoundEvents.BREEZE_DEFLECT, SoundSource.HOSTILE, 1.2f, 0.7f)
		}

		caught.clear()
	}

	/**
	 * The answer to being walled in.
	 *
	 * Where the reckoning drags a distant player down, this one goes through
	 * whatever is between them. The arm comes back behind the shoulder, then
	 * straightens: a three by three breach punched out of the wall in front, and
	 * anything caught in the swing takes the worst single blow it has.
	 *
	 * Which makes it a way out as much as an attack. A sentinel that cannot walk
	 * is not a sentinel any more, so the point of the strike is that the hole is
	 * still there afterwards.
	 */
	private fun sunder(level: ServerLevel) {
		say(ModSounds.SENTINEL_SUNDER, 2.0f, 0.6f)
		say(ModSounds.SENTINEL_SLAM, 1.4f, 0.5f)
		shake(level, SHAKE_MAX)

		val facing = yRot * Mth.DEG_TO_RAD
		val fx = -Mth.sin(facing.toDouble()).toDouble()
		val fz = Mth.cos(facing.toDouble()).toDouble()

		breach(level, fx, fz)

		level.sendParticles(
			BlockParticleOption(ParticleTypes.BLOCK, Blocks.DEEPSLATE_BRICKS.defaultBlockState()),
			x + fx * 2.0, y + bbHeight * 0.55, z + fz * 2.0,
			70,
			0.9, 0.9, 0.9,
			0.35,
		)

		for (victim in around(SUNDER_RADIUS)) {
			val dx = victim.x - x
			val dz = victim.z - z
			val len = Mth.sqrt((dx * dx + dz * dz).toFloat()).toDouble()
			if (len > 0.001 && (dx / len * fx + dz / len * fz) < SUNDER_ARC) continue

			breakGuard(victim, SUNDER_GUARD_LOCK)
			victim.hurtServer(level, damageSources().mobAttack(this), SUNDER_DAMAGE)
			throwBack(victim, SUNDER_PUSH, SUNDER_LIFT)
			lastLanded = tickCount
		}

		// Whatever was holding it is gone, so stop counting it as held and let it
		// try to walk again on the next tick.
		pinnedTicks = 0
	}

	/**
	 * Punches a doorway out of the wall it is facing.
	 *
	 * Three wide and three tall, starting at the sentinel's own feet, which is
	 * exactly the opening it needs to fit through. The course of blocks it is
	 * standing on is never touched -- taking that would drop it into a pit and
	 * leave it just as stuck as before.
	 */
	private fun breach(level: ServerLevel, fx: Double, fz: Double) {
		if (!level.gameRules.get(GameRules.MOB_GRIEFING)) return

		// Perpendicular to the facing, so the three-wide span runs across the wall
		// rather than into it.
		val sx = -fz
		val sz = fx

		val floor = blockPosition().y
		val cursor = BlockPos.MutableBlockPos()

		for (depth in 1..SUNDER_DEPTH) {
			for (side in -1..1) {
				val bx = x + fx * depth + sx * side
				val bz = z + fz * depth + sz * side
				for (dy in 0 until SUNDER_HEIGHT) {
					cursor.set(Mth.floor(bx), floor + dy, Mth.floor(bz))
					val state = level.getBlockState(cursor)
					if (state.isAir) continue
					if (state.getDestroySpeed(level, cursor) < 0.0f) continue
					level.destroyBlock(cursor, true, this, DESTROY_RECURSION)
				}
			}
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
	 * Smashes a corridor from the sentinel to its victim.
	 *
	 * Straight line, sampled block by block, cleared to the sentinel's full
	 * height so the result is something it can actually walk down -- the floor
	 * of the corridor is left intact and only the headroom above it is taken,
	 * which turns solid rock into a ramp rather than a pit.
	 *
	 * Deliberately destructive. It will take a bite out of the sanctum floor on
	 * the way down to the crypt, which is the point: the ruin is what the player
	 * chose to fight inside.
	 */
	private fun carveApproach(level: ServerLevel, victim: LivingEntity) {
		if (!level.gameRules.get(GameRules.MOB_GRIEFING)) return

		val from = position()
		val to = victim.position()
		val span = from.distanceTo(to)
		if (span < 1.0 || span > CARVE_REACH) return

		val steps = Mth.ceil(span)
		val tall = Mth.ceil(bbHeight)
		val cursor = BlockPos.MutableBlockPos()

		for (i in 0..steps) {
			val t = i.toDouble() / steps
			val px = Mth.floor(Mth.lerp(t, from.x, to.x))
			val py = Mth.floor(Mth.lerp(t, from.y, to.y))
			val pz = Mth.floor(Mth.lerp(t, from.z, to.z))

			for (dy in 0 until tall) {
				for (dx in -1..1) {
					for (dz in -1..1) {
						cursor.set(px + dx, py + dy, pz + dz)
						val state = level.getBlockState(cursor)
						if (state.isAir) continue
						if (state.getDestroySpeed(level, cursor) < 0.0f) continue
						level.destroyBlock(cursor, true, this, DESTROY_RECURSION)
					}
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

	/** Everything in range that is not on the ruin's side. */
	private fun around(radius: Double): List<LivingEntity> =
		level().getEntitiesOfClass(LivingEntity::class.java, boundingBox.inflate(radius)) {
			it !== this && it.isAlive && it !is RuinDefender
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

		val bolt = source.directEntity as? Projectile

		if (bulwarkTick > 0) {
			// Nothing thrown gets through the shell. Refusing the damage is the
			// whole of it: vanilla bounces a projectile that fails to hurt what
			// it struck, so the arrow is seen to come off on its own.
			if (bolt != null) return false

			// Something got close enough to touch it. That is the one thing the
			// shell was never proof against, so it opens -- and takes the hit.
			endBulwark(release = false)
		}

		if (gyreTick > 0 && bolt != null) {
			// Anything thrown at a spinning sentinel is caught rather than
			// absorbed, and comes back on the next tick. Queued instead of
			// answered here because vanilla bounces a projectile that fails to
			// damage its target, and that bounce happens after this returns.
			if (bolt !in caught) caught.add(bolt)
			return false
		}

		// Half of everything else while it is spinning. A face of moving stone is
		// a worse thing to swing at than a standing one.
		val taken = if (gyreTick > 0) amount * GYRE_ABSORB else amount

		val hurt = super.hurtServer(level, source, taken)
		if (hurt) (source.entity as? Player)?.let { resent(it, ranged = bolt != null) }
		return hurt
	}

	/**
	 * Counts hits it had no way to answer, and eventually answers them anyway.
	 *
	 * Two arrangements make a fight free, and they need different replies.
	 * Height is one: the sentinel stands 2.6 blocks and swings level, so anyone
	 * whose feet clear [OUT_OF_REACH] above its own can hit it from a pillar it
	 * cannot climb, and the [RECKONING] pulls them off it. Being held in place is
	 * the other -- walled in, or wedged -- and there the player may be standing
	 * level and close, so what the sentinel needs is not a pull but a way out.
	 * That is the [SUNDER].
	 *
	 * Kiting is the third: the player is in reach and it is not stuck, they are
	 * simply never standing still long enough to be hit. The [GYRE] is the reply.
	 *
	 * The fourth is not unfairness so much as futility -- shot at from so far
	 * above or below that none of the other three could span it. There the
	 * sentinel has no move, and [beginBulwark] is it declining to invent one.
	 *
	 * All three require that it has genuinely had no chance: a height difference
	 * on its own means nothing, the sanctum has steps, and a sentinel still
	 * landing its own blows is not being cheesed by anybody.
	 */
	private fun resent(attacker: Player, ranged: Boolean) {
		if (tickCount - lastLanded <= RETALIATION_WINDOW) {
			unanswered = 0
			swept = 0.0f
			return
		}

		traceBearing(attacker)

		// Either direction on the gap. Being too tall to follow a player down the
		// crypt stair is the same problem as being too short to climb their
		// pillar -- what matters is the gap, not which way it runs.
		val gap = abs(attacker.y - y)
		val reach = attacker.distanceToSqr(this)

		val answer = when {
			// Out of reach *and* past anything it can answer with. The Reckoning
			// spans sixteen blocks; beyond that a bow from a tower is a fight the
			// sentinel has no move in at all, so it stops trying to have one.
			gap >= OUT_OF_REACH && ranged && reach > RECKONING_RANGE_SQ -> Answer.BULWARK
			gap >= OUT_OF_REACH -> Answer.RECKONING
			pinnedTicks >= PINNED_TICKS -> Answer.SUNDER
			swept >= CIRCLE_SWEEP -> Answer.GYRE
			else -> {
				unanswered = 0
				return
			}
		}

		target = attacker
		unanswered++
		if (unanswered < FREE_HITS_ALLOWED) return
		if (attackKind != NO_ATTACK) return

		when (answer) {
			Answer.RECKONING -> if (reckoningCooldown > 0 || reach > RECKONING_RANGE_SQ) return
			Answer.SUNDER -> if (sunderCooldown > 0 || reach > SUNDER_TRIGGER_SQ) return
			Answer.GYRE -> if (gyreCooldown > 0 || gyreTick > 0) return
			// A player it can still walk towards is a long climb, not a siege, and
			// it should make the climb. Going nowhere is what separates the two,
			// and that is already measured -- asking the navigator instead looks
			// more direct but is not: createPath hands back whatever path the
			// melee goal already has cached, so the answer describes that goal's
			// state rather than the question being asked.
			Answer.BULWARK -> if (gyreTick > 0 || stalledTicks < STALLED_TICKS) return
		}

		unanswered = 0
		when (answer) {
			Answer.RECKONING -> begin(RECKONING)
			Answer.SUNDER -> begin(SUNDER)
			Answer.GYRE -> beginGyre()
			Answer.BULWARK -> beginBulwark()
		}
	}

	/**
	 * Adds up how far round the attacker has moved between the hits it lands.
	 *
	 * Accumulated across hits rather than measured between two of them, because
	 * one step sideways is not kiting -- what the sentinel is looking for is a
	 * player who has been all the way round it without ever being caught. The
	 * total lapses if the hits stop coming, so a long fight in one spot never
	 * adds up to a circle.
	 */
	private fun traceBearing(attacker: Player) {
		val bearing = (Mth.atan2(attacker.z - z, attacker.x - x) * Mth.RAD_TO_DEG).toFloat()

		if (lastBearing.isNaN() || tickCount - lastCircled > CIRCLE_WINDOW) {
			swept = 0.0f
		} else {
			swept += abs(Mth.wrapDegrees(bearing - lastBearing))
		}

		lastBearing = bearing
		lastCircled = tickCount
	}

	/** Which kind of unfairness the sentinel decided it was looking at. */
	private enum class Answer { RECKONING, SUNDER, GYRE, BULWARK }

	/**
	 * Its death is what unseals the Reliquary in the vault below.
	 *
	 * Recorded against the altar rather than wherever it happened to fall, so a
	 * sentinel chased out of its own ruin still lifts the right seal -- the
	 * lookup wants a point inside the structure, and the altar always is one.
	 */
	override fun die(source: DamageSource) {
		val level = level()
		val altar = homePos
		if (level is ServerLevel && altar != null) RuinSeal.markFelled(level, altar)
		super.die(source)
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
		private val DATA_GYRE: EntityDataAccessor<Int> =
			SynchedEntityData.defineId(StoneSentinel::class.java, EntityDataSerializers.INT)
		private val DATA_BULWARK: EntityDataAccessor<Int> =
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
		const val SUNDER: Byte = 4

		/** Ticks each attack spends winding up before it lands. */
		fun windupOf(kind: Byte): Int = when (kind) {
			SLAM -> SLAM_WINDUP
			SWEEP -> SWEEP_WINDUP
			SUNDER -> SUNDER_WINDUP
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

		/** Longest corridor it will smash to reach someone underneath it. */
		private const val CARVE_REACH = 16.0

		// ---- sunder: the answer to being held in place ----

		/**
		 * Long. The whole read is the arm travelling backwards and hanging there,
		 * so a player watching through the hole they left themselves has a full
		 * second to understand what is about to come through it.
		 */
		const val SUNDER_WINDUP: Int = 24

		private const val SUNDER_COOLDOWN = 100

		/** Half a second of sampled drift under this counts as going nowhere. */
		private const val PIN_SAMPLE = 10
		private const val PIN_DRIFT_SQ = 1.0

		/** Two seconds pinned before it stops treating the wall as scenery. */
		private const val PINNED_TICKS = 40

		/**
		 * Blocks it has to close in half a second for the chase to count as going
		 * somewhere. Walking covers several, so anything short of this is a stall.
		 */
		private const val CLOSING = 0.5f

		/** Two seconds of getting no nearer before it stops trying to. */
		private const val STALLED_TICKS = 40

		/** Close enough that a punch through the wall is a plausible answer. */
		private const val SUNDER_TRIGGER_SQ = 7.0 * 7.0

		/** Its worst single blow. Nothing else it does hits one target this hard. */
		private const val SUNDER_DAMAGE = 18.0f

		private const val SUNDER_RADIUS = 4.5

		/** Dot-product threshold for the punch's cone -- a narrow 70 degrees. */
		private const val SUNDER_ARC = 0.35

		private const val SUNDER_PUSH = 2.1
		private const val SUNDER_LIFT = 0.5

		/** Blocks of wall it goes through, and how tall the hole is left. */
		private const val SUNDER_DEPTH = 2
		private const val SUNDER_HEIGHT = 3

		// ---- gyre: the answer to being kited ----

		/** Degrees the attacker has to travel around it before it stops playing along. */
		private const val CIRCLE_SWEEP = 200.0f

		/** Hits further apart than this stop counting toward the same circle. */
		private const val CIRCLE_WINDOW = 100

		/** Six seconds of it, which is long enough to cross a sanctum twice. */
		const val GYRE_TICKS: Int = 120

		/** Arms coming out level before the turn starts. The tell. */
		const val GYRE_FLARE: Int = 12

		/** Radians per tick at full speed: a whole turn every eight ticks. */
		private const val SPIN_RATE = 0.785f

		/** Ticks after the flare before it is turning at full speed. */
		private const val SPIN_RAMP = 20.0f

		/**
		 * How far round the spin has come.
		 *
		 * Lives here rather than in the model because both sides need it and they
		 * must agree: the model turns the bones by this, and the wake particles
		 * are placed on fists that are wherever this says they are. Two copies of
		 * the formula would come apart the moment either was tuned.
		 *
		 * Both terms grow, so the angle only ever runs one way -- an accelerating
		 * spin, never a stutter. Pass `ageInTicks` on the client for a smooth
		 * value, or `tickCount` where whole ticks are all that is on offer; the
		 * former is the latter plus the partial tick, so they agree on the frame.
		 */
		fun spinAngle(gyreTick: Int, ageInTicks: Float): Float {
			if (gyreTick <= 0) return 0.0f
			val ramp = ((gyreTick - GYRE_FLARE) / SPIN_RAMP).coerceIn(0.0f, 1.0f)
			return ageInTicks * SPIN_RATE * ramp
		}

		// ---- the wake it sheds while spinning ----

		/**
		 * Where the fists and greaves sit, in blocks from the Sentinel's own
		 * centre line and above its feet.
		 *
		 * Read off the model rather than eyeballed. `fist_r` centres on Blockbench
		 * (10.5, 14.5, 0), which is model (-10.5, 9.5, 0); with the arm out level
		 * its bone rotation carries that to (-23.5, -6.5, 0), and the feet are at
		 * model y = 24. Everything below is those numbers over sixteen.
		 */
		private const val FIST_REACH = 23.5 / 16.0
		private const val FIST_HEIGHT = 30.5 / 16.0
		private const val GREAVE_REACH = 4.5 / 16.0
		private const val GREAVE_HEIGHT = 6.0 / 16.0

		/**
		 * Tangential speed given to the wake, well under the fist's own.
		 *
		 * A fist at this radius covers better than a block a tick, and a particle
		 * launched at that speed is gone before it has been seen. What is wanted
		 * is the suggestion of being flung, not the arithmetic.
		 */
		private const val FIST_WHIP = 0.28
		private const val GREAVE_WHIP = 0.10

		private const val WAKE_SCATTER = 0.25
		private const val WAKE_DRIFT = 0.05

		private const val WAKE_PER_FIST = 2
		private const val WAKE_PER_GREAVE = 1

		/** Right limbs sit at a negative offset, left at a positive one. */
		private val SIDES = doubleArrayOf(-1.0, 1.0)

		// ---- bulwark: the answer to being shot at from out of reach ----

		/** Ticks the curl takes to close. The model reads this too. */
		const val BULWARK_CURL: Int = 14

		/** How often a curled sentinel knits, and by how much. */
		private const val MEND_EVERY = 10
		private const val MEND_AMOUNT = 1.0f

		/**
		 * How near a player has to be for the siege to still count as one.
		 *
		 * Deliberately the same as FOLLOW_RANGE. Any shorter and the sentinel
		 * would open up while the target selector could still see the player, and
		 * immediately re-acquire them instead of going home.
		 */
		private const val BULWARK_WATCH_SQ = 40.0 * 40.0

		private const val GYRE_COOLDOWN = 240

		/** Nearly double pace. A walking player cannot open ground on this. */
		private const val GYRE_SPEED_BONUS = 0.95

		/** What is left of a blow that lands on moving stone. */
		private const val GYRE_ABSORB = 0.5f

		private const val WHIRL_EVERY = 14

		/** Slowest a thrown-back projectile travels, however gently it arrived. */
		private const val REFLECT_MIN_SPEED = 0.8

		/** Sent back harder than it came in. */
		private const val REFLECT_GAIN = 1.15

		/** Far enough out of its own bulk not to strike the sentinel on the way. */
		private const val REFLECT_CLEARANCE = 1.2

		private val GYRE_HASTE: Identifier = VanguardSpirits.id("gyre_haste")

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
		private const val SUNDER_GUARD_LOCK = 120
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
