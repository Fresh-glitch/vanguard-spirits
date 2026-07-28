package io.github.freshglitch.vanguardspirits.charm

import net.minecraft.core.Holder
import net.minecraft.world.effect.MobEffect

/**
 * The passive boon a charm radiates while a player carries it.
 *
 * [amplifier] follows the vanilla convention and is zero-based: 0 is level I.
 */
data class CharmAura(
	val effect: Holder<MobEffect>,
	val amplifier: Int = 0,
)
