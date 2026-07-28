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
			if (inventory.getItem(slot).item is CharmItem) active += slot
		}
		return active
	}
}
