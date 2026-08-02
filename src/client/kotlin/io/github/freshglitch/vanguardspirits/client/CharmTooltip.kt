package io.github.freshglitch.vanguardspirits.client

import io.github.freshglitch.vanguardspirits.charm.Attunement
import io.github.freshglitch.vanguardspirits.charm.CharmScan
import io.github.freshglitch.vanguardspirits.item.CharmItem
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

/**
 * Adds the live attunement state to charm tooltips.
 *
 * Whether a charm is attuned depends on the holder, not the stack, so it cannot
 * be a baked LORE component. [CharmScan] is shared with the server tick so the
 * two can never disagree.
 */
object CharmTooltip {
	private const val ATTUNED_KEY = "tooltip.vanguard-spirits.charm.attuned"
	private const val DORMANT_KEY = "tooltip.vanguard-spirits.charm.dormant"
	private const val GENERIC_KEY = "tooltip.vanguard-spirits.charm.generic"
	const val HUSHED_KEY: String = "tooltip.vanguard-spirits.charm.hushed"
	const val COST_KEY: String = "tooltip.vanguard-spirits.charm.cost"

	fun register() {
		ItemTooltipCallback.EVENT.register { stack, _, _, lines ->
			val charm = stack.item as? CharmItem ?: return@register
			val player = Minecraft.getInstance().player ?: return@register

			// Only the expensive ones say so. Printing "costs 1" on every charm
			// would be noise on the case that is already the assumption.
			if (charm.cost > 1) {
				lines.add(
					Component.translatable(COST_KEY, charm.cost).withStyle(ChatFormatting.DARK_PURPLE),
				)
			}

			lines.add(stateLine(player, stack))
		}
	}

	private fun stateLine(player: Player, stack: ItemStack): Component {
		// Switched off by hand, which is worth saying wherever the stack is --
		// including in a chest or the creative menu, where there is no slot to
		// find and the generic line would otherwise hide the setting.
		if (CharmItem.isHushed(stack)) {
			return Component.translatable(HUSHED_KEY).withStyle(ChatFormatting.GRAY)
		}

		val slot = slotOf(player, stack)
			?: return Component.translatable(GENERIC_KEY).withStyle(ChatFormatting.DARK_GRAY)

		return if (slot in CharmScan.activeSlots(player)) {
			Component.translatable(ATTUNED_KEY).withStyle(ChatFormatting.AQUA)
		} else {
			Component.translatable(DORMANT_KEY, Attunement.of(player))
				.withStyle(ChatFormatting.DARK_GRAY)
		}
	}

	/**
	 * Locates the stack in the player's inventory by identity.
	 *
	 * Returns null for stacks that are not actually carried -- creative menu
	 * entries, recipe previews, other containers -- which is what makes the
	 * generic line the safe fallback rather than a wrong "Dormant".
	 */
	private fun slotOf(player: Player, stack: ItemStack): Int? {
		val inventory = player.inventory
		for (slot in 0 until inventory.containerSize) {
			if (inventory.getItem(slot) === stack) return slot
		}
		return null
	}
}
