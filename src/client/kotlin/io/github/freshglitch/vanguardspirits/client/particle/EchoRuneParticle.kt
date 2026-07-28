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
 * A glyph surfacing out of the dark, holding, and sinking back.
 *
 * The rarest thing the ruins do. Unlike [MemoryMoteParticle] the sprite is
 * picked once and kept -- variety comes from which of the six glyphs is drawn,
 * so animating through them would read as one rune flicking between shapes
 * rather than as six distinct marks.
 *
 * Everything else is the fade. A rune barely moves; it is the alpha envelope
 * that makes it feel like something remembered rather than something rendered.
 */
class EchoRuneParticle(
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

		// A slow lift, so the glyph is not pinned in the air like a decal.
		yd = Mth.lerp(random.nextFloat(), RISE_MIN, RISE_MAX).toDouble()

		setAlpha(0.0f)
	}

	override fun getLayer(): Layer = Layer.TRANSLUCENT

	override fun getLightCoords(partialTick: Float): Int =
		LightCoordsUtil.withBlock(super.getLightCoords(partialTick), FULL_BLOCK_LIGHT)

	override fun tick() {
		super.tick()
		if (!isAlive) return

		setAlpha(envelope(age.toFloat() / lifetime))
	}

	/**
	 * Fades in fast, holds, then fades out slowly.
	 *
	 * The asymmetry is the point: a rune should arrive as if it were always
	 * there and leave as if it were being forgotten.
	 */
	private fun envelope(progress: Float): Float = when {
		progress < FADE_IN -> progress / FADE_IN
		progress < FADE_OUT -> 1.0f
		else -> 1.0f - (progress - FADE_OUT) / (1.0f - FADE_OUT)
	} * PEAK_ALPHA

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
		): Particle = EchoRuneParticle(level, x, y, z, sprites)
	}

	private companion object {
		const val FULL_BLOCK_LIGHT = 15

		const val LIFE_MIN = 80
		const val LIFE_MAX = 120

		/** Far larger than a mote. A rune is meant to be read, not glimpsed. */
		const val SIZE_MIN = 0.34f
		const val SIZE_MAX = 0.46f

		const val RISE_MIN = 0.002f
		const val RISE_MAX = 0.006f

		/** Fractions of the lifetime spent arriving and beginning to leave. */
		const val FADE_IN = 0.18f
		const val FADE_OUT = 0.45f

		/** Held back from opaque so the glyph always reads as a projection. */
		const val PEAK_ALPHA = 0.85f
	}
}
