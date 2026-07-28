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
 * The silhouette is a deepslate sanctum: a broken colonnade around a stepped
 * altar, soul-fire braziers at the cardinal points, and a crypt sunk beneath it
 * reached by a stair cut through the platform. Everything is derived from the
 * piece random and the bounding box, so no extra state has to be persisted.
 *
 * Local coordinates run from the bounding box minimum, so [SURFACE] -- not zero
 * -- is ground level; everything below it is the crypt.
 */
class GuardedRuinsPiece : StructurePiece {

	constructor(centre: BlockPos) : super(
		ModStructures.GUARDED_RUINS_PIECE,
		0,
		boxAround(centre),
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
		carveCrypt(level, box, random)
		clearSanctum(level, box)
		layPlatform(level, box, random)
		raiseColonnade(level, box, random)
		buildAltar(level, box, random)
		placeBraziers(level, box)
		cutStairwell(level, box, random)
	}

	// ---------------------------------------------------------------- crypt

	/** Hollows the buried chamber and lines it, sculk creeping across the floor. */
	private fun carveCrypt(level: WorldGenLevel, box: BoundingBox, random: RandomSource) {
		val lo = CRYPT_MARGIN
		val hi = LAST - CRYPT_MARGIN

		// Shell first, then hollow it out -- simpler than tracking wall faces.
		generateBox(level, box, lo, 0, lo, hi, SURFACE - 1, hi, deepslate(random), deepslate(random), false)
		generateBox(level, box, lo + 1, 1, lo + 1, hi - 1, SURFACE - 1, hi - 1, AIR, AIR, false)

		for (x in (lo + 1)..(hi - 1)) {
			for (z in (lo + 1)..(hi - 1)) {
				// Relay the floor so it is not a flat sheet of one texture.
				placeBlock(level, deepslate(random), x, 0, z, box)
				if (random.nextFloat() < SCULK_CHANCE) {
					placeBlock(level, Blocks.SCULK.defaultBlockState(), x, 1, z, box)
				}
			}
		}

		// Four lanterns at the chamber corners: the only light down here.
		for (x in intArrayOf(lo + 1, hi - 1)) {
			for (z in intArrayOf(lo + 1, hi - 1)) {
				placeBlock(level, Blocks.SOUL_LANTERN.defaultBlockState(), x, 1, z, box)
			}
		}
	}

	// ------------------------------------------------------------- platform

	/**
	 * Empties the volume the sanctum stands in.
	 *
	 * Structure placement anchors to the *noise-predicted* terrain height, which
	 * does not include snow layers, topsoil or anything the surface rules add
	 * afterwards. Without this the platform ends up sealed under the finished
	 * ground and only the pillars show. Clearing also removes trees and overburden
	 * so the ruin reads as a clearing rather than a shape pushed into a hillside.
	 */
	private fun clearSanctum(level: WorldGenLevel, box: BoundingBox) {
		generateBox(level, box, 0, SURFACE + 1, 0, LAST, SURFACE + ABOVE, LAST, AIR, AIR, false)
	}

	/** The ground-level slab, which doubles as the crypt ceiling. */
	private fun layPlatform(level: WorldGenLevel, box: BoundingBox, random: RandomSource) {
		for (x in 0..LAST) {
			for (z in 0..LAST) {
				placeBlock(level, deepslate(random), x, SURFACE, z, box)
			}
		}
	}

	// ------------------------------------------------------------ colonnade

