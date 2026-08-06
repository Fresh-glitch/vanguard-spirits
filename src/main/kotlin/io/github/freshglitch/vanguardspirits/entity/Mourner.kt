package io.github.freshglitch.vanguardspirits.entity

import io.github.freshglitch.vanguardspirits.registry.ModItems
import io.github.freshglitch.vanguardspirits.registry.ModSounds
import io.github.freshglitch.vanguardspirits.worldgen.RuinVigil
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.tags.BlockTags
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.phys.Vec3
import net.minecraft.util.Mth
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.PanicGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.TemptGoal
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

/**
 * A bird that will not leave a particular patch of sky.
 *
 * There was no way to find a Guarded Ruin. They sit thirty blocks under the
 * deepslate with nothing on the surface to say so, which made the best content
 * in the mod something a player found by accident or not at all. Mourners are
 * the sign: they circle over a ruin and nowhere else, so a dark shape turning
 * slowly above a hillside means there is something beneath it.
 *
 * It flies on an orbit rather than by pathfinding. A navigator would wander,
 * get distracted, or settle in a tree, and the whole point is that this bird is
 * a landmark and has to stay put.
 *
 * **Only while it has a ruin to watch.** One with no anchor -- summoned, or from
 * a spawn egg -- is not a landmark and has no circle to hold, so it is an
 * ordinary ground bird instead: it walks, it panics, it follows seeds. Without
 * that it simply hung in the air with its wings beating and nothing to do.
 * [becomeSentry] is where the one turns into the other.
 */
class Mourner(type: EntityType<out Mourner>, level: Level) : PathfinderMob(type, level) {

	/** The point it circles: directly over the ruin, well above the treeline. */
	private var anchor: BlockPos? = null

	/** Where it currently is on that circle, in radians. */
	private var orbit = 0.0f

	/** Ticks left of being spooked. Not saved -- it should settle on reload. */
	private var alarm = 0

	/** The leaf it is heading for or sitting on, if any. */
	private var perch: BlockPos? = null

	/** Ticks left of sitting. Counts only once it has actually arrived. */
	private var roost = 0

	/**
	 * Ticks before it can lose another feather.
	 *
	 * Not saved, like [alarm] -- a bird that has just loaded in owes nobody a
	 * cooldown, and the worst a reload can buy is one feather early.
	 */
	private var shedCooldown = 0

	/**
	 * What an unanchored bird does with itself.
	 *
	 * Registered for every Mourner, because goals are built in the constructor
	 * and whether this one has a ruin is not known until the beacon says so or
	 * its save data loads. [becomeSentry] throws them all away at that point,
	 * which is cheaper than teaching six goals to check an anchor apiece.
	 */
	override fun registerGoals() {
		goalSelector.addGoal(0, FloatGoal(this))
		goalSelector.addGoal(1, PanicGoal(this, PANIC_SPEED))
		goalSelector.addGoal(2, TemptGoal(this, TEMPT_SPEED, { it.`is`(ItemTags.PARROT_FOOD) }, false))
		goalSelector.addGoal(3, WaterAvoidingRandomStrollGoal(this, STROLL_SPEED))
		goalSelector.addGoal(4, LookAtPlayerGoal(this, Player::class.java, WATCH_RANGE))
		goalSelector.addGoal(5, RandomLookAroundGoal(this))
	}

	/**
	 * Turns a bird into a landmark, for good.
	 *
	 * Everything the ground behaviour needs is dropped rather than suppressed:
	 * the goals go, the navigator stops, and gravity comes off, because from
	 * here on the orbit in [customServerAiStep] owns its movement outright and
	 * anything else touching it would only fight.
	 */
	private fun becomeSentry() {
		goalSelector.removeAllGoals { true }
		navigation.stop()
		isNoGravity = true
	}

	override fun defineSynchedData(builder: SynchedEntityData.Builder) {
		super.defineSynchedData(builder)
		builder.define(DATA_PERCHED, false)
	}

	/** Sitting rather than flying. The client folds the wings on this. */
	var isPerched: Boolean
		get() = entityData.get(DATA_PERCHED)
		private set(value) = entityData.set(DATA_PERCHED, value)

