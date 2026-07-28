package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes
import net.minecraft.core.Registry
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.core.registries.BuiltInRegistries

/**
 * The ambience the Guarded Ruins give off.
 *
 * Both carry no data beyond their identity, so they are simple types -- what
 * each one looks like and how it moves is entirely the client's business, and
 * lives in the matching particle class under `client/particle`.
 *
 * The sprites they animate over come from `assets/vanguard-spirits/particles/`,
 * not from code. Adding a frame is a resource edit.
 */
object ModParticles {

	/** Amber glint. Drifts through the cavern air, a few at a time. */
	val MEMORY_MOTE: SimpleParticleType = register("memory_mote")

	/**
	 * A glyph that surfaces, hangs, and fades.
	 *
	 * Registered with the limiter overridden: these are rare and deliberately
	 * placed, so they should still appear on the lowest particle setting rather
	 * than being culled with the ambient chaff.
	 */
	val ECHO_RUNE: SimpleParticleType = register("echo_rune", alwaysShow = true)

	private fun register(path: String, alwaysShow: Boolean = false): SimpleParticleType =
		Registry.register(
			BuiltInRegistries.PARTICLE_TYPE,
			VanguardSpirits.id(path),
			FabricParticleTypes.simple(alwaysShow),
		)

	/** Touching the object is what actually runs the registrations above. */
	fun register() = Unit
}
