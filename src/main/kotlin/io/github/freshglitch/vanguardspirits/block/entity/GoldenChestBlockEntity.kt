package io.github.freshglitch.vanguardspirits.block.entity

import io.github.freshglitch.vanguardspirits.menu.GoldenChestMenu
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
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.ContainerOpenersCounter
import net.minecraft.world.level.block.entity.LidBlockEntity
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
	RandomizableContainerBlockEntity(ModBlockEntities.GOLDEN_CHEST, pos, state), LidBlockEntity {

	private var items: NonNullList<ItemStack> = NonNullList.withSize(SIZE, ItemStack.EMPTY)

	/**
	 * Drives the lid angle on the client. It only ever eases toward a target,
	 * so the server just has to say open or shut.
	 */
	private val lid = ReliquaryLid()

	private val openers = object : ContainerOpenersCounter() {
		override fun onOpen(level: Level, pos: BlockPos, state: BlockState) =
			play(level, pos, ModSounds.GOLDEN_CHEST_OPEN)

		override fun onClose(level: Level, pos: BlockPos, state: BlockState) =
			play(level, pos, ModSounds.GOLDEN_CHEST_CLOSE)

		/**
		 * Announces the lid state to everyone tracking the chunk.
		 *
		 * The opener count only exists on the server, so the animation cannot be
		 * derived client-side -- it has to be pushed as a block event.
		 */
		override fun openerCountChanged(
			level: Level,
			pos: BlockPos,
			state: BlockState,
			previous: Int,
			current: Int,
		) {
			level.blockEvent(pos, state.block, EVENT_LID, if (current > 0) 1 else 0)
		}

		/** Exact: the menu is ours, so it can be asked which chest it is showing. */
		override fun isOwnContainer(player: Player): Boolean {
			val menu = player.containerMenu
			return menu is GoldenChestMenu && menu.isFor(this@GoldenChestBlockEntity)
		}
	}

	override fun getContainerSize(): Int = SIZE

	override fun getItems(): NonNullList<ItemStack> = items

	override fun setItems(list: NonNullList<ItemStack>) {
		items = list
	}

	override fun getDefaultName(): Component = Component.translatable(NAME_KEY)

	override fun createMenu(id: Int, inventory: Inventory): AbstractContainerMenu =
		GoldenChestMenu(id, inventory, this)

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

	override fun getOpenNess(partialTick: Float): Float = lid.openness(partialTick)

	/** Receives the block event fired by [openerCountChanged] on the server. */
	override fun triggerEvent(id: Int, value: Int): Boolean {
		if (id == EVENT_LID) {
			lid.shouldBeOpen(value > 0)
			return true
		}
		return super.triggerEvent(id, value)
	}

	companion object {
		/** Three by three, as a square. */
		const val SIZE: Int = 9

		const val NAME_KEY: String = "container.vanguard-spirits.golden_chest"

		/** Block-event id carrying the lid's open/shut state to clients. */
		const val EVENT_LID: Int = 1

		/** Client-only: eases the lid toward whatever the last block event said. */
		fun clientTick(
			level: Level,
			pos: BlockPos,
			state: BlockState,
			entity: GoldenChestBlockEntity,
		) {
			entity.lid.tick()
		}
	}
}
