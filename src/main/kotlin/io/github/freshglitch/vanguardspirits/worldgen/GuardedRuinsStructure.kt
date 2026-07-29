package io.github.freshglitch.vanguardspirits.worldgen

import com.mojang.serialization.MapCodec
import io.github.freshglitch.vanguardspirits.registry.ModStructures
import net.minecraft.core.BlockPos
import net.minecraft.world.level.NoiseColumn
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureType
import java.util.Optional

/**
 * A Guarded Ruin: a deepslate sanctum buried in a cavern of its own making.
 *
 * There is no NBT template and no jigsaw pool -- [GuardedRuinsPiece] lays the
 * blocks itself, so the shape is varied by the world seed rather than authored.
 */
class GuardedRuinsStructure(settings: StructureSettings) : Structure(settings) {

	override fun findGenerationPoint(context: GenerationContext): Optional<GenerationStub> {
		val chunkPos = context.chunkPos()
		val x = chunkPos.middleBlockX
		val z = chunkPos.middleBlockZ

		val y = MIN_FLOOR + context.random().nextInt(MAX_FLOOR - MIN_FLOOR + 1)

		// Require solid rock through the whole vertical span. The cavern is dug
		// out by the piece, so starting inside an existing void would leave the
		// sanctum floating in it, and starting below the world would leave it in
		// bedrock.
		val column = context.chunkGenerator().getBaseColumn(
			x,
			z,
			context.heightAccessor(),
			context.randomState(),
		)
		if (!isEncased(column, y, context.heightAccessor().minY)) return Optional.empty()

		val centre = BlockPos(x, y, z)

		return Optional.of(
			GenerationStub(centre) { builder -> builder.addPiece(GuardedRuinsPiece(centre)) },
		)
	}

	/** True when the column is solid across the piece's full height at [floorY]. */
	private fun isEncased(column: NoiseColumn, floorY: Int, worldMinY: Int): Boolean {
		val lowest = floorY - GuardedRuinsPiece.DEPTH
		val highest = floorY + GuardedRuinsPiece.ABOVE

		if (lowest <= worldMinY + BEDROCK_CLEARANCE) return false

		// Sampling every block is wasted work; a few rungs catch the cases that
		// matter -- an open cave, a ravine, or the underside of the surface.
		var y = lowest
		while (y <= highest) {
			if (column.getBlock(y).isAir) return false
			y += SAMPLE_STRIDE
		}
		return true
	}

	override fun type(): StructureType<*> = ModStructures.GUARDED_RUINS

	companion object {
		/**
		 * Y band for the sanctum floor.
		 *
		 * Raised from its original -44..-30 when the rooms below the crypt were
		 * added. The piece now reaches [GuardedRuinsPiece.DEPTH] beneath the
		 * sanctum instead of eight blocks, and the old band would have put the
		 * lowest level inside bedrock. The dome still tops out well short of the
		 * stone layer, so the ruin stays buried.
		 */
		private const val MIN_FLOOR = -34
		private const val MAX_FLOOR = -26

		/** Blocks of margin kept above the bedrock floor. */
		private const val BEDROCK_CLEARANCE = 6

		/**
		 * Rungs of the column check. Two catches mineshaft corridors and thin
		 * caves that a coarser stride steps straight over.
		 */
		private const val SAMPLE_STRIDE = 2

		val CODEC: MapCodec<GuardedRuinsStructure> = simpleCodec(::GuardedRuinsStructure)
	}
}