	fun setAnchor(pos: BlockPos, startAngle: Float) {
		anchor = pos
		orbit = startAngle
		becomeSentry()
	}

	/** Whether it is walking the ground rather than holding a circle over a ruin. */
	val isWild: Boolean
		get() = anchor == null

	fun anchoredNear(pos: BlockPos): Boolean {
		val home = anchor ?: return false
		return home.distSqr(pos) < SAME_RUIN_SQ
	}

	override fun customServerAiStep(level: ServerLevel) {
		super.customServerAiStep(level)

		// Above the anchor check, because both of these belong to any Mourner.
		// A wild one can be startled too, and until this moved up its alarm
		// never wound back down.
		if (alarm > 0) alarm--
		if (shedCooldown > 0) shedCooldown--

		val home = anchor ?: return

		// Perching takes priority over the circle entirely: a bird heading for a
		// branch has stopped patrolling.
		if (perch != null) {
			roostStep(level)
			return
		}
		if (tickCount % PERCH_LOOK_INTERVAL == 0) lookForPerch(level, home)

		// Three things bend the circle, so it never runs at one radius and one
		// height for long: a slow breath in and out, whether anyone is standing
		// under it, and whether it has just been shot at.
		val breath = Mth.sin((tickCount * BREATH_RATE).toDouble())
		val watcher = level.getNearestPlayer(
			home.x + 0.5, home.y.toDouble(), home.z + 0.5,
			STOOP_RANGE, /* filter = */ null,
		)

		val startled = alarm > 0
		orbit += if (startled) ORBIT_STEP * FLEE_RUSH else ORBIT_STEP

		var radius = ORBIT_RADIUS + breath * BREATH_SPREAD
		var height = home.y + Mth.sin((tickCount * BOB_RATE).toDouble()) * BOB

		if (startled) {
			// Up and out. It wants distance, not a fight.
			radius += FLEE_SPREAD
			height += FLEE_CLIMB
		} else if (watcher != null) {
			// Comes down for a closer look at whoever is standing on the grave.
			radius -= STOOP_TIGHTEN
			height -= STOOP_DROP
		}

		val target = Vec3Target(
			home.x + 0.5 + Mth.cos(orbit.toDouble()) * radius,
			height,
			home.z + 0.5 + Mth.sin(orbit.toDouble()) * radius,
		)

		// Steer toward the point rather than snapping to it, so the turn has
		// some weight and the bird banks into the circle instead of tracking it
		// like a cursor.
		val steer = deltaMovement
			.add(
				(target.x - x) * TURN,
				(target.y - y) * CLIMB,
				(target.z - z) * TURN,
			)
			.scale(DRAG)
		deltaMovement = steer

		if (steer.horizontalDistanceSqr() > 1.0e-4) {
			yRot = (-Mth.atan2(steer.x, steer.z) * Mth.RAD_TO_DEG).toFloat()
			yBodyRot = yRot
			yHeadRot = yRot
		}
	}

	private data class Vec3Target(val x: Double, val y: Double, val z: Double)

	// ---------------------------------------------------------------- perching

	/**
	 * Looks for a branch in the stretch of circle it is about to fly through.
	 *
	 * Only ahead, never behind or to the side: a bird that spots a tree it has
	 * already passed and doubles back looks like it changed its mind, where one
	 * that drops onto something in front of it looks like it saw it coming.
	 */
	private fun lookForPerch(level: ServerLevel, home: BlockPos) {
		if (alarm > 0) return
		if (random.nextFloat() > PERCH_CHANCE) return

		val ahead = orbit + PERCH_LOOKAHEAD
		val x = Mth.floor(home.x + 0.5 + Mth.cos(ahead.toDouble()) * ORBIT_RADIUS)
		val z = Mth.floor(home.z + 0.5 + Mth.sin(ahead.toDouble()) * ORBIT_RADIUS)

		val cursor = BlockPos.MutableBlockPos()
		for (y in home.y downTo home.y - PERCH_SEARCH_DEPTH) {
			cursor.set(x, y, z)
			if (!level.getBlockState(cursor).`is`(BlockTags.LEAVES)) continue

			// The first leaf going down is the top of the canopy. Anything below
			// that is inside the tree, which is no place to sit.
			val seat = BlockPos(x, y + 1, z)
			if (!level.getBlockState(seat).isAir) return

			perch = seat
			roost = Mth.nextInt(random, ROOST_MIN, ROOST_MAX)
			return
		}
	}

