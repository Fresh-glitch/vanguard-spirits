package io.github.freshglitch.vanguardspirits.menu

import io.github.freshglitch.vanguardspirits.block.entity.GoldenChestBlockEntity
import io.github.freshglitch.vanguardspirits.registry.ModMenus
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

/**
 * Nine slots in a square, plus the player's inventory.
 *
 * Functionally this is vanilla's 3x3, but it needs to be its own menu type:
 * screens are registered per menu type, so reusing GENERIC_3x3 would give every
 * dispenser in the game the Reliquary's gilded interface.
 *
 * **Slot positions here must match the panel painted by the GUI generator.**
 * Move one and the artwork no longer lines up with where items actually sit.
 */
class GoldenChestMenu(
	containerId: Int,
	playerInventory: Inventory,
	private val container: Container,
) : AbstractContainerMenu(ModMenus.GOLDEN_CHEST, containerId) {

	/** Client-side constructor: the real container arrives via sync. */
	constructor(containerId: Int, playerInventory: Inventory) :
		this(containerId, playerInventory, SimpleContainer(GoldenChestBlockEntity.SIZE))

	init {
		checkContainerSize(container, GoldenChestBlockEntity.SIZE)
		container.startOpen(playerInventory.player)

		for (row in 0 until 3) {
			for (col in 0 until 3) {
				addSlot(Slot(container, col + row * 3, GRID_X + col * SLOT, GRID_Y + row * SLOT))
			}
		}

		for (row in 0 until 3) {
			for (col in 0 until 9) {
				addSlot(Slot(playerInventory, col + row * 9 + 9, INV_X + col * SLOT, INV_Y + row * SLOT))
			}
		}
		for (col in 0 until 9) {
			addSlot(Slot(playerInventory, col, INV_X + col * SLOT, HOTBAR_Y))
		}
	}

	override fun stillValid(player: Player): Boolean = container.stillValid(player)

	/**
	 * Whether this menu is looking at [other].
	 *
	 * Vanilla's DispenserMenu exposes no accessor for its backing container, so
	 * while the chest borrowed it the open/close counter could only ask "is some
	 * 3x3 open?". Owning the menu means it can ask the exact question.
	 */
	fun isFor(other: Container): Boolean = container === other

	override fun removed(player: Player) {
		super.removed(player)
		container.stopOpen(player)
	}

	/**
	 * Shift-click routing: reliquary to inventory, inventory to reliquary.
	 */
	override fun quickMoveStack(player: Player, index: Int): ItemStack {
		val slot = slots.getOrNull(index) ?: return ItemStack.EMPTY
		if (!slot.hasItem()) return ItemStack.EMPTY

		val moved = slot.item
		val original = moved.copy()

		val chestSlots = GoldenChestBlockEntity.SIZE
		val movedOk = if (index < chestSlots) {
			moveItemStackTo(moved, chestSlots, slots.size, true)
		} else {
			moveItemStackTo(moved, 0, chestSlots, false)
		}
		if (!movedOk) return ItemStack.EMPTY

		if (moved.isEmpty) slot.set(ItemStack.EMPTY) else slot.setChanged()
		return original
	}

	companion object {
		private const val SLOT = 18

		/** Reliquary grid origin, matching the alcove in the painted panel. */
		private const val GRID_X = 62
		private const val GRID_Y = 17

		/** Player inventory origin. The vanilla standard, so the panel reads familiar. */
		private const val INV_X = 8
		private const val INV_Y = 84
		private const val HOTBAR_Y = 142
	}
}
