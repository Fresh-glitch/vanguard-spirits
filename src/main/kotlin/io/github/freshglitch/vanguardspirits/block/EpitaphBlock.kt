package io.github.freshglitch.vanguardspirits.block

import io.github.freshglitch.vanguardspirits.block.entity.EpitaphBlockEntity
import io.github.freshglitch.vanguardspirits.registry.ModSounds
import io.github.freshglitch.vanguardspirits.worldgen.RuinSettled
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * A grave marker with nobody's name on it yet.
 *
 * The last keeper carved eight passages, buried everyone else, and asked whoever
 * came after to leave something behind. Nobody carved theirs. This is the stone
 * they started and did not finish, and the player is the one who finishes it --
 * with their own name, which is the only name left to put there.
 *
 * Engraving it **settles** the ruin: see [RuinSettled]. That is a real trade,
 * not a reward. A settled graveyard stops giving up Remnants, which is the mod's
 * only renewable source of memories, so what a player buys here is somewhere
 * quiet to live at the cost of the thing that made the place worth returning to.
 *
 * Implements [EntityBlock] directly rather than extending `BaseEntityBlock`,
 * which would force `RenderShape.INVISIBLE` and have to be undone. The stone is
 * an ordinary JSON model and has no renderer at all.
 *
 * ## The name is not drawn on the stone
 *
 * It was, briefly. A `BlockEntityRenderer` submitting real text the way
 * `AbstractSignRenderer` does is the right *technique* and the wrong surface
 * here: the shaft's face is six pixels wide, so a player name either renders
 * too small to read or hangs off both edges. A sign is a plank and a half for a
 * reason.
 *
 * So the stone stays blank and the name is reported on the action bar, which is
 * legible, needs no space on the block, and reads the same whether you have just
 * cut it or come back to it later. The block entity still exists purely to
 * remember the name.
 */
class EpitaphBlock(properties: Properties) : Block(properties), EntityBlock {

	init {
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))
	}

	override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
		builder.add(FACING)
	}

	override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
		EpitaphBlockEntity(pos, state)

	/** Faces whoever set it down, so the name reads to the person who cut it. */
	override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
		defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

	/**
	 * The marker's own outline rather than a full cube.
	 *
	 * Two shapes, not four: the stone is symmetric front to back, so north and
	 * south share one and east and west share its transpose.
	 */
	override fun getShape(
		state: BlockState,
		level: BlockGetter,
		pos: BlockPos,
		context: CollisionContext,
	): VoxelShape = when (state.getValue(FACING)) {
		Direction.EAST, Direction.WEST -> SHAPE_EW
		else -> SHAPE_NS
	}

	/**
	 * Cuts the player's name into the stone, and settles the ruin around it.
	 *
	 * Deliberately a second, separate act from placing it. Setting the stone down
	 * is reversible in the sense that nothing has happened yet; this is the point
	 * of no return, and it wants its own deliberate click.
	 */
	override fun useWithoutItem(
		state: BlockState,
		level: Level,
		pos: BlockPos,
		player: Player,
		hit: BlockHitResult,
	): InteractionResult {
		if (level.isClientSide) return InteractionResult.SUCCESS

		val epitaph = level.getBlockEntity(pos) as? EpitaphBlockEntity
			?: return InteractionResult.PASS

		// Already somebody's. Reading a name is not an action, so this answers
		// without consuming anything or pretending work was done.
		epitaph.engraved?.let { name ->
			player.sendOverlayMessage(
				Component.translatable(READS_KEY, name).withStyle(ChatFormatting.GRAY),
			)
			return InteractionResult.CONSUME
		}

		if (!epitaph.engrave(player.name.string)) return InteractionResult.CONSUME

		if (level is ServerLevel) {
			mark(level, pos)

			// The same line reading it gives, because the stone cannot show it.
			// Its face is six pixels wide -- a name drawn there is either
			// illegible or hanging off both edges -- so the action bar is where
			// the engraving is actually legible, and it says the same thing
			// whether you have just cut it or come back to read it years later.
			player.sendOverlayMessage(
				Component.translatable(READS_KEY, player.name.string).withStyle(ChatFormatting.GRAY),
			)

			// A stone planted outside any ruin is just a headstone -- there is
			// nothing to settle and nothing to announce.
			if (RuinSettled.settle(level, pos)) {
				player.sendSystemMessage(
					Component.translatable(SETTLED_KEY).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
				)
			}
		}

		return InteractionResult.CONSUME
	}

	/**
	 * A chisel, and nothing else.
	 *
	 * No echo runes. Every other place they appear is the ruin *giving something
	 * up* -- a grave coming open, a memory coming loose. Cutting a name is the
	 * opposite: it is a person doing quiet work with their hands, and glyphs
	 * boiling off the stone made it read as another piece of ruin magic rather
	 * than as the one moment in the mod where somebody finishes a job.
	 */
	private fun mark(level: ServerLevel, pos: BlockPos) {
		val x = pos.x + 0.5
		val y = pos.y + 0.9
		val z = pos.z + 0.5

		level.playSound(null, x, y, z, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.9f, 0.7f)
		level.playSound(null, x, y, z, ModSounds.MURAL_READ, SoundSource.BLOCKS, 0.7f, 0.8f)
	}

	companion object {
		val FACING: EnumProperty<Direction> = HorizontalDirectionalBlock.FACING

		const val READS_KEY: String = "message.vanguard-spirits.epitaph.reads"
		const val SETTLED_KEY: String = "message.vanguard-spirits.epitaph.settled"

		// The same five boxes as `models/block/unfinished_epitaph.json`, in the
		// same order: plinth, body, arch1, arch2, arch3. Both are transcriptions
		// of `blockbench/unfinished_epitaph.bbmodel`, which is the source. Keep
		// them in step -- a mismatch shows up as a marker you can walk through,
		// or one you bump into a pixel early.
		//
		// Widths bottom-up are 10, 8, 7, 6, 4: an arch tapering over three steps.
		// Two earlier versions did not read as one. The first capped a 6 wide
		// shaft with a 4 wide block, which is a squared shoulder. The second
		// copied the item sprite's rows exactly -- 12, 10, 8, 6, 4 -- and looked
		// no different in game, because that sprite spends only two of its
		// fourteen rows on the dome, and two sixteenths of a block is a step
		// however many steps are cut into it. Matching the numbers was not the
		// same as matching the shape; it took building it in Blockbench and
		// looking at it to get an arch that reads as one.
		private val SHAPE_NS: VoxelShape = Shapes.or(
			Block.box(3.0, 0.0, 6.0, 13.0, 2.0, 10.0),
			Block.box(4.0, 2.0, 7.0, 12.0, 12.0, 9.0),
			Block.box(4.5, 12.0, 7.0, 11.5, 13.0, 9.0),
			Block.box(5.0, 13.0, 7.0, 11.0, 14.0, 9.0),
			Block.box(6.0, 14.0, 7.0, 10.0, 15.0, 9.0),
		)

		// The same solid turned a quarter: x and z swap about the block centre.
		private val SHAPE_EW: VoxelShape = Shapes.or(
			Block.box(6.0, 0.0, 3.0, 10.0, 2.0, 13.0),
			Block.box(7.0, 2.0, 4.0, 9.0, 12.0, 12.0),
			Block.box(7.0, 12.0, 4.5, 9.0, 13.0, 11.5),
			Block.box(7.0, 13.0, 5.0, 9.0, 14.0, 11.0),
			Block.box(7.0, 14.0, 6.0, 9.0, 15.0, 10.0),
		)
	}
}
