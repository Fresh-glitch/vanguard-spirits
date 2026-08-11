package io.github.freshglitch.vanguardspirits.charm

import io.github.freshglitch.vanguardspirits.item.CharmItem
import io.github.freshglitch.vanguardspirits.registry.ModComponents
import io.github.freshglitch.vanguardspirits.registry.ModItems
import net.minecraft.world.item.ItemStack

/**
 * What it takes to bind a charm one depth further, and what comes out.
 *
 * The single source of truth for deepening, and in the common source set for the
 * same reason [CharmScan] is: the menu builds the result on the server and the
 * screen explains the price on the client. If they disagree the panel lies about
 * a cost the player is being charged.
 */
object Binding {

	/**
	 * Fractured Memories needed to reach [depth] from the one below it.
	 *
	 * Eight for the second binding and sixteen for the third, so a charm taken
	 * all the way costs twenty-four on top of the four it took to make. That is
	 * several Reliquaries' worth, which is the point -- deepening is the sink the
	 * memory economy did not have, and a hollowed graveyard is what makes paying
	 * it a session rather than a pilgrimage.
	 */
	fun priceOf(depth: Int): Int = STEP * (depth - 1)

	/**
	 * The deeper charm [charm] would become, or null if this binding cannot happen.
	 *
	 * Null covers every refusal with one answer -- not a charm, already at its
	 * deepest, wrong payment, not enough of it -- because the menu has exactly one
	 * thing to do about any of them: show an empty result slot.
	 */
	fun result(charm: ItemStack, payment: ItemStack): ItemStack? {
		val item = charm.item as? CharmItem ?: return null

		val next = CharmItem.depthOf(charm) + 1
		if (next > item.maxDepth) return null

		if (!payment.`is`(ModItems.FRACTURED_MEMORY)) return null
		if (payment.count < priceOf(next)) return null

		// A copy, so the input slot still holds the shallow charm until the
		// player actually takes the result. Everything else on the stack rides
		// along -- a renamed charm keeps its name, and one left switched off
		// comes back off, because the binding is not what decides either.
		return charm.copy().apply {
			count = 1
			set(ModComponents.CHARM_DEPTH, next)
		}
	}

	// There is deliberately no `priceTaken(result)` helper here.
	//
	// There was one, and it is how the altar came to deepen charms for free. It
	// invited the caller to price a binding from the stack being carried away,
	// and that stack is empty by the time a shift-click reaches `onTake` --
	// `ItemCombinerMenu.quickMoveStack` shrinks the slot's live stack in place
	// and then passes that same stack on. An empty stack has no depth component,
	// so it answered 1, and the price of reaching depth 1 is nothing.
	//
	// Price a binding from the charm still sitting in the input slot instead.

	/** What each successive binding adds to the price. */
	private const val STEP = 8
}
