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
 * The same glyphs as [EchoRuneParticle], thrown off a Reliquary refusing a claim.
 *
 * A separate type rather than a parameter because the two want opposite things.
 * An echo rune is meant to be read: large, and on screen for four seconds or
 * more. A ward rune is one frame of a moving shell, and at that size and
 * lifetime the shell stops being a shell -- every ring the burst emits is still
 * hanging in the air when the next one goes out, and the whole thing sets into a
 * solid ball of glyphs.
 *
 * So: small, and gone almost at once. What should persist is the impression of
 * a front passing through, not the runes themselves.
 */
class WardRuneParticle(
	level: ClientLevel,
	x: Double,
	y: Double,
	z: Double,
	sprites: SpriteSet,
) : SingleQuadParticle(level, x, y, z, sprites.get(level.random)) {

	init {
		lifetime = Mth.nextInt(random, LIFE_MIN, LIFE_MAX)
		quadSize = Mth.lerp(random.nextFloat(), SIZE_MIN, SIZE_MAX)

		gravity = 0.0f
		friction = 1.0f
		hasPhysics = false

		// Stationary. The expansion is in where each one is spawned, not in how
		// it moves -- a rune that drifted as well would smear the front.
		xd = 0.0
		yd = 0.0
		zd = 0.0

		setAlpha(0.0f)
	}

	override fun getLayer(): Layer = Layer.TRANSLUCENT

	override fun getLightCoords(partialTick: Float): Int =
		LightCoordsUtil.withBlock(super.getLightCoords(partialTick), FULL_BLOCK_LIGHT)

	override fun tick() {
		super.tick()
		if (!isAlive) return

		// Straight up and straight down, unlike the echo rune's long hold. It is
		// a flicker of something passing, not an apparition.
		val p = age.toFloat() / lifetime
		setAlpha((if (p < FADE_IN) p / FADE_IN else 1.0f - (p - FADE_IN) / (1.0f - FADE_IN)) * PEAK_ALPHA)
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
		): Particle = WardRuneParticle(level, x, y, z, sprites)
	}

	private companion object {
		const val FULL_BLOCK_LIGHT = 15

		/** Well under a second, so the shell never catches up with itself. */
		const val LIFE_MIN = 9
		const val LIFE_MAX = 16

		/** A third of an echo rune. Dozens at once, not one being studied. */
		const val SIZE_MIN = 0.10f
		const val SIZE_MAX = 0.17f

		const val FADE_IN = 0.3f
		const val PEAK_ALPHA = 0.9f
	}
}
