package io.github.freshglitch.vanguardspirits.block

import io.github.freshglitch.vanguardspirits.block.entity.MuralBlockEntity
import io.github.freshglitch.vanguardspirits.lore.MuralCodex
import io.github.freshglitch.vanguardspirits.lore.MuralLore
import io.github.freshglitch.vanguardspirits.lore.MuralOpen
import io.github.freshglitch.vanguardspirits.registry.ModBlockEntities
import io.github.freshglitch.vanguardspirits.registry.ModSounds
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.phys.BlockHitResult

/**
 * A slab of deepslate with one of the ruin's eight passages cut into its face.
 *
 * Which passage is a **blockstate**, not block entity data. That is the whole
 * reason this block needs no `BlockEntity`, no save data and no sync code: the
 * state travels to clients on its own, and worldgen sets it with a single
 * `placeBlock`. It also means a mural broken and carried home keeps its text
 * for free -- `BlockItem` applies `DataComponents.BLOCK_STATE` on placement, so
 * a `copy_state` loot function is the entire mechanism.
 *
 * Reading is server-authoritative even though the client already holds the
 * state, because the *codex* -- which passages this player has found -- is a
 * data attachment the client has no copy of. One round trip settles both: the
 * server records the read and hands back a snapshot for the screen to page
 * through.
 */
class MuralBlock(properties: Properties) : Block(properties), EntityBlock {

	init {
		registerDefaultState(
			stateDefinition.any()
				.setValue(FACING, net.minecraft.core.Direction.NORTH)
				.setValue(PASSAGE, 0)
				.setValue(GLOW, 0),
		)
	}

	override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
		builder.add(FACING, PASSAGE, GLOW)
	}

	override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
		MuralBlockEntity(pos, state)

	/**
	 * Server side only. The glow is decided from player distance, which only the
	 * server knows, and reaches clients as the blockstate change.
	 *
	 * Note this implements [EntityBlock] directly rather than extending
	 * `BaseEntityBlock`, whose `getRenderShape` defaults to `INVISIBLE` -- taking
	 * that route would have needed the default undone just to keep the block
	 * visible.
	 */
	@Suppress("UNCHECKED_CAST")
	override fun <T : BlockEntity> getTicker(
		level: Level,
		state: BlockState,
		type: BlockEntityType<T>,
	): BlockEntityTicker<T>? {
		if (level.isClientSide) return null

		// `BaseEntityBlock.createTickerHelper` would do this, but it is protected
		// on a class this block does not extend -- see [getTicker]'s note. The
		// guard it provides is the one that matters: the cast is only sound
		// because the type has been checked to be ours first.
		if (type != ModBlockEntities.MURAL) return null
		return BlockEntityTicker<T> { tickLevel, pos, tickState, entity ->
			MuralBlockEntity.serverTick(tickLevel, pos, tickState, entity as MuralBlockEntity)
		}
	}

	/**
	 * Turns the carved face toward whoever placed it.
	 *
	 * Without this the state keeps its default NORTH forever and every mural in
	 * a built wall faces the same way regardless of where the player stood.
	 */
	override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
		defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

	override fun useWithoutItem(
		state: BlockState,
		level: Level,
		pos: BlockPos,
		player: Player,
		hit: BlockHitResult,
	): InteractionResult {
		// The client swings and stops there; everything real happens on the
		// server, which then asks the client to open the screen.
		if (level.isClientSide) return InteractionResult.SUCCESS

		val passage = state.getValue(PASSAGE)
		if (!MuralLore.exists(passage)) return InteractionResult.PASS

		// Record before snapshotting, so the passage in front of the player is
		// already marked read in the codex the screen is handed. Otherwise the
		// one you are looking at is the one page the arrows refuse to return to.
		MuralCodex.markRead(player, passage)

		if (player is ServerPlayer) {
			level.playSound(
				null,
				pos,
				ModSounds.MURAL_READ,
				SoundSource.BLOCKS,
				0.7f,
				0.95f + level.random.nextFloat() * 0.1f,
			)
			ServerPlayNetworking.send(player, MuralOpen(passage, MuralCodex.of(player)))
		}

		return InteractionResult.CONSUME
	}

	companion object {
		val FACING: EnumProperty<net.minecraft.core.Direction> = HorizontalDirectionalBlock.FACING

		/**
		 * Which passage this slab carries.
		 *
		 * Ranged off [MuralLore.COUNT] rather than a literal, so a ninth passage
		 * cannot end up with nowhere to live. Note that widening this changes the
		 * blockstate definition -- existing murals keep their value, but the
		 * models and blockstate JSON have to grow with it, which datagen handles
		 * from the same constant.
		 */
		val PASSAGE: IntegerProperty = IntegerProperty.create("passage", 0, MuralLore.COUNT - 1)

		/**
		 * How awake the carving is, 0 dark to [MuralBlockEntity.MAX_GLOW].
		 *
		 * In the blockstate rather than on the block entity because
		 * `BlockBehaviour.Properties.lightLevel` is handed a `BlockState` and
		 * nothing else -- a glow held anywhere else is invisible to the light
		 * engine. The cost is that it multiplies the blockstate count, which is
		 * why the blockstate JSON is generated rather than written by hand.
		 *
		 * Deliberately not copied by the loot table. A mural carried home starts
		 * dark and wakes to whoever placed it, the same as one found in a ruin.
		 */
		val GLOW: IntegerProperty = IntegerProperty.create("glow", 0, MuralBlockEntity.MAX_GLOW)
	}
}
