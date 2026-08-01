package io.github.freshglitch.vanguardspirits.entity

import io.github.freshglitch.vanguardspirits.registry.ModSounds
import io.github.freshglitch.vanguardspirits.worldgen.RuinVigil
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.util.Mth
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
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
 */
class Mourner(type: EntityType<out Mourner>, level: Level) : Mob(type, level) {

	/** The point it circles: directly over the ruin, well above the treeline. */
	private var anchor: BlockPos? = null

	/** Where it currently is on that circle, in radians. */
	private var orbit = 0.0f

	/** Ticks left of being spooked. Not saved -- it should settle on reload. */
	private var alarm = 0

	init {
		isNoGravity = true
	}

	override fun registerGoals() {
		// None. Movement is driven outright in customServerAiStep, because every
		// goal worth having would take it somewhere else.
	}

	fun setAnchor(pos: BlockPos, startAngle: Float) {
		anchor = pos
		orbit = startAngle
	}

	fun anchoredNear(pos: BlockPos): Boolean {
		val home = anchor ?: return false
		return home.distSqr(pos) < SAME_RUIN_SQ
	}

	override fun customServerAiStep(level: ServerLevel) {
		super.customServerAiStep(level)

		val home = anchor ?: return
		if (alarm > 0) alarm--

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
		if (hurt && isAlive) alarm = ALARM_TICKS
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

	/** Nothing about a landmark should be edible or worth attacking. */
	override fun isPushable(): Boolean = false

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

		fun createAttributes(): AttributeSupplier.Builder = createMobAttributes()
			.add(Attributes.MAX_HEALTH, 8.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FLYING_SPEED, 0.6)
	}
}
