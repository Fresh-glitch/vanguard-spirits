package io.github.freshglitch.vanguardspirits.block.entity

import io.github.freshglitch.vanguardspirits.registry.ModBlockEntities
import io.github.freshglitch.vanguardspirits.registry.ModSounds
import net.minecraft.core.BlockPos
import net.minecraft.core.NonNullList
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.ContainerHelper
import net.minecraft.world.entity.ContainerUser
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.DispenserMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.ContainerOpenersCounter
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

/**
 * A nine-slot treasure chest.
 *
 * Extends [RandomizableContainerBlockEntity] rather than a plain container so
 * structures can drop one with a loot table attached and have it roll on first
 * open, which is the whole point of putting these in ruins.
 */
class GoldenChestBlockEntity(pos: BlockPos, state: BlockState) :
	RandomizableContainerBlockEntity(ModBlockEntities.GOLDEN_CHEST, pos, state) {

	private var items: NonNullList<ItemStack> = NonNullList.withSize(SIZE, ItemStack.EMPTY)

	private val openers = object : ContainerOpenersCounter() {
		override fun onOpen(level: Level, pos: BlockPos, state: BlockState) =
			play(level, pos, ModSounds.GOLDEN_CHEST_OPEN)

		override fun onClose(level: Level, pos: BlockPos, state: BlockState) =
			play(level, pos, ModSounds.GOLDEN_CHEST_CLOSE)

		override fun openerCountChanged(
			level: Level,
			pos: BlockPos,
			state: BlockState,
			previous: Int,
			current: Int,
		) = Unit

		/**
		 * Approximate. [DispenserMenu] exposes no accessor for its backing
		 * container, so this cannot verify the menu belongs to *this* chest --
		 * it only confirms the player has some 3x3 container open. The cost is
		 * a mis-timed close sound in the rare case a player closes this chest
		 * while another 3x3 container is also open.
		 */
		override fun isOwnContainer(player: Player): Boolean =
			player.containerMenu is DispenserMenu
	}

	override fun getContainerSize(): Int = SIZE

	override fun getItems(): NonNullList<ItemStack> = items

	override fun setItems(list: NonNullList<ItemStack>) {
		items = list
	}

	override fun getDefaultName(): Component = Component.translatable(NAME_KEY)

	override fun createMenu(id: Int, inventory: Inventory): AbstractContainerMenu =
		DispenserMenu(id, inventory, this)

	override fun startOpen(user: ContainerUser) {
		val level = this.level ?: return
		if (remove) return
		openers.incrementOpeners(user.livingEntity, level, blockPos, blockState, user.containerInteractionRange)
	}

	override fun stopOpen(user: ContainerUser) {
		val level = this.level ?: return
		if (remove) return
		openers.decrementOpeners(user.livingEntity, level, blockPos, blockState)
	}

	fun recheckOpen() {
		val level = this.level ?: return
		if (!remove) openers.recheckOpeners(level, blockPos, blockState)
	}

	override fun loadAdditional(input: ValueInput) {
		super.loadAdditional(input)
		items = NonNullList.withSize(SIZE, ItemStack.EMPTY)
		if (!tryLoadLootTable(input)) ContainerHelper.loadAllItems(input, items)
	}

	override fun saveAdditional(output: ValueOutput) {
		super.saveAdditional(output)
		if (!trySaveLootTable(output)) ContainerHelper.saveAllItems(output, items)
	}

	private fun play(level: Level, pos: BlockPos, sound: SoundEvent) {
		level.playSound(
			null,
			pos.x + 0.5,
			pos.y + 0.5,
			pos.z + 0.5,
			sound,
			SoundSource.BLOCKS,
			0.6f,
			1.0f,
		)
	}

	companion object {
		/** Three by three, as a square. */
		const val SIZE: Int = 9

		const val NAME_KEY: String = "container.vanguard-spirits.golden_chest"
	}
}
