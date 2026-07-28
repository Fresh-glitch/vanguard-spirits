package io.github.freshglitch.vanguardspirits.block

import com.mojang.serialization.MapCodec
import io.github.freshglitch.vanguardspirits.block.entity.GoldenChestBlockEntity
import io.github.freshglitch.vanguardspirits.registry.ModBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.phys.BlockHitResult

/**
 * A Terraria-style treasure chest: nine slots, gilded, and worth breaking into.
 *
 * Deliberately not a vanilla chest subclass -- it has no double-chest pairing,
 * no cat-sitting rule, and a fixed 3x3 inventory, so inheriting that behaviour
 * would mean disabling most of it.
 */
class GoldenChestBlock(props: Properties) : BaseEntityBlock(props) {

	init {
		registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH))
	}

	override fun codec(): MapCodec<GoldenChestBlock> = CODEC

	override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
		GoldenChestBlockEntity(pos, state)

	override fun createBlockStateDefinition(builder: StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState>) {
		builder.add(FACING)
	}

	/**
	 * The chest is drawn entirely by its BlockEntityRenderer, which is the only
	 * way to get an animated lid -- a static block model cannot move. Leaving
	 * this as MODEL would draw the JSON model straight through the rendered one.
	 */
	override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE

	/** Client-side only: the lid eases toward its target every tick. */
	override fun <T : BlockEntity> getTicker(
		level: Level,
		state: BlockState,
		type: BlockEntityType<T>,
	): BlockEntityTicker<T>? =
		if (level.isClientSide) {
			createTickerHelper(type, ModBlockEntities.GOLDEN_CHEST, GoldenChestBlockEntity::clientTick)
		} else {
			null
		}

	/**
	 * Block events are addressed to the block, not the block entity, so they
	 * have to be handed down explicitly.
	 */
	override fun triggerEvent(
		state: BlockState,
		level: Level,
		pos: BlockPos,
		id: Int,
		param: Int,
	): Boolean {
		super.triggerEvent(state, level, pos, id, param)
		return level.getBlockEntity(pos)?.triggerEvent(id, param) ?: false
	}

	override fun useWithoutItem(
		state: BlockState,
		level: Level,
		pos: BlockPos,
		player: Player,
		hit: BlockHitResult,
	): InteractionResult {
		if (level.isClientSide) return InteractionResult.SUCCESS

		val menu = state.getMenuProvider(level, pos) ?: return InteractionResult.PASS
		player.openMenu(menu)
		return InteractionResult.CONSUME
	}

	companion object {
		val FACING = HorizontalDirectionalBlock.FACING

		val CODEC: MapCodec<GoldenChestBlock> = simpleCodec(::GoldenChestBlock)
	}
}
