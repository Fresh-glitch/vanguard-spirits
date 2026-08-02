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
	 */
	fun activeSlots(player: Player): Set<Int> {
		val cap = Attunement.of(player)
		if (cap <= 0) return emptySet()

		val inventory = player.inventory
		val active = LinkedHashSet<Int>(cap)
		for (slot in 0 until inventory.containerSize) {
			if (active.size >= cap) break

			val stack = inventory.getItem(slot)
			if (stack.item !is CharmItem) continue

			// A charm switched off does not merely go dormant, it stops queueing:
			// it gives up its place so a charm further down the inventory takes
			// the attunement instead. Otherwise silencing one would waste the
			// slot rather than free it.
			if (CharmItem.isHushed(stack)) continue

			active += slot
		}
		return active
	}
}
