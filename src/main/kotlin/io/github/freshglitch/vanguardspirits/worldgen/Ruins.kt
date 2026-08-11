package io.github.freshglitch.vanguardspirits.worldgen

import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.levelgen.structure.Structure

/**
 * Where a Guarded Ruin *is*, for anything that needs to remember something about
 * one.
 *
 * Both [RuinSeal] and [RuinHollow] key their state on the ruin's structure-start
 * chunk, and both have to agree about which chunk that is or a Sentinel dying in
 * one place and a Reliquary opening in another would write to different keys.
 * Two copies of this lookup that drifted would be exactly that bug, so there is
 * one.
 */
object Ruins {

	/**
	 * The start chunk of the Guarded Ruin containing [pos], if any.
	 *
	 * `getStructureWithPieceAt` returns [Structure]'s INVALID_START rather than
	 * null when nothing matches, so the emptiness has to be asked for. The
	 * predicate overload takes a `Predicate<Holder<Structure>>`, which lets a
	 * structure that only exists in a dynamic registry be matched without
	 * resolving it first.
	 *
	 * A ruin's two halves share one structure, so a grave on the surface and the
	 * vault thirty blocks under it both answer with the same chunk.
	 */
	fun startChunkAt(level: ServerLevel, pos: BlockPos): ChunkPos? {
		val start = level.structureManager().getStructureWithPieceAt(pos) { holder: Holder<Structure> ->
			holder.value() is GuardedRuinsStructure
		}
		return if (start.isValid) start.chunkPos else null
	}
}
