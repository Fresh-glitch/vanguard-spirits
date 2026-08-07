package io.github.freshglitch.vanguardspirits.block.entity

import io.github.freshglitch.vanguardspirits.block.MuralBlock
import io.github.freshglitch.vanguardspirits.registry.ModBlockEntities
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Wakes a mural's carving as somebody comes near it, and lets it go dark again
 * when they leave.
 *
 * Holds no data of its own. Everything it decides ends up in the blockstate,
 * because that is the only place a light level can live: `lightLevel` is a
 * function of `BlockState` alone, so a glow kept on the block entity would be
 * invisible to the light engine. This exists purely to get a tick.
 *
 * A tick is genuinely needed. Nothing in the game volunteers "a player moved
 * near this block" -- there is no event, and a block cannot ask. Scheduled
 * ticks were the alternative and are worse: something has to seed the first
 * one, and worldgen writes murals with `StructurePiece.placeBlock`, which is a
 * flag-2 write that skips the hooks a scheduled tick would have to be started
 * from. A block entity ticks from the moment its chunk loads, whoever placed it.
 *
 * Cost is small and bounded: eight murals per ruin, waking on a [PERIOD] tick
 * cycle, doing one distance test each.
 */
class MuralBlockEntity(pos: BlockPos, state: BlockState) :
	BlockEntity(ModBlockEntities.MURAL, pos, state) {

	companion object {
		/**
		 * How bright a fully woken mural is.
		 *
		 * Four steps, so the wake reads as a ramp rather than a switch, and
		 * [LIGHT_PER_STEP] apart so the top of the range is 12 -- bright enough
		 * to light a crypt wall, short of a torch, and well short of anything a
		 * player would use this as a light source for.
		 */
		const val MAX_GLOW: Int = 3
		const val LIGHT_PER_STEP: Int = 4

		/** How far off a mural notices somebody. */
		private const val RANGE = 7.0

		/**
		 * Ticks between checks.
		 *
		 * With [MAX_GLOW] steps this makes a full wake a little under a second,
		 * which is the point -- instant is a light switch, and slower than this
		 * means walking past a mural before it has finished coming up.
		 */
		private const val PERIOD = 5L

		/**
		 * Server side only: the client is told by the blockstate change.
		 *
		 * Steps one level at a time toward the target rather than jumping to it,
		 * so the carving fades up and down instead of snapping between distance
		 * bands as the player walks.
		 */
		fun serverTick(level: Level, pos: BlockPos, state: BlockState, be: MuralBlockEntity) {
			// Staggered by position, so eight murals in one ruin do not all
			// recalculate on the same tick.
			if ((level.gameTime + pos.asLong()) % PERIOD != 0L) return

			val current = state.getValue(MuralBlock.GLOW)
			val target = target(level, pos)
			if (current == target) return

			level.setBlock(
				pos,
				state.setValue(MuralBlock.GLOW, if (target > current) current + 1 else current - 1),
				Block.UPDATE_ALL,
			)
		}

		private fun target(level: Level, pos: BlockPos): Int {
			val x = pos.x + 0.5
			val y = pos.y + 0.5
			val z = pos.z + 0.5

			// The predicate overload with a null predicate, deliberately: the
			// boolean overload excludes creative and spectator players, and a
			// mural that stays dark for a builder standing in front of it looks
			// broken rather than principled.
			val nearest = level.getNearestPlayer(x, y, z, RANGE, null) ?: return 0

			val distance = sqrt(nearest.distanceToSqr(x, y, z))
			val lit = ceil((1.0 - distance / RANGE) * MAX_GLOW).toInt()
			return lit.coerceIn(1, MAX_GLOW)
		}
	}
}
