package io.github.freshglitch.vanguardspirits.menu

import io.github.freshglitch.vanguardspirits.charm.Binding
import io.github.freshglitch.vanguardspirits.item.CharmItem
import io.github.freshglitch.vanguardspirits.registry.ModBlocks
import io.github.freshglitch.vanguardspirits.registry.ModItems
import io.github.freshglitch.vanguardspirits.registry.ModMenus
import io.github.freshglitch.vanguardspirits.registry.ModSounds
import io.github.freshglitch.vanguardspirits.registry.ModTriggers
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.ItemCombinerMenu
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

/**
 * A charm, a pile of Fractured Memories, and the charm again one depth deeper.
 *
 * Built on vanilla's [ItemCombinerMenu] rather than from scratch, which is what
 * the anvil, the grindstone and the smithing table all are. That base owns the
 * input container, the result container, shift-click routing, dropping the
 * inputs when the screen closes, and `stillValid` against the block -- so the
 * altar needs no block entity at all. Nothing is stored between visits, exactly
 * like every other vanilla workstation.
 *
 * **The slot positions must match the painted panel.** The two inputs sit left
 * of centre rather than at the anvil's own spacing, to leave room between the
 * payment slot and the result for the binding chain -- which is the panel's
 * whole readout. `tools/make_binding_altar.py` asserts these three coordinates
 * against this file, so moving one here fails the art generator rather than
 * quietly sliding the slots off their wells.
 */
class BindingAltarMenu(
	containerId: Int,
	playerInventory: Inventory,
	access: ContainerLevelAccess,
) : ItemCombinerMenu(ModMenus.BINDING_ALTAR, containerId, playerInventory, access, slotLayout()) {

	/** Client-side constructor: there is no level to reach from here. */
	constructor(containerId: Int, playerInventory: Inventory) :
		this(containerId, playerInventory, ContainerLevelAccess.NULL)

	override fun isValidBlock(state: BlockState): Boolean = state.`is`(ModBlocks.BINDING_ALTAR)

	/**
	 * Recomputes the result slot. Called by the base class whenever an input moves.
	 */
	override fun createResult() {
		val deeper = Binding.result(inputSlots.getItem(CHARM_SLOT), inputSlots.getItem(PAYMENT_SLOT))

		resultSlots.setItem(0, deeper ?: ItemStack.EMPTY)
		broadcastChanges()
	}

	/**
	 * Charges for the binding once the player actually takes the charm.
	 *
	 * The price is read back off the *result*, not off what happens to be in the
	 * payment slot now. By the time this runs the stack could have been changed
	 * by anything that can reach a slot -- a hopper, another player in the same
	 * screen -- and charging for the binding that was previewed is the only
	 * reading that cannot take more than the panel quoted.
	 */
	override fun onTake(player: Player, stack: ItemStack) {
		inputSlots.removeItem(CHARM_SLOT, 1)
		inputSlots.removeItem(PAYMENT_SLOT, Binding.priceTaken(stack))

		// Fired from the taking rather than from the result appearing: a charm
		// previewed and left on the altar has not been bound.
		if (player is ServerPlayer) ModTriggers.BINDING.fire(player, CharmItem.depthOf(stack))

		access.execute { level, pos ->
			level.playSound(
				null,
				pos,
				ModSounds.BINDING_ALTAR_BIND,
				SoundSource.BLOCKS,
				0.9f,
				// Each binding rings a step brighter, so the deepest one is
				// audibly the biggest thing that happens at this block.
				0.8f + 0.2f * CharmItem.depthOf(stack),
			)
		}
	}

	companion object {
		const val CHARM_SLOT: Int = 0
		const val PAYMENT_SLOT: Int = 1

		private fun slotLayout(): ItemCombinerMenuSlotDefinition =
			ItemCombinerMenuSlotDefinition.create()
				// Only a charm that has somewhere left to go. A Returned, or one
				// already at its deepest, is refused by the slot rather than
				// accepted and then silently doing nothing.
				.withSlot(CHARM_SLOT, 22, 47) { stack ->
					val charm = stack.item as? CharmItem
					charm != null && CharmItem.depthOf(stack) < charm.maxDepth
				}
				.withSlot(PAYMENT_SLOT, 52, 47) { stack -> stack.`is`(ModItems.FRACTURED_MEMORY) }
				.withResultSlot(2, 134, 47)
				.build()
	}
}
