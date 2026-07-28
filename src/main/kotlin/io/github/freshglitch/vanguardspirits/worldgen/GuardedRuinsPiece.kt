package io.github.freshglitch.vanguardspirits.worldgen

import io.github.freshglitch.vanguardspirits.registry.ModStructures
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.RandomSource
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.StructurePiece
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext

/**
 * One Guarded Ruin, generated block by block rather than from an NBT template.
 *
 * The silhouette is a square colonnade: an intact floor, four corner pillars
 * that survived, and perimeter walls that have collapsed to varying heights.
 * Everything is derived from the piece's own random, so no extra state has to
 * be persisted.
 */
class GuardedRuinsPiece : StructurePiece {

	constructor(origin: BlockPos) : super(
		ModStructures.GUARDED_RUINS_PIECE,
		0,
		BoundingBox(
			origin.x, origin.y, origin.z,
			origin.x + WIDTH - 1, origin.y + HEIGHT - 1, origin.z + WIDTH - 1,
		),
	) {
		setOrientation(IDENTITY_FACING)
	}

	constructor(tag: CompoundTag) : super(ModStructures.GUARDED_RUINS_PIECE, tag) {
		setOrientation(IDENTITY_FACING)
	}

	/**
	 * The layout is regenerated from the piece random and the bounding box, both
	 * of which the base class already persists, so there is nothing to add.
	 */
	override fun addAdditionalSaveData(
		context: StructurePieceSerializationContext,
		tag: CompoundTag,
	) = Unit

	override fun postProcess(
		level: WorldGenLevel,
		structureManager: StructureManager,
		generator: ChunkGenerator,
		random: RandomSource,
		box: BoundingBox,
		chunkPos: ChunkPos,
		pos: BlockPos,
	) {
		val last = WIDTH - 1

		// Floor. Sunk one block so it reads as settled into the ground.
		for (x in 0..last) {
			for (z in 0..last) {
				placeBlock(level, weathered(random), x, 0, z, box)
			}
		}

		// Perimeter walls, collapsed to an uneven height.
		for (i in 0..last) {
			raiseWall(level, random, box, i, 0)
			raiseWall(level, random, box, i, last)
			raiseWall(level, random, box, 0, i)
			raiseWall(level, random, box, last, i)
		}

		// Corner pillars, the part still standing.
		for (corner in CORNERS) {
			val (x, z) = corner
			for (y in 1 until HEIGHT) {
				placeBlock(level, weathered(random), x, y, z, box)
			}
			placeBlock(level, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), x, HEIGHT - 1, z, box)
		}

		// Central plinth -- where the memory will eventually rest.
		val mid = last / 2
		for (x in (mid - 1)..(mid + 1)) {
			for (z in (mid - 1)..(mid + 1)) {
				placeBlock(level, weathered(random), x, 1, z, box)
			}
		}
		placeBlock(level, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), mid, 2, mid, box)
	}

	/** Builds one wall column, leaving gaps so the ring looks broken rather than sawn. */
	private fun raiseWall(
		level: WorldGenLevel,
		random: RandomSource,
		box: BoundingBox,
		x: Int,
		z: Int,
	) {
		if (random.nextFloat() < GAP_CHANCE) return

		val height = 1 + random.nextInt(HEIGHT - 2)
		for (y in 1..height) {
			placeBlock(level, weathered(random), x, y, z, box)
		}
	}

	/** Stone brick that has not aged evenly. */
	private fun weathered(random: RandomSource): BlockState = when {
		random.nextFloat() < 0.30f -> Blocks.MOSSY_STONE_BRICKS
		random.nextFloat() < 0.30f -> Blocks.CRACKED_STONE_BRICKS
		else -> Blocks.STONE_BRICKS
	}.defaultBlockState()

	companion object {
		/**
		 * StructurePiece keeps its orientation in a private field that defaults to
		 * null, and `getWorldX`/`getWorldZ` return the *relative* coordinate
		 * unchanged in that case instead of offsetting by the bounding box. The
		 * result is a piece that generates nothing at all: every block is aimed
		 * near world origin and then dropped by placeBlock's chunk-box check.
		 *
		 * SOUTH is the identity transform (`minX + x`, `minZ + z`); NORTH mirrors
		 * Z. Any non-null horizontal facing keeps blocks inside the box, but SOUTH
		 * is the one that makes local coordinates mean what they read like.
		 */
		private val IDENTITY_FACING = Direction.SOUTH

		const val WIDTH: Int = 11
		const val HEIGHT: Int = 6

		private const val GAP_CHANCE = 0.35f

		private val CORNERS = listOf(
			0 to 0,
			0 to (WIDTH - 1),
			(WIDTH - 1) to 0,
			(WIDTH - 1) to (WIDTH - 1),
		)
	}
}
