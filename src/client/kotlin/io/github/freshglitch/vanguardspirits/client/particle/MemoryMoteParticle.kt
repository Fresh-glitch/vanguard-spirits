package io.github.freshglitch.vanguardspirits.client.particle

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.SingleQuadParticle
import net.minecraft.client.particle.SpriteSet
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.util.LightCoordsUtil
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource

/**
 * An amber glint drifting through a Guarded Ruin.
 *
 * Rises steadily while wandering sideways, and steps through its eight sprite
 * frames as it ages, so it blooms on arrival and thins out before it goes. The
 * shape of that bloom lives entirely in the sprites -- this class only decides
 * where the mote is.
 *
 * Velocity is assigned outright each tick rather than nudged. [Particle.tick]
 * damps `xd`/`zd` by friction after moving, which would flatten a sway added
 * before the call into a straight line within a second or two.
 */
class MemoryMoteParticle(
	level: ClientLevel,
	x: Double,
	y: Double,
	z: Double,
	private val sprites: SpriteSet,
) : SingleQuadParticle(level, x, y, z, sprites.first()) {

	/**
	 * Keeps every mote out of phase with its neighbours. Held as a double
	 * because Mth.cos/sin take one, even though they return a float.
	 */
	private val phase = random.nextFloat().toDouble() * Mth.TWO_PI

	private val rise = Mth.lerp(random.nextFloat(), RISE_MIN, RISE_MAX)
	private val sway = Mth.lerp(random.nextFloat(), SWAY_MIN, SWAY_MAX)

	init {
		lifetime = Mth.nextInt(random, LIFE_MIN, LIFE_MAX)
		quadSize = Mth.lerp(random.nextFloat(), SIZE_MIN, SIZE_MAX)

		// Gravity and friction are both bypassed by the assignments in tick, so
		// leaving them at their defaults would only be misleading.
		gravity = 0.0f
		friction = 1.0f

		// Motes are echoes, not objects. Letting one snag on a pillar and hang
		// there reads as a stuck particle rather than as anything haunting.
		hasPhysics = false

		setSpriteFromAge(sprites)
	}

	override fun getLayer(): Layer = Layer.TRANSLUCENT

	/** Lit from within, so a mote reads the same in the dark as under a lantern. */
	override fun getLightCoords(partialTick: Float): Int =
		LightCoordsUtil.withBlock(super.getLightCoords(partialTick), FULL_BLOCK_LIGHT)

	override fun tick() {
		super.tick()
		if (!isAlive) return

		// Two circles at different rates, so the path never closes on itself and
		// the drift does not read as an orbit.
		val t = age * SWAY_RATE + phase
		xd = (Mth.cos(t) * sway).toDouble()
		zd = (Mth.sin(t * SWAY_SKEW) * sway).toDouble()
		yd = rise.toDouble()

		setSpriteFromAge(sprites)
	}

	class Provider(private val sprites: SpriteSet) : ParticleProvider<SimpleParticleType> {
		override fun createParticle(
			options: SimpleParticleType,
			level: ClientLevel,
			x: Double,
			y: Double,
			z: Double,
			xd: Double,
			yd: Double,
			zd: Double,
			random: RandomSource,
		): Particle = MemoryMoteParticle(level, x, y, z, sprites)
	}

	private companion object {
		const val FULL_BLOCK_LIGHT = 15

		const val LIFE_MIN = 70
		const val LIFE_MAX = 130

		const val SIZE_MIN = 0.09f
		const val SIZE_MAX = 0.15f

		const val RISE_MIN = 0.004f
		const val RISE_MAX = 0.013f

		const val SWAY_MIN = 0.006f
		const val SWAY_MAX = 0.016f

		/** Radians per tick. A full wander takes roughly six seconds. */
		const val SWAY_RATE = 0.055f

		/** Deliberately not a whole ratio, so the two axes never resynchronise. */
		const val SWAY_SKEW = 0.77f
	}
}
