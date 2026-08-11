package io.github.freshglitch.vanguardspirits.block.entity

import io.github.freshglitch.vanguardspirits.menu.BindingAltarMenu
import io.github.freshglitch.vanguardspirits.registry.ModBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.Container
import net.minecraft.world.ContainerHelper
import net.minecraft.world.Containers
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

/**
 * What is lying on the altar: a charm, and the memories waiting to be spent on it.
 *
 * The altar spent two versions without one of these, on the reasoning that
 * [net.minecraft.world.inventory.ItemCombinerMenu] holds its own inputs and gives
 * them back when the screen closes, exactly as an anvil does. That is fine for a
 * workstation nobody looks at, and wrong for this one: a charm set down here is
 * meant to *stay* set down, and to be visible on the stone from across the room.
 * Both of those need somewhere for the stacks to live between visits.
 *
 * ## One owner, and a picture of it
 *
 * While a screen is open the real stacks live in the menu's container, not here.
 * That is not a detail: hand a *copy* to every menu that asks and two players
 * opening the same altar can each carry off the same charm. [lendTo] therefore
 * answers only the first caller, and [onLoan] refuses everyone after it.
 *
 * But an altar that goes bare the moment you look inside it is no good either --
 * the block is meant to be read from across the room, including while somebody
 * is standing at it. So [items] keeps serving as the **mirror**: the menu writes
 * through on every change via [mirror], and the renderer draws that.
 *
 * The distinction is load-bearing, and everything that could hand an item to a
 * player has to respect it. A mirror is a picture, never a source -- which is
 * why [preRemoveSideEffects] drops nothing while the loan is out. Breaking the
 * altar under an open screen is already handled, by the menu giving the stacks
 * back to whoever held them.
 */
class BindingAltarBlockEntity(pos: BlockPos, state: BlockState) :
	BlockEntity(ModBlockEntities.BINDING_ALTAR, pos, state) {

	private var items: NonNullList<ItemStack> = NonNullList.withSize(SIZE, ItemStack.EMPTY)

	/**
	 * Whether an open menu is holding the real stacks, leaving [items] a mirror.
	 *
	 * Not saved. A block entity read back off the disk is by definition not being
	 * looked at, and a stale `true` would be an altar that could never be emptied
	 * again -- it would refuse to lend and refuse to drop.
	 */
	private var onLoan: Boolean = false

	/** The charm resting in the ring, or empty. Read by the renderer. */
	val charm: ItemStack get() = items[BindingAltarMenu.CHARM_SLOT]

	/** The Fractured Memories circling it, or empty. Read by the renderer. */
	val payment: ItemStack get() = items[BindingAltarMenu.PAYMENT_SLOT]

	/**
	 * Hands the altar's contents to a freshly opened menu, if they are going.
	 *
	 * Returns whether this menu got them, which is how the caller learns it is
	 * the one owner -- and so the only one allowed to mirror or to give them
	 * back. A second screen opened on the same altar is answered `false` and
	 * works an empty pair of slots.
	 *
	 * Copies, so the menu's stacks and the mirror are separate objects from here
	 * on; slot for slot, because [BindingAltarMenu] numbers its inputs the same
	 * way this list does.
	 */
	fun lendTo(container: Container): Boolean {
		if (onLoan) return false

		onLoan = true
		for (slot in items.indices) container.setItem(slot, items[slot].copy())
		published()
		return true
	}

	/**
	 * Repaints the mirror from the menu that borrowed the stacks.
	 *
	 * Called on every change to the inputs, which is what makes a charm appear on
	 * the stone as it is laid in the slot rather than when the screen closes.
	 */
	fun mirror(container: Container) {
		if (!onLoan) return

		for (slot in items.indices) items[slot] = container.getItem(slot).copy()
		published()
	}

	/** And takes the real ones back when that menu closes. */
	fun reclaim(container: Container) {
		if (!onLoan) return

		val taken = NonNullList.withSize(SIZE, ItemStack.EMPTY)
		for (slot in taken.indices) taken[slot] = container.getItem(slot)

		// Ends the loan *before* the container is emptied. Emptying it is a slot
		// change like any other, so it runs the menu's `createResult` and comes
		// straight back here through [mirror] -- which would paint the mirror
		// with the empties and lose everything just taken.
		onLoan = false
		for (slot in taken.indices) container.setItem(slot, ItemStack.EMPTY)

		items = taken
		published()
	}

	/**
	 * Spills the altar when the block goes, whatever took it.
	 *
	 * This hook rather than the block's `affectNeighborsAfterRemoval`, because
	 * the contents belong to the block entity and this is where vanilla drops
	 * them from -- the base implementation is three lines that check for a
	 * [Container] and call the same method. Overriding it here instead of
	 * implementing [Container] gets that behaviour without also making the altar
	 * something a hopper can load and empty, which nobody asked for and which
	 * would let a comparator-and-hopper rig take the charm back out.
	 *
	 * Nothing is dropped while the loan is out. [items] is only a picture of what
	 * the open menu is holding, and dropping a picture is how you print items:
	 * the menu hands the real stacks back to its player a moment later, when
	 * `stillValid` fails against the block that has just gone.
	 */
	override fun preRemoveSideEffects(pos: BlockPos, state: BlockState) {
		if (onLoan) return
		level?.let { Containers.dropContents(it, pos, items) }
	}

	override fun loadAdditional(input: ValueInput) {
		super.loadAdditional(input)

		// Reallocated rather than cleared in place, so a packet that carries no
		// item list -- which is what an emptied altar sends -- lands as empty
		// instead of leaving the last thing seen on the stone.
		items = NonNullList.withSize(SIZE, ItemStack.EMPTY)
		ContainerHelper.loadAllItems(input, items)
	}

	/**
	 * Writes the mirror when a loan is out, which is the safe way round.
	 *
	 * An orderly shutdown closes every open screen first, so the usual case saves
	 * the real stacks. Only a crash saves a mirror -- and then the menu's copy
	 * died with the process and was never anywhere else, so what lands on the
	 * disk restores the altar rather than printing a second set.
	 */
	override fun saveAdditional(output: ValueOutput) {
		super.saveAdditional(output)
		ContainerHelper.saveAllItems(output, items)
	}

	/**
	 * The whole of the altar's state is what is lying on it, and the renderer is
	 * the only client that wants it -- so the update tag is simply the save tag.
	 */
	override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag =
		saveCustomOnly(registries)

	override fun getUpdatePacket(): Packet<ClientGamePacketListener>? =
		ClientboundBlockEntityDataPacket.create(this)

	/**
	 * Marks the change for saving and tells everyone watching.
	 *
	 * [setChanged] alone only reaches the disk. Without the block update the
	 * altar keeps rendering whatever the client last heard about, which looks
	 * exactly like the charm failing to be picked up.
	 */
	private fun published() {
		setChanged()
		level?.sendBlockUpdated(blockPos, blockState, blockState, Block.UPDATE_ALL)
	}

	companion object {
		/** The charm and what it is being paid for with. The same two the menu shows. */
		const val SIZE: Int = 2
	}
}
