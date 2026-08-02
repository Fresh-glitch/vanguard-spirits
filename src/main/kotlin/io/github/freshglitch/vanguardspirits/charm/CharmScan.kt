package io.github.freshglitch.vanguardspirits.charm

import io.github.freshglitch.vanguardspirits.item.CharmItem
import net.minecraft.world.entity.player.Player

/**
 * Decides which carried charms are currently attuned.
 *
 * Lives in the common source set on purpose: the server applies the auras and
 * the client colours the tooltips, and the two must never disagree about which
 * charm is live.
 */
object CharmScan {

	/**
	 * Inventory slot indices holding an attuned charm, in priority order.
	 *
	 * Slot order is the tie-breaker, and vanilla numbers the hotbar first, so
	 * players reorder priority simply by moving charms into the hotbar.
	 *
	 * Attunement is spent as a budget rather than counted as a number of charms,
	 * because not every charm costs the same. A charm that will not fit in what
	 * is left is *skipped* rather than ending the walk, so a cheap charm further
	 * down still gets its turn -- otherwise carrying one expensive charm high in
	 * the inventory would silently strand everything below it.
	 */
	fun activeSlots(player: Player): Set<Int> {
		var budget = Attunement.of(player)
		if (budget <= 0) return emptySet()

		val inventory = player.inventory
		val active = LinkedHashSet<Int>()
		for (slot in 0 until inventory.containerSize) {
			if (budget <= 0) break

			val stack = inventory.getItem(slot)
			val charm = stack.item as? CharmItem ?: continue

			// A charm switched off does not merely go dormant, it stops queueing:
			// it gives up its place so a charm further down the inventory takes
			// the attunement instead. Otherwise silencing one would waste the
			// slot rather than free it.
			if (CharmItem.isHushed(stack)) continue

			if (charm.cost > budget) continue

			active += slot
			budget -= charm.cost
		}
		return active
	}
}