	/** Pillars at the corners and cardinal midpoints, plus the broken wall between. */
	private fun raiseColonnade(level: WorldGenLevel, box: BoundingBox, random: RandomSource) {
		for (i in 0..LAST) {
			ruinedWall(level, box, random, i, 0)
			ruinedWall(level, box, random, i, LAST)
			ruinedWall(level, box, random, 0, i)
			ruinedWall(level, box, random, LAST, i)
		}

		val mid = LAST / 2
		val inner = LAST - 1

		// Corners are 2x2 shafts. A one-block column at this height reads as a
		// fence post, not a pillar -- the extra thickness is what makes the ruin
		// look built rather than scattered.
		for ((cx, cz) in listOf(0 to 0, 0 to inner, inner to 0, inner to inner)) {
			val top = PILLAR_TALL - random.nextInt(3)
			for (dx in 0..1) {
				for (dz in 0..1) {
					for (y in 1..top) {
						placeBlock(level, deepslate(random), cx + dx, SURFACE + y, cz + dz, box)
					}
					placeBlock(
						level,
						Blocks.CHISELED_DEEPSLATE.defaultBlockState(),
						cx + dx, SURFACE + top + 1, cz + dz, box,
					)
				}
			}
		}

		// Cardinal posts stay single and snap off lower, so the ring is uneven.
		for ((x, z) in listOf(mid to 0, mid to LAST, 0 to mid, LAST to mid)) {
			val top = PILLAR_SHORT - random.nextInt(3)
			for (y in 1..top) {
				placeBlock(level, deepslate(random), x, SURFACE + y, z, box)
			}
			placeBlock(level, Blocks.CHISELED_DEEPSLATE.defaultBlockState(), x, SURFACE + top + 1, z, box)
		}
	}

	/**
	 * One wall column.
	 *
	 * The foundation course is never skipped -- an unbroken base is what makes
	 * the perimeter read as a collapsed wall instead of a row of loose stones.
	 * Only the courses above it are allowed to be missing.
	 */
	private fun ruinedWall(
		level: WorldGenLevel,
		box: BoundingBox,
		random: RandomSource,
		x: Int,
		z: Int,
	) {
		placeBlock(level, deepslate(random), x, SURFACE + 1, z, box)

		if (random.nextFloat() < GAP_CHANCE) return

		val height = 2 + random.nextInt(WALL_MAX)
		for (y in 2..height) {
			placeBlock(level, deepslate(random), x, SURFACE + y, z, box)
		}
	}

	// ---------------------------------------------------------------- altar

	/** Stepped plinth at the centre -- where the memory will eventually rest. */
	private fun buildAltar(level: WorldGenLevel, box: BoundingBox, random: RandomSource) {
		val mid = LAST / 2

		for (x in (mid - 2)..(mid + 2)) {
			for (z in (mid - 2)..(mid + 2)) {
				placeBlock(level, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), x, SURFACE + 1, z, box)
			}
		}
		for (x in (mid - 1)..(mid + 1)) {
			for (z in (mid - 1)..(mid + 1)) {
				placeBlock(level, tiles(random), x, SURFACE + 2, z, box)
			}
		}
		placeBlock(level, Blocks.CHISELED_DEEPSLATE.defaultBlockState(), mid, SURFACE + 3, mid, box)

