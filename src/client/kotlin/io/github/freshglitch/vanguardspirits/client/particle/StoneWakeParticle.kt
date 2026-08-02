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
 * Air torn off something heavy moving fast.
 *
 * Thrown from the Sentinel's fists and greaves while it spins, so unlike the
 * ambient motes this one is given its velocity by whatever spawned it and only
 * has to decay convincingly: it is flung, it slows, it thins out and goes.
 *
 * Short-lived on purpose. The gyre turns roughly two and a half times a second,
 * so a wake that outlived one revolution would smear into a solid ring and the
 * spin would stop reading as a spin.
 */
class StoneWakeParticle(
	level: ClientLevel,
	x: Double,
	y: Double,
	z: Double,
	xd: Double,
	yd: Double,
	zd: Double,
	private val sprites: SpriteSet,
) : SingleQuadParticle(level, x, y, z, sprites.first()) {

	init {
		// Assigned outright rather than passed to the six-double constructor,
		// which does not set velocity at all -- it adds a random vector and
		// normalises, which is vanilla's scatter and the opposite of wanted here.
		this.xd = xd
		this.yd = yd
		this.zd = zd

		lifetime = Mth.nextInt(random, LIFE_MIN, LIFE_MAX)
		quadSize = Mth.lerp(random.nextFloat(), SIZE_MIN, SIZE_MAX)

		// Decelerates instead of coasting: the air is being dragged along, not
		// launched. Gravity would pull the ribbon into a droop.
		friction = DRAG
		gravity = 0.0f

		// Nothing to catch on. A wake that stopped dead against the wall the
		// Sentinel is fighting next to would read as a bug.
		hasPhysics = false

		// Each crescent is turned differently, so a burst of them reads as
		// turbulence rather than as one shape stamped out several times.
		roll = random.nextFloat() * Mth.TWO_PI

		setColor(TINT_R, TINT_G, TINT_B)
		setSpriteFromAge(sprites)
	}

	override fun getLayer(): Layer = Layer.TRANSLUCENT

	/**
	 * Kept legible in an unlit crypt.
	 *
	 * Not the full-brightness treatment the motes get -- those are lit from
	 * within and this is not. Enough to be seen against black deepslate, and no
	 * more, so it still reads as air rather than as something glowing.
	 */
	override fun getLightCoords(partialTick: Float): Int =
		LightCoordsUtil.withBlock(super.getLightCoords(partialTick), WAKE_LIGHT)

	override fun tick() {
		super.tick()
		if (!isAlive) return

		val p = age.toFloat() / lifetime.toFloat()

		// The sprite carries most of the fade; this takes the last of it off so
		// nothing pops out of existence mid-frame.
		alpha = (1.0f - p * p) * PEAK_ALPHA
		roll += SPIN_OUT

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
		): Particle = StoneWakeParticle(level, x, y, z, xd, yd, zd, sprites)
	}

	private companion object {
		/** Bright enough to read in the dark without looking lit from within. */
		const val WAKE_LIGHT = 11

		const val LIFE_MIN = 7
		const val LIFE_MAX = 13

		const val SIZE_MIN = 0.16f
		const val SIZE_MAX = 0.30f

		/** Sheds most of its speed within half a second. */
		const val DRAG = 0.82f

		/** Radians per tick the crescent turns as it trails away. */
		const val SPIN_OUT = 0.12f

		const val PEAK_ALPHA = 0.85f

		const val TINT_R = 0.86f
		const val TINT_G = 0.93f
		const val TINT_B = 1.0f
	}
}
