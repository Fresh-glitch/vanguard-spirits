package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory

/**
 * Status effects this mod defines.
 *
 * Only one, and only because a charm needed something vanilla has no equivalent
 * of. Everything else the charms grant is a vanilla effect on purpose: a player
 * already knows what speed does.
 */
object ModEffects {

	/**
	 * Sends back whatever is thrown at the holder.
	 *
	 * A real effect rather than behaviour hung off the charm, so it shows in the
	 * HUD beside the others and so the aura machinery does not need a special
	 * case. What it actually does lives in
	 * [io.github.freshglitch.vanguardspirits.charm.Deflection] -- an effect on
	 * its own is only a marker.
	 */
	val DEFLECTION: Holder<MobEffect> = Registry.registerForHolder(
		BuiltInRegistries.MOB_EFFECT,
		VanguardSpirits.id("deflection"),
		object : MobEffect(MobEffectCategory.BENEFICIAL, TINT) {},
	)

	/** The violet the charm's rune is painted in, so icon and item agree. */
	private const val TINT = 0x9B6BD8

	/** Touching the object is what runs the registration above. */
	fun register() = Unit
}