		for (x in intArrayOf(mid - 2, mid + 2)) {
			for (z in intArrayOf(mid - 2, mid + 2)) {
				placeBlock(level, Blocks.SOUL_LANTERN.defaultBlockState(), x, SURFACE + 2, z, box)
			}
		}
	}

	/**
	 * Soul campfires on low plinths. They light the sanctum blue, throw a
	 * particle column, and burn anything that walks into them -- the ruin's only
	 * standing defence until the guardians arrive.
	 */
	private fun placeBraziers(level: WorldGenLevel, box: BoundingBox) {
		val near = CRYPT_MARGIN
		val far = LAST - CRYPT_MARGIN

		for (x in intArrayOf(near, far)) {
			for (z in intArrayOf(near, far)) {
				placeBlock(level, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), x, SURFACE + 1, z, box)
				placeBlock(level, Blocks.SOUL_CAMPFIRE.defaultBlockState(), x, SURFACE + 2, z, box)
			}
		}
	}

	// ------------------------------------------------------------ stairwell

	/**
	 * Sinks the descent into the crypt as a stairwell mouth in the plaza.
	 *
	 * It starts two blocks inside the platform edge rather than at it. Running
	 * the stair out to the edge instead opens its head into untouched ground --
	 * a wall of raw dirt at the top of the stairs -- and severs the perimeter
	 * wall on the way past.
	 *
	 * Run last: it deliberately overwrites the platform laid above it.
	 */
	private fun cutStairwell(level: WorldGenLevel, box: BoundingBox, random: RandomSource) {
		val mid = LAST / 2
		val zLo = mid - 1
		val zHi = mid + 1
		val headX = LAST - 2

		// Cast the buried run of the stair as one solid block of masonry, then cut
		// the steps out of it. Facing the shaft afterwards never covers everything
		// -- the back wall and the ground below the treads stay bare -- and the
		// raw terrain shows through wherever the cut passes.
		generateBox(
			level, box,
			CRYPT_OUTER + 1, 0, zLo - 1,
			headX + 1, SURFACE, zHi + 1,
			deepslate(random), deepslate(random), false,
		)

		for (step in 0 until SURFACE) {
			val x = headX - step
			val treadY = SURFACE - 1 - step
			val buried = x > CRYPT_OUTER

			// Outside the crypt the shaft has to be cut up through the platform to
			// open the mouth. Once inside, the chamber is already hollow and the
			// platform is its ceiling -- carrying the cut on would punch a hole in
			// the plaza floor and daylight the crypt.
			val ceiling = if (buried) SURFACE else SURFACE - 1
			generateBox(level, box, x, treadY + 1, zLo, x, ceiling, zHi, AIR, AIR, false)

			for (z in zLo..zHi) {
				placeBlock(level, deepslate(random), x, treadY, z, box)
			}
		}
	}

	// --------------------------------------------------------------- blocks

	/** Deepslate brickwork that has not aged evenly. */
	private fun deepslate(random: RandomSource): BlockState = when (random.nextInt(10)) {
		0, 1, 2 -> Blocks.CRACKED_DEEPSLATE_BRICKS
		3, 4 -> Blocks.DEEPSLATE_TILES
		5 -> Blocks.CRACKED_DEEPSLATE_TILES
		6 -> Blocks.COBBLED_DEEPSLATE
		else -> Blocks.DEEPSLATE_BRICKS
	}.defaultBlockState()

	private fun tiles(random: RandomSource): BlockState =
		if (random.nextBoolean()) {
			Blocks.DEEPSLATE_TILES.defaultBlockState()
		} else {
			Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState()
		}

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

		private val AIR: BlockState = Blocks.AIR.defaultBlockState()

		/** Footprint, in blocks, on each horizontal axis. */
		const val WIDTH: Int = 17

		/** Blocks of crypt below ground level. */
		const val CRYPT_DEPTH: Int = 6

		/** Headroom reserved above ground level for the tallest pillar. */
		const val ABOVE: Int = 13

		/** Local Y of ground level. Below it is crypt, above it is sanctum. */
		private const val SURFACE = CRYPT_DEPTH

		private const val LAST = WIDTH - 1

		/** Inset of the crypt shell from the platform edge. */
		private const val CRYPT_MARGIN = 4

		/** Local X of the crypt's far shell wall -- the boundary the stair pierces. */
		private const val CRYPT_OUTER = WIDTH - 1 - CRYPT_MARGIN

		private const val GAP_CHANCE = 0.40f
		private const val SCULK_CHANCE = 0.18f
		private const val WALL_MAX = 4
		private const val PILLAR_TALL = 10
		private const val PILLAR_SHORT = 6

		private fun boxAround(centre: BlockPos): BoundingBox {
			val half = WIDTH / 2
			return BoundingBox(
				centre.x - half,
				centre.y - CRYPT_DEPTH,
				centre.z - half,
				centre.x - half + LAST,
				centre.y + ABOVE,
				centre.z - half + LAST,
			)
		}
	}
}
