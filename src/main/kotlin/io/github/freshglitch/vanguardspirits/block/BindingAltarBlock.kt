package io.github.freshglitch.vanguardspirits.block

import io.github.freshglitch.vanguardspirits.block.entity.BindingAltarBlockEntity
import io.github.freshglitch.vanguardspirits.menu.BindingAltarMenu
import io.github.freshglitch.vanguardspirits.registry.ModSounds
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionResult
import net.minecraft.world.MenuProvider
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerLevelAccess
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
import net.minecraft.world.phys.shapes.VoxelShape

/**
 * Where a charm is bound deeper. The fourth passage calls it a binding and this
 * is the block that makes it one.
 *
 * The menu is built on [net.minecraft.world.inventory.ItemCombinerMenu], which
 * keeps its inputs in a container of its own exactly as an anvil does -- but
 * unlike an anvil the altar does not hand them back and forget them. What is set
 * down here stays here, in [BindingAltarBlockEntity], and is drawn on the stone.
 *
 * [EntityBlock] directly rather than `BaseEntityBlock`, so the render shape stays
 * MODEL: the altar's eleven cuboids are still drawn from JSON and the renderer
 * only adds what is lying on top of them.
 */
class BindingAltarBlock(properties: Properties) : Block(properties), EntityBlock {

	init {
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))
	}

	override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity =
		BindingAltarBlockEntity(pos, state)

	override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
		builder.add(FACING)
	}

	/**
	 * Faces whoever placed it.
	 *
	 * Declaring [FACING] does nothing on its own -- without this the state keeps
	 * its default forever and every altar in the world points north.
	 */
	override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
		defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

	override fun getShape(
		state: BlockState,
		level: BlockGetter,
		pos: BlockPos,
		context: CollisionContext,
	): VoxelShape = SHAPE

	override fun useWithoutItem(
		state: BlockState,
		level: Level,
		pos: BlockPos,
		player: Player,
		hit: BlockHitResult,
	): InteractionResult {
		if (level.isClientSide) return InteractionResult.SUCCESS

		// Before the menu opens, not after. The screen does not pause the game --
		// it is a container -- but playing the sound first is what makes the
		// altar feel like it answered the hand rather than the interface.
		level.playSound(
			null,
			pos,
			ModSounds.BINDING_ALTAR_OPEN,
			SoundSource.BLOCKS,
			0.7f,
			0.95f + level.random.nextFloat() * 0.1f,
		)

		player.openMenu(menuAt(level, pos))
		return InteractionResult.CONSUME
	}

	private fun menuAt(level: Level, pos: BlockPos): MenuProvider =
		SimpleMenuProvider(
			{ containerId, inventory, _ ->
				BindingAltarMenu(containerId, inventory, ContainerLevelAccess.create(level, pos))
			},
			Component.translatable(TITLE_KEY),
		)

	companion object {
		const val TITLE_KEY: String = "container.vanguard-spirits.binding_altar"

		val FACING: EnumProperty<net.minecraft.core.Direction> = HorizontalDirectionalBlock.FACING

		/**
		 * The solid mass of the altar: plinth, waist, table and the rim on top.
		 *
		 * Not a full cube. The altar is something you stand *at*, and a block
		 * filling its own space would read as masonry to walk over rather than a
		 * surface to put something down on.
		 *
		 * Thirteen high rather than the model's fifteen, because the last two are
		 * the corner finials and they are spikes at the very edges: including them
		 * would mean either a box covering air across the whole top, or a union of
		 * five shapes to catch three decorations. Vanilla leaves the same kind of
		 * ornament out of its shapes -- a lectern's book is not collidable either.
		 */
		private val SHAPE: VoxelShape = box(1.0, 0.0, 1.0, 15.0, 13.0, 15.0)
	}
}