	/** Flying down to a branch, or sitting on one. */
	private fun roostStep(level: ServerLevel) {
		val seat = perch ?: return

		// Anything that would make sitting there wrong: startled, the tree gone,
		// or somebody standing underneath it.
		val stillLeaves = level.getBlockState(seat.below()).`is`(BlockTags.LEAVES)
		if (alarm > 0 || !stillLeaves) {
			takeOff(level, startled = true)
			return
		}

		val cx = seat.x + 0.5
		val cy = seat.y.toDouble()
		val cz = seat.z + 0.5

		if (!isPerched) {
			if (distanceToSqr(cx, cy, cz) < LANDED_SQ) {
				isPerched = true
				snapTo(cx, cy, cz, yRot, 0.0f)
				deltaMovement = Vec3.ZERO
				return
			}

			// Steer in harder than the orbit does. A slow drift onto a branch
			// reads as being blown there rather than choosing to land.
			deltaMovement = deltaMovement
				.add((cx - x) * LAND_PULL, (cy - y) * LAND_PULL, (cz - z) * LAND_PULL)
				.scale(LAND_DRAG)
			return
		}

		deltaMovement = Vec3.ZERO
		roost--

		// Slow head turns while it sits, so a perched bird is not a statue.
		if (tickCount % LOOK_INTERVAL == 0) {
			yRot += (random.nextFloat() - 0.5f) * LOOK_SWEEP
			yBodyRot = yRot
			yHeadRot = yRot
		}

		// A bird whose time on the branch simply ran out has not been startled;
		// one that a player walked up on has. Only the second sheds, or every
		// roost would pay out on its own and standing still would be the way to
		// farm feathers.
		val flushed = level.getNearestPlayer(cx, cy, cz, FLUSH_RANGE, null) != null
		if (roost <= 0 || flushed) takeOff(level, startled = flushed)
	}

	/**
	 * Back to the circle, and sometimes a feather short.
	 *
	 * The shed is deliberately the *only* worthwhile thing a Mourner gives up,
	 * because [die] ends its ruin's vigil for good and a kill that paid better
	 * than a fright would make shooting the landmark the correct play. Flushing
	 * the same bird off its branch every few minutes is meant to beat killing it
	 * once, and [SHED_COOLDOWN] is what stops that becoming a fast farm.
	 */
	private fun takeOff(level: ServerLevel, startled: Boolean) {
		if (isPerched) deltaMovement = deltaMovement.add(0.0, TAKEOFF_KICK, 0.0)
		perch = null
		roost = 0
		isPerched = false

		if (!startled || shedCooldown > 0) return
		if (random.nextFloat() >= SHED_CHANCE) return

		shedCooldown = SHED_COOLDOWN
		spawnAtLocation(level, ModItems.MOURNER_FEATHER)
	}

	override fun getAmbientSound(): SoundEvent = ModSounds.MOURNER_CALL

	/**
	 * Calls rarely.
	 *
	 * The default is roughly every four seconds, which for a bird meant to be
	 * heard across a valley would turn from a landmark into a nuisance inside a
	 * minute. Half a minute apart leaves it as something you notice.
	 */
	override fun getAmbientSoundInterval(): Int = CALL_INTERVAL

