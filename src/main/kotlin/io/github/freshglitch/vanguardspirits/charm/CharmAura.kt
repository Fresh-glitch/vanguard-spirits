package io.github.freshglitch.vanguardspirits.charm

import net.minecraft.core.Holder
import net.minecraft.world.effect.MobEffect

/**
 * One effect a charm radiates, and how strongly.
 *
 * [amplifier] follows the vanilla convention and is zero-based: 0 is level I.
 */
data class CharmGrant(
	val effect: Holder<MobEffect>,
	val amplifier: Int = 0,
)

/**
 * Everything a charm radiates at one depth, and what carrying it costs.
 *
 * ## Why a list of effects rather than one
 *
 * Deepening a charm cannot always mean raising an amplifier, because for two of
 * the four it would mean nothing at all. `GameRenderer.nightVisionScale` reads
 * only `MobEffectInstance.getDuration` -- there is no `getAmplifier` call
 * anywhere in it -- so night vision has exactly one strength and a Delver II
 * built on amplifier would be identical to a Delver I while costing twice as
 * much. Deflection is ours and has no amplifier to read in the first place.
 *
 * So a deeper binding gives *more of the person*, which is an amplifier where
 * that means something and a second effect where it does not.
 */
data class CharmAura(
	val grants: List<CharmGrant>,
	/**
	 * How much of the holder's attunement this depth takes up.
	 *
	 * Rising cost is what makes deepening a decision rather than an upgrade: at
	 * the cap of four, one charm at depth III and one at depth I is the whole
	 * budget, and so is three shallow charms.
	 */
	val cost: Int = 1,
) {
	/** The common case: one effect, one depth. */
	constructor(effect: Holder<MobEffect>, amplifier: Int = 0, cost: Int = 1) :
		this(listOf(CharmGrant(effect, amplifier)), cost)

	init {
		require(grants.isNotEmpty()) { "a depth that grants nothing is a charm that does nothing" }
		require(cost >= 1) { "a free charm would make attunement meaningless" }
	}
}
