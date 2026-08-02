package io.github.freshglitch.vanguardspirits.block

import com.mojang.serialization.MapCodec
import io.github.freshglitch.vanguardspirits.block.entity.GoldenChestBlockEntity
import io.github.freshglitch.vanguardspirits.registry.ModBlockEntities
import io.github.freshglitch.vanguardspirits.worldgen.RuinSeal
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.context.BlockPlaceContext
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
	 * Faces the chest at whoever placed it.
	 *
	 * Without this the state keeps its default NORTH forever, so the lid always
	 * hinges on the same world edge regardless of where the player stood.
	 */
	override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
		defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

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
			// Idle almost always: it does nothing until a refusal is playing out,
			// and there is one Reliquary per ruin.
			createTickerHelper(type, ModBlockEntities.GOLDEN_CHEST, GoldenChestBlockEntity::serverTick)
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

		// The ruin's own guardian is the price of admission. Anyone who dug past
		// it is turned away here rather than at the loot, so the refusal happens
		// where they can see the reason for it.
		//
		// Creative walks through: a builder placing one of these in their own
		// world has no Sentinel to fight and no business being locked out.
		if (level is ServerLevel && !player.isCreative && !RuinSeal.isCleared(level, pos)) {
			(level.getBlockEntity(pos) as? GoldenChestBlockEntity)?.refuse(level, player)
			return InteractionResult.CONSUME
		}

		val menu = state.getMenuProvider(level, pos) ?: return InteractionResult.PASS
		player.openMenu(menu)
		return InteractionResult.CONSUME
	}

	companion object {
		val FACING = HorizontalDirectionalBlock.FACING

		val CODEC: MapCodec<GoldenChestBlock> = simpleCodec(::GoldenChestBlock)
	}
}