	/**
	 * Startles rather than fights.
	 *
	 * It has no attack and no business having one, but a bird that took an arrow
	 * without so much as flinching read as scenery. Now it breaks off, climbs,
	 * and calls -- which also warns anyone nearby that something just happened.
	 */
	override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
		val hurt = super.hurtServer(level, source, amount)
		if (hurt && isAlive) {
			alarm = ALARM_TICKS
			takeOff(level, startled = true)
		}
		return hurt
	}

	override fun getHurtSound(source: DamageSource): SoundEvent = ModSounds.MOURNER_HURT

	override fun getDeathSound(): SoundEvent = ModSounds.MOURNER_HURT

	/**
	 * Killing one ends the vigil over that ruin for good.
	 *
	 * Marked on the first death rather than the last: once a player has started
	 * shooting them down, replacing the ones they killed is the behaviour that
	 * made this feel like scenery rather than a landmark.
	 */
	override fun die(source: DamageSource) {
		val home = anchor
		val level = level()
		if (home != null && !level.isClientSide) RuinVigil.breakVigil(level, home)
		super.die(source)
	}

	/**
	 * A bird on foot leaves feathers. A landmark leaves a ruin nobody can find.
	 *
	 * The vigil is what killing an anchored Mourner costs, and paying out on top
	 * of that would make the loss look like a price rather than a mistake. So the
	 * two halves of the mob are split here: [isWild] ones drop like any other
	 * bird, and the ones circling a ruin drop nothing at all.
	 *
	 * `shouldDropLoot` rather than a second loot table, because `Mob.getLootTable`
	 * is final and cannot be swapped per entity. The bytecode of
	 * `LivingEntity.dropAllDeathLoot` shows this gates `dropFromLootTable` and
	 * `dropCustomDeathLoot` while leaving `dropEquipment` and `dropExperience`
	 * alone -- which is exactly the seam wanted, since the kill should still be
	 * worth the ordinary experience.
	 */
	override fun shouldDropLoot(level: ServerLevel): Boolean =
		isWild && super.shouldDropLoot(level)

	/**
	 * Takes seeds from the hand, and mends on them.
	 *
	 * The same tag vanilla feeds parrots on, so it is the seeds a player already
	 * expects a bird to want rather than a list to look up. Works on a sentry as
	 * well as a wild one -- they stoop low enough to reach, and a player who has
	 * worked out they can patch up the thing they shot deserves to.
	 */
	override fun mobInteract(player: Player, hand: InteractionHand): InteractionResult {
		val held = player.getItemInHand(hand)
		if (!held.`is`(ItemTags.PARROT_FOOD)) return super.mobInteract(player, hand)

		// Nothing to mend. Passing rather than eating leaves the seeds in hand,
		// which is what a player expects when the bird is already whole.
		if (health >= maxHealth) return super.mobInteract(player, hand)

		if (!player.hasInfiniteMaterials()) held.shrink(1)

		val level = level()
		if (level is ServerLevel) {
			heal(SEED_MEND)
			level.sendParticles(
				ParticleTypes.HAPPY_VILLAGER,
				x, y + bbHeight * 0.7, z,
				6,
				0.3, 0.3, 0.3,
				0.0,
			)
			playSound(ModSounds.MOURNER_CALL, 0.4f, 1.4f)
		}

		return InteractionResult.SUCCESS
	}

	/** A landmark cannot be shoved off its circle. One on foot is just a bird. */
	override fun isPushable(): Boolean = isWild

	override fun causeFallDamage(distance: Double, multiplier: Float, source: DamageSource): Boolean = false

	override fun addAdditionalSaveData(output: ValueOutput) {
		super.addAdditionalSaveData(output)
		anchor?.let {
			output.putInt(TAG_X, it.x)
			output.putInt(TAG_Y, it.y)
			output.putInt(TAG_Z, it.z)
		}
	}

	override fun readAdditionalSaveData(input: ValueInput) {
		super.readAdditionalSaveData(input)
		val x = input.getInt(TAG_X)
		val y = input.getInt(TAG_Y)
		val z = input.getInt(TAG_Z)
		anchor = if (x.isPresent && y.isPresent && z.isPresent) {
			BlockPos(x.get(), y.get(), z.get())
		} else {
			null
		}

		// Goals were registered by the constructor, before there was any way to
		// know this one had a ruin. Now there is.
		if (anchor != null) becomeSentry()
	}

	companion object {
		private const val TAG_X = "AnchorX"
		private const val TAG_Y = "AnchorY"
		private const val TAG_Z = "AnchorZ"

		/** Radians per tick. A full turn takes about twenty seconds. */
		private const val ORBIT_STEP = 0.016f

		const val ORBIT_RADIUS: Double = 9.0

		private const val BOB_RATE = 0.04f
		private const val BOB = 1.6

		private const val TURN = 0.035
		private const val CLIMB = 0.05
		private const val DRAG = 0.91

		/** Two anchors closer than this are the same ruin. */
		private val DATA_PERCHED: EntityDataAccessor<Boolean> =
			SynchedEntityData.defineId(Mourner::class.java, EntityDataSerializers.BOOLEAN)

		private const val SAME_RUIN_SQ = 24.0 * 24.0

		/** Ticks between calls. Roughly half a minute. */
		private const val CALL_INTERVAL = 600

		/** How long it stays rattled after a hit. About eight seconds. */
		private const val ALARM_TICKS = 160

		private const val FLEE_RUSH = 2.4f
		private const val FLEE_SPREAD = 5.0
		private const val FLEE_CLIMB = 7.0

		/** A slow swell in and out of the circle, so it is never mechanical. */
		private const val BREATH_RATE = 0.011f
		private const val BREATH_SPREAD = 2.5

		/** How close someone has to stand before it drops in to look at them. */
		private const val STOOP_RANGE = 26.0
		private const val STOOP_TIGHTEN = 2.5
		private const val STOOP_DROP = 4.0

		// ---- perching ----

		/** How often it bothers looking for a branch, and how often it takes one. */
		private const val PERCH_LOOK_INTERVAL = 40
		private const val PERCH_CHANCE = 0.35f

		/** How far around the circle it looks. A quarter turn ahead of itself. */
		private const val PERCH_LOOKAHEAD = 1.5f

		/** Blocks below the flight line worth searching. Clears any canopy. */
		private const val PERCH_SEARCH_DEPTH = 22

		/** Roughly twenty seconds to a minute of sitting. */
		private const val ROOST_MIN = 400
		private const val ROOST_MAX = 1200

		private const val LANDED_SQ = 0.6
		private const val LAND_PULL = 0.09
		private const val LAND_DRAG = 0.82
		private const val TAKEOFF_KICK = 0.35

		/** Head turns while sitting. */
		private const val LOOK_INTERVAL = 50
		private const val LOOK_SWEEP = 70.0f

		/** How close someone has to get before it gives up the branch. */
		private const val FLUSH_RANGE = 8.0

		// ---- shedding ----

		/**
		 * How often a fright actually costs it a feather.
		 *
		 * Better than one in three, so a player who has noticed the trick is
		 * rewarded within a couple of attempts rather than left wondering
		 * whether the mechanic exists at all.
		 */
		private const val SHED_CHANCE = 0.4f

		/**
		 * Two and a half minutes between feathers.
		 *
		 * The long pole in the whole design. Without it a bow and a perched bird
		 * is an unlimited feather machine, and the [SHED_CHANCE] above would be
		 * meaningless -- any roll repeats until it passes. With it, farming one
		 * Mourner has a hard ceiling and finding several is the faster route,
		 * which means going and looking at more ruins.
		 */
		private const val SHED_COOLDOWN = 3000

		// ---- on foot, with no ruin to watch ----

		private const val PANIC_SPEED = 1.4
		private const val TEMPT_SPEED = 1.1
		private const val STROLL_SPEED = 0.9
		private const val WATCH_RANGE = 6.0f

		/** Two hearts a seed. Eight health means four seeds from the brink. */
		private const val SEED_MEND = 4.0f

		fun createAttributes(): AttributeSupplier.Builder = createMobAttributes()
			.add(Attributes.MAX_HEALTH, 8.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FLYING_SPEED, 0.6)
			// Required by TemptGoal, which reads it in canUse and throws if it is
			// missing. Only createAnimalAttributes declares it, and this is not an
			// Animal, so it has to be added by hand.
			.add(Attributes.TEMPT_RANGE, 10.0)
	}
}
