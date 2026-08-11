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
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
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
		registerDefaultState(
			stateDefinition.any()
				.setValue(FACING, net.minecraft.core.Direction.NORTH)
				// Sealed by default, because worldgen places these with
				// `defaultBlockState()` and every generated Reliquary is guarded.
				// A player placing one gets the opposite -- see getStateForPlacement.
				.setValue(SEALED, true),
		)
	}

	override fun codec(): MapCodec<GoldenChestBlock> = CODEC

	override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
		GoldenChestBlockEntity(pos, state)

	override fun createBlockStateDefinition(builder: StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState>) {
		builder.add(FACING, SEALED)
	}

	/**
	 * Faces the chest at whoever placed it.
	 *
	 * Without this the state keeps its default NORTH forever, so the lid always
	 * hinges on the same world edge regardless of where the player stood.
	 */
	override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
		// Asked here rather than left to the ticker, which cannot answer it: that
		// runs only while [SEALED] is already true, so it lifts a seal and can
		// never set one. A chest placed inside a guarded ruin was therefore
		// unsealed for good -- minable, but refusing to open, which is a split
		// that reads as broken from either side.
		//
		// Client-side this is a prediction and comes out unsealed, since the seal
		// cannot be resolved without the structure manager. The server's answer
		// arrives with the block and wins.
		val level = context.level
		val guarded = level is ServerLevel && !RuinSeal.isCleared(level, context.clickedPos)

		return defaultBlockState()
			.setValue(FACING, context.horizontalDirection.opposite)
			.setValue(SEALED, guarded)
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

	/**
	 * The ward holds the stone shut. A sealed Reliquary cannot be mined at all.
	 *
	 * Zero progress is how bedrock is unbreakable, and both sides agree on it,
	 * which is the whole reason [SEALED] is a blockstate rather than a question
	 * asked of [RuinSeal]. That seal is a chunk attachment, and resolving which
	 * ruin a position belongs to needs the structure manager -- server only. The
	 * client could not know, so it animated the crack and the block snapped back,
	 * which reads as lag rather than as a ward. A blockstate is synced for free.
	 *
	 * Creative goes through, as it does everywhere else in this block: a builder
	 * placing a Reliquary in their own world has no Sentinel to fight.
	 */
	override fun getDestroyProgress(
		state: BlockState,
		player: Player,
		level: BlockGetter,
		pos: BlockPos,
	): Float {
		if (state.getValue(SEALED) && !player.isCreative) return 0.0f

		return super.getDestroyProgress(state, player, level, pos)
	}

	/**
	 * Answers a pickaxe the same way the block answers a hand.
	 *
	 * Without this the refusal is silent -- the block simply never breaks -- and
	 * a player would read that as the game being broken rather than the ruin
	 * being guarded. The runes and the shove already exist to say *why*, and
	 * [GoldenChestBlockEntity.refuse] rate-limits itself, so holding the button
	 * down does not repeat it.
	 */
	override fun attack(state: BlockState, level: Level, pos: BlockPos, player: Player) {
		if (level is ServerLevel && !player.isCreative && !RuinSeal.isCleared(level, pos)) {
			(level.getBlockEntity(pos) as? GoldenChestBlockEntity)?.refuse(level, player)
			return
		}
		super.attack(state, level, pos, player)
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

		/**
		 * Whether the ruin's guardian still stands, as the *client* sees it.
		 *
		 * A mirror of [RuinSeal], not a second source of truth: the server keeps
		 * deciding through the attachment, and this exists so the client can
		 * refuse a pickaxe without being told. [GoldenChestBlockEntity] keeps the
		 * two in step.
		 */
		val SEALED: BooleanProperty = BooleanProperty.create("sealed")

		val CODEC: MapCodec<GoldenChestBlock> = simpleCodec(::GoldenChestBlock)
	}
}
