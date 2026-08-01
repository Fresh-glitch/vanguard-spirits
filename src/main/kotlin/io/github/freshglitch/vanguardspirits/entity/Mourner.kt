package io.github.freshglitch.vanguardspirits.entity

import io.github.freshglitch.vanguardspirits.registry.ModSounds
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
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
		orbit += ORBIT_STEP

		val target = Vec3Target(
			home.x + 0.5 + Mth.cos(orbit.toDouble()) * ORBIT_RADIUS,
			home.y + Mth.sin((tickCount * BOB_RATE).toDouble()) * BOB,
			home.z + 0.5 + Mth.sin(orbit.toDouble()) * ORBIT_RADIUS,
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

	/** Nothing about a landmark should be edible or worth attacking. */
	override fun isPushable(): Boolean = false

	override fun causeFallDamage(distance: Double, multiplier: Float, source: net.minecraft.world.damagesource.DamageSource): Boolean = false

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

		fun createAttributes(): AttributeSupplier.Builder = createMobAttributes()
			.add(Attributes.MAX_HEALTH, 8.0)
			.add(Attributes.MOVEMENT_SPEED, 0.3)
			.add(Attributes.FLYING_SPEED, 0.6)
	}
}
