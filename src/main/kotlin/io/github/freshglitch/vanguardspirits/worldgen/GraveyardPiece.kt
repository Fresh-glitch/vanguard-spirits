package io.github.freshglitch.vanguardspirits.worldgen

import io.github.freshglitch.vanguardspirits.block.GraveBlock
import io.github.freshglitch.vanguardspirits.registry.ModBlocks
import io.github.freshglitch.vanguardspirits.registry.ModStructures
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.RandomSource
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SlabBlock
import net.minecraft.world.level.block.StairBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.Half
import net.minecraft.world.level.block.state.properties.SlabType
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.StructurePiece
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext
import kotlin.math.abs
import kotlin.math.max

/**
 * The burial ground the Mourners circle over, and the way down into the ruin.
 *
 * Before this the chain stopped halfway: birds in the sky, a sealed sanctum
 * ninety blocks under them and nothing in between, so the only way in was to
 * mine blindly and hope. The graveyard is the part that was missing -- it says
 * *here*, it says what is buried, and the mausoleum at its west end opens onto
 * the stair.
 *
 * ## Three things this file is careful about
 *
 * **Height is decided once, by the structure, and travels in the bounding box.**
 * `postProcess` runs separately for every chunk the piece touches, so a plot
 * level worked out here would be worked out several times from different views
 * of the terrain and the halves would not meet. [plotLevel] reads it back off
 * the box, which the base class already persists.
 *
 * **Nothing structural is drawn from the piece random**, for the same reason --
 * each chunk gets its own. Grave positions, which graves have already been
 * opened, which stones are cracked: all of it is a hash of the position, so
 * every chunk computes the same answer about the parts it can see.
 *
 * **The stair comes out on the far side of the cavern from the crypt.** The
 * Sentinel's trigger is measured from the stairwell it wards, not from itself,
 * so landing the shaft near that mouth woke it through a wall before the player
 * had seen anything. The mausoleum is at the west end for that reason alone.
 */
class GraveyardPiece : StructurePiece {

	/** What the surface here is made of. See [Ground]. */
	private val ground: Ground

	constructor(centre: BlockPos, plotY: Int, cavernFloorY: Int, ground: Ground) : super(
		ModStructures.GRAVEYARD_PIECE,
		0,
		BoundingBox(
			centre.x - HALF_X,
			cavernFloorY,
			centre.z - HALF_Z,
			centre.x + HALF_X,
			plotY + ABOVE_PLOT,
			centre.z + HALF_Z,
		),
	) {
		this.ground = ground
		setOrientation(IDENTITY_FACING)
	}

	constructor(tag: CompoundTag) : super(ModStructures.GRAVEYARD_PIECE, tag) {
		this.ground = Ground.byName(tag.getStringOr(TAG_GROUND, Ground.EARTH.name))
		setOrientation(IDENTITY_FACING)
	}

	/**
	 * The one thing the box cannot carry.
	 *
	 * Everything else about the layout is recovered from the bounding box, but
	 * the palette is a fact about the biome the structure was sited in and there
	 * is nowhere in the geometry to read it back out of.
	 */
	override fun addAdditionalSaveData(
		context: StructurePieceSerializationContext,
		tag: CompoundTag,
	) {
		tag.putString(TAG_GROUND, ground.name)
	}

	/**
	 * Local Y of the ground the graveyard stands on.
	 *
	 * Derived rather than stored. The box top is the plot plus a fixed headroom,
	 * and that headroom is also what gives the footprint clearing room to take a
	 * dark oak canopy off.
	 */
	private val plotLevel: Int
		get() = boundingBox.maxY() - ABOVE_PLOT - boundingBox.minY()

	override fun postProcess(
		level: WorldGenLevel,
		structureManager: StructureManager,
		generator: ChunkGenerator,
		random: RandomSource,
		box: BoundingBox,
		chunkPos: ChunkPos,
		pos: BlockPos,
	) {
		val floor = plotLevel

		clearFootprint(level, box, floor)
		layGround(level, box, floor)
		raiseRailing(level, box, floor)
		layPath(level, box, floor)
		digGraves(level, box, floor)
		buildMausoleum(level, box, floor)
		sinkShaft(level, box, floor)
	}

	// ----------------------------------------------------------------- ground

	/**
	 * Takes the hillside, the trees and the snow off the plot.
	 *
	 * Cleared against the *live* heightmap rather than the noise prediction the
	 * structure sited itself with: the prediction is taken before the surface
	 * rules run, so it knows nothing about topsoil, snow or anything a feature
	 * put on top, and building to it leaves the graveyard sealed under its own
	 * ground.
	 */
	private fun clearFootprint(level: WorldGenLevel, box: BoundingBox, floor: Int) {
		for (x in 0..LAST_X) {
			for (z in 0..LAST_Z) {
				if (!owns(box, x, z)) continue

				val wx = getWorldX(x, z)
				val wz = getWorldZ(x, z)

				// WORLD_SURFACE rather than MOTION_BLOCKING, because leaves are not
				// motion blocking and a canopy left hanging over a cleared plot is
				// the one thing that would still read as broken.
				val surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - boundingBox.minY()
				val top = max(surface + CANOPY_MARGIN, floor + 1)

				for (y in (floor + 1)..top) {
					placeBlock(level, AIR, x, y, z, box)
				}
			}
		}
	}

	/**
	 * Whether this column belongs to the chunk currently being written.
	 *
	 * `placeBlock` already drops anything outside, so this is not about stray
	 * blocks -- it is about the two calls that *read*. `getHeight` and [getBlock]
	 * would otherwise be asked about columns in a neighbouring chunk that has not
	 * reached its surface stage yet, which 26.2 detects and logs as an unsafe
	 * terrain read. It also saves three quarters of the heightmap lookups, since
	 * the plot spans several chunks and each of them runs this whole pass.
	 */
	private fun owns(box: BoundingBox, x: Int, z: Int): Boolean {
		val wx = getWorldX(x, z)
		val wz = getWorldZ(x, z)
		return wx >= box.minX() && wx <= box.maxX() && wz >= box.minZ() && wz <= box.maxZ()
	}

	/**
	 * Lays the plot itself and props up whatever the slope left hanging.
	 *
	 * The fill downward is bounded rather than run to the first solid block: an
	 * unbounded one over a cave mouth would pour into the cavern the ruin has
	 * just finished hollowing.
	 */
	private fun layGround(level: WorldGenLevel, box: BoundingBox, floor: Int) {
		for (x in 0..LAST_X) {
			for (z in 0..LAST_Z) {
				if (!owns(box, x, z)) continue

				placeBlock(level, turf(x, z), x, floor, z, box)

				for (depth in 1..TERRACE_DEPTH) {
					val y = floor - depth
					if (y < 1) break
					// Only what is hanging. Anything already solid is the hillside
					// doing the job for us.
					if (!getBlock(level, x, y, z, box).isAir) break
					placeBlock(level, if (depth < 3) subsoil(x, z) else bedding(x, y, z), x, y, z, box)
				}
			}
		}
	}

	// ---------------------------------------------------------------- railing

	/**
	 * A kerb of old stone with wrought iron standing on it.
	 *
	 * Iron bars specifically, because they are one of the few connecting blocks a
	 * structure can place and have come out joined: `placeBlock` writes with the
	 * client-update flag only, so nothing recalculates its shape afterwards
	 * unless it is in vanilla's post-processing set. Walls are *not* in that set
	 * and would generate as a row of loose posts.
	 */
	private fun raiseRailing(level: WorldGenLevel, box: BoundingBox, floor: Int) {
		for (x in 0..LAST_X) {
			for (z in 0..LAST_Z) {
				if (x != 0 && x != LAST_X && z != 0 && z != LAST_Z) continue

				placeBlock(level, kerb(x, z), x, floor, z, box)

				// The gate, and the gap the mausoleum fills in behind us.
				if (x == LAST_X && abs(z - LAST_Z / 2) <= GATE_HALF) continue
				if (x <= MAUS_EAST && z >= MAUS_NORTH && z <= MAUS_SOUTH) continue

				val post = (x == 0 || x == LAST_X) && (z == 0 || z == LAST_Z) ||
					(x % POST_EVERY == 0 && (z == 0 || z == LAST_Z)) ||
					(z % POST_EVERY == 0 && (x == 0 || x == LAST_X))

				if (post) {
					for (y in 1..RAILING_HEIGHT) {
						placeBlock(level, kerb(x, y + z), x, floor + y, z, box)
					}
					placeBlock(level, capstone(), x, floor + RAILING_HEIGHT + 1, z, box)
					continue
				}

				// Bars rust through in places, which is what stops the perimeter
				// reading as a fence somebody put up last week.
				if (chance(x, z, 21, 0.14f)) continue
				for (y in 1..RAILING_HEIGHT) {
					placeBlock(level, Blocks.IRON_BARS.defaultBlockState(), x, floor + y, z, box)
				}
			}
		}

		// Gateposts, taller than the rest so the entrance reads from outside.
		for (z in intArrayOf(LAST_Z / 2 - GATE_HALF - 1, LAST_Z / 2 + GATE_HALF + 1)) {
			for (y in 1..(RAILING_HEIGHT + 2)) {
				placeBlock(level, kerb(y, z), LAST_X, floor + y, z, box)
			}
			placeBlock(level, finial(), LAST_X, floor + RAILING_HEIGHT + 3, z, box)
		}
	}

	// ------------------------------------------------------------------- path

	/** The way from the gate to the mausoleum door, worn through the middle. */
	private fun layPath(level: WorldGenLevel, box: BoundingBox, floor: Int) {
		val mid = LAST_Z / 2
		for (x in (MAUS_EAST + 1)..LAST_X) {
			for (z in (mid - PATH_HALF)..(mid + PATH_HALF)) {
				placeBlock(level, paving(x, z), x, floor, z, box)
			}
		}
	}

	// ----------------------------------------------------------------- graves

	/**
	 * The plots themselves: a headstone at the head, a raised mound behind it.
	 *
	 * Roughly one in six has already been opened -- an empty pit and a stone
	 * knocked flat. That is the warning. A player who reads it and leaves the
	 * rest alone walks out of here; a player who does not gets what came out of
	 * the ones already standing open.
	 */
	private fun digGraves(level: WorldGenLevel, box: BoundingBox, floor: Int) {
		for (row in GRAVE_ROWS) {
			var col = GRAVE_FIRST_COL
			while (col <= GRAVE_LAST_COL) {
				grave(level, box, floor, col, row)
				col += GRAVE_COL_STEP
			}
		}
	}

	private fun grave(level: WorldGenLevel, box: BoundingBox, floor: Int, x: Int, headZ: Int) {
		val opened = chance(x, headZ, 41, 0.17f)

		headstone(level, box, floor, x, headZ, opened)

		for (dz in 1..GRAVE_LENGTH) {
			val z = headZ + dz
			if (opened) {
				// Dug out and never filled in. Two deep, so it reads as a hole
				// rather than a scuff in the ground.
				placeBlock(level, subsoil(x, z), x, floor - PIT_DEPTH, z, box)
				for (y in (floor - PIT_DEPTH + 1)..(floor + 1)) {
					placeBlock(level, AIR, x, y, z, box)
				}
			} else {
				placeBlock(level, subsoil(x, z), x, floor, z, box)
				placeBlock(
					level,
					ModBlocks.GRAVE.defaultBlockState().setValue(GraveBlock.OCCUPIED, true),
					x, floor + 1, z, box,
				)
			}
		}
	}

	/**
	 * One marker.
	 *
	 * Deepslate whatever the surface is made of, along with the mausoleum and the
	 * shaft. It is the one material the graveyard shares with the sanctum ninety
	 * blocks under it, and the reason a player believes the hole in the floor
	 * goes somewhere -- so it does not get a desert variant.
	 */
	private fun headstone(
		level: WorldGenLevel,
		box: BoundingBox,
		floor: Int,
		x: Int,
		z: Int,
		toppled: Boolean,
	) {
		if (toppled) {
			// Face down in the dirt, which is the only way a broken one still
			// reads as a headstone rather than as litter.
			placeBlock(
				level,
				Blocks.COBBLED_DEEPSLATE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
				x, floor + 1, z, box,
			)
			return
		}

		placeBlock(level, marker(x, z), x, floor + 1, z, box)

		// Half of them keep their capstone; the rest have lost it to nine hundred
		// years of weather, which is what stops the rows looking machined.
		if (chance(x, z, 51, 0.55f)) {
			placeBlock(
				level,
				Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
				x, floor + 2, z, box,
			)
		}

		if (chance(x, z, 52, 0.18f)) {
			placeBlock(level, Blocks.COBWEB.defaultBlockState(), x, floor + 2, z, box)
		}
	}

	// -------------------------------------------------------------- mausoleum

	/** The building at the west end, holding the mouth of the stair. */
	private fun buildMausoleum(level: WorldGenLevel, box: BoundingBox, floor: Int) {
		val doorZ = LAST_Z / 2

		for (x in 0..MAUS_EAST) {
			for (z in MAUS_NORTH..MAUS_SOUTH) {
				val wall = x == 0 || x == MAUS_EAST || z == MAUS_NORTH || z == MAUS_SOUTH

				placeBlock(level, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), x, floor, z, box)

				if (!wall) {
					for (y in 1..MAUS_HEIGHT) {
						placeBlock(level, AIR, x, floor + y, z, box)
					}
					continue
				}

				for (y in 1..MAUS_HEIGHT) {
					placeBlock(level, masonry(x, y, z), x, floor + y, z, box)
				}
			}
		}

		// Doorway, two tall, in the wall facing the graves.
		for (y in 1..2) {
			placeBlock(level, AIR, MAUS_EAST, floor + y, doorZ, box)
		}
		placeBlock(level, Blocks.CHISELED_DEEPSLATE.defaultBlockState(), MAUS_EAST, floor + 3, doorZ, box)

		// Roof, with a slab lip on the perimeter so it casts a shadow line.
		for (x in 0..MAUS_EAST) {
			for (z in MAUS_NORTH..MAUS_SOUTH) {
				placeBlock(level, masonry(x, MAUS_HEIGHT + 1, z), x, floor + MAUS_HEIGHT + 1, z, box)
			}
		}
		for (x in 0..MAUS_EAST) {
			for (z in MAUS_NORTH..MAUS_SOUTH) {
				if (x != 0 && x != MAUS_EAST && z != MAUS_NORTH && z != MAUS_SOUTH) continue
				placeBlock(
					level,
					Blocks.DEEPSLATE_TILE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
					x, floor + MAUS_HEIGHT + 2, z, box,
				)
			}
		}

		// Lanterns either side of the door, inside.
		for (dz in intArrayOf(-2, 2)) {
			placeBlock(level, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), MAUS_EAST - 1, floor + 1, doorZ + dz, box)
			placeBlock(level, Blocks.SOUL_LANTERN.defaultBlockState(), MAUS_EAST - 1, floor + 2, doorZ + dz, box)
		}
	}

	// ------------------------------------------------------------------ shaft

	/**
	 * The descent, cast solid and then carved out of itself.
	 *
	 * Cast first because facing a shaft after cutting it never covers everything
	 * -- the back of each tread and the rock under it stay bare, and at this
	 * depth the cut passes through dirt, stone, deepslate and whatever caves are
	 * in the way, so the bare patches would be four different colours.
	 *
	 * The stair is a newel spiral: a single column left standing in the middle
	 * and the eight cells around it stepped down one block each, so it drops
	 * eight blocks a turn. Every cell of the ring carries a tread, which is what
	 * makes it walkable in both directions rather than a series of jumps.
	 */
	private fun sinkShaft(level: WorldGenLevel, box: BoundingBox, floor: Int) {
		// Solid masonry, cavern floor to the mausoleum's own floor.
		for (x in (SHAFT_X - SHAFT_OUTER)..(SHAFT_X + SHAFT_OUTER)) {
			for (z in (SHAFT_Z - SHAFT_OUTER)..(SHAFT_Z + SHAFT_OUTER)) {
				for (y in 0..floor) {
					placeBlock(level, masonry(x, y, z), x, y, z, box)
				}
			}
		}

		// Which cell the descent begins on is chosen so that it *ends* on the one
		// facing the sanctum. Picking the start instead of correcting at the
		// bottom is what keeps the last tread flush with the cavern floor -- run
		// from a fixed cell, the stair finishes wherever the drop happens to fall
		// modulo the ring and the way out is part way up a wall.
		val start = Math.floorMod(EXIT_CELL - floor, RING.size)

		for (step in 0..floor) {
			val (dx, dz) = RING[(start + step) % RING.size]
			val x = SHAFT_X + dx
			val z = SHAFT_Z + dz
			val y = floor - step

			// Headroom only, cut out of the cast. Consecutive turns are a ring
			// apart, so four courses of masonry stay between one flight's ceiling
			// and the next flight's tread -- the stair gets a proper soffit and
			// there is nowhere to fall through.
			for (head in 1..HEADROOM) {
				placeBlock(level, AIR, x, y + head, z, box)
			}
			placeBlock(level, tread(step), x, y, z, box)

			// A light set into the newel, once a turn, level with the tread beside
			// it. The newel is solid all the way down, so it holds the lantern up
			// without anything else being needed.
			if ((start + step) % RING.size == LANTERN_CELL) {
				placeBlock(level, Blocks.SOUL_LANTERN.defaultBlockState(), SHAFT_X, y + 1, SHAFT_Z, box)
			}
		}

		// The mouth, opening east onto the cavern floor -- the far side of the
		// sanctum from the crypt stairwell, which is what the Sentinel's trigger
		// is measured from. Landing beside that mouth woke it through a wall.
		for (y in 1..HEADROOM) {
			placeBlock(level, AIR, SHAFT_X + SHAFT_OUTER, y, SHAFT_Z, box)
		}
		placeBlock(
			level,
			Blocks.DEEPSLATE_BRICK_STAIRS.defaultBlockState()
				.setValue(StairBlock.FACING, Direction.WEST)
				.setValue(StairBlock.HALF, Half.TOP),
			SHAFT_X + SHAFT_OUTER, HEADROOM + 1, SHAFT_Z, box,
		)
	}

	/** A step. Cracked more often the deeper it is, since nobody mends these. */
	private fun tread(step: Int): BlockState = when {
		step % 7 == 3 -> Blocks.CRACKED_DEEPSLATE_BRICKS
		step % 5 == 2 -> Blocks.DEEPSLATE_TILES
		else -> Blocks.DEEPSLATE_BRICKS
	}.defaultBlockState()

	// ---------------------------------------------------------------- palette

	/** The surface of the plot. */
	private fun turf(x: Int, z: Int): BlockState = when (ground) {
		Ground.SAND -> when {
			chance(x, z, 12, 0.18f) -> Blocks.SANDSTONE
			chance(x, z, 13, 0.10f) -> Blocks.SMOOTH_SANDSTONE
			else -> Blocks.SAND
		}

		Ground.SNOW -> when {
			chance(x, z, 12, 0.16f) -> Blocks.COARSE_DIRT
			chance(x, z, 13, 0.10f) -> Blocks.PODZOL
			else -> Blocks.SNOW_BLOCK
		}

		Ground.EARTH -> {
			val edge = abs(x - LAST_X / 2) > LAST_X / 3 || abs(z - LAST_Z / 2) > LAST_Z / 3
			when {
				edge && chance(x, z, 11, 0.55f) -> Blocks.GRASS_BLOCK
				chance(x, z, 12, 0.30f) -> Blocks.PODZOL
				chance(x, z, 13, 0.12f) -> Blocks.ROOTED_DIRT
				else -> Blocks.COARSE_DIRT
			}
		}
	}.defaultBlockState()

	/** What a grave is dug in, and the first courses under the plot. */
	private fun subsoil(x: Int, z: Int): BlockState = when (ground) {
		Ground.SAND -> if (chance(x, z, 14, 0.25f)) Blocks.SANDSTONE else Blocks.SAND
		else -> Blocks.COARSE_DIRT
	}.defaultBlockState()

	/** Backfill under the terrace, below the topsoil courses. */
	private fun bedding(x: Int, y: Int, z: Int): BlockState = when (ground) {
		Ground.SAND -> if (chance(x + y, z, 91, 0.45f)) Blocks.SANDSTONE else Blocks.SAND
		else -> if (chance(x + y, z, 91, 0.35f)) Blocks.COBBLESTONE else Blocks.STONE
	}.defaultBlockState()

	/** The kerb the railing stands on, and the gateposts. */
	private fun kerb(a: Int, b: Int): BlockState = when (ground) {
		Ground.SAND -> when {
			chance(a, b, 61, 0.30f) -> Blocks.CUT_SANDSTONE
			chance(a, b, 62, 0.20f) -> Blocks.CHISELED_SANDSTONE
			chance(a, b, 63, 0.15f) -> Blocks.SMOOTH_SANDSTONE
			else -> Blocks.SANDSTONE
		}

		// No moss. Nothing grows on stone left under permafrost, and mossy
		// cobblestone under a snow layer reads as a texture bug rather than age.
		Ground.SNOW -> when {
			chance(a, b, 61, 0.28f) -> Blocks.CRACKED_STONE_BRICKS
			chance(a, b, 62, 0.20f) -> Blocks.COBBLESTONE
			else -> Blocks.STONE_BRICKS
		}

		Ground.EARTH -> when {
			chance(a, b, 61, 0.30f) -> Blocks.MOSSY_COBBLESTONE
			chance(a, b, 62, 0.20f) -> Blocks.MOSSY_STONE_BRICKS
			chance(a, b, 63, 0.15f) -> Blocks.CRACKED_STONE_BRICKS
			else -> Blocks.COBBLESTONE
		}
	}.defaultBlockState()

	/** Cap on a railing post. */
	private fun capstone(): BlockState = when (ground) {
		Ground.SAND -> Blocks.SANDSTONE_SLAB
		else -> Blocks.COBBLESTONE_SLAB
	}.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)

	/** Cap on a gatepost, which is taller and gets something with a shape to it. */
	private fun finial(): BlockState = when (ground) {
		Ground.SAND -> Blocks.CHISELED_SANDSTONE
		else -> Blocks.CHISELED_STONE_BRICKS
	}.defaultBlockState()

	/** The path from the gate to the door. */
	private fun paving(x: Int, z: Int): BlockState = when (ground) {
		Ground.SAND -> if (chance(x, z, 31, 0.30f)) Blocks.CUT_SANDSTONE else Blocks.SMOOTH_SANDSTONE
		else -> if (chance(x, z, 31, 0.22f)) Blocks.COBBLESTONE else Blocks.GRAVEL
	}.defaultBlockState()

	/** Mausoleum and shaft. Matches the sanctum at the bottom of it, always. */
	private fun masonry(x: Int, y: Int, z: Int): BlockState = when {
		chance(x + y, z, 81, 0.22f) -> Blocks.CRACKED_DEEPSLATE_BRICKS
		chance(x, z + y, 82, 0.16f) -> Blocks.DEEPSLATE_TILES
		chance(x + z, y, 83, 0.08f) -> Blocks.COBBLED_DEEPSLATE
		else -> Blocks.DEEPSLATE_BRICKS
	}.defaultBlockState()

	/** A headstone's body. Deepslate wherever the graveyard is. */
	private fun marker(x: Int, z: Int): BlockState = when {
		chance(x, z, 71, 0.30f) -> Blocks.COBBLED_DEEPSLATE
		chance(x, z, 72, 0.25f) -> Blocks.CRACKED_DEEPSLATE_BRICKS
		chance(x, z, 73, 0.20f) -> Blocks.DEEPSLATE_TILES
		else -> Blocks.POLISHED_DEEPSLATE
	}.defaultBlockState()

	/**
	 * What the surface of a graveyard is made of.
	 *
	 * Only the ground, the kerb and the path follow it. The graves, the mausoleum
	 * and the shaft stay deepslate everywhere on purpose -- they are what the
	 * place has in common with the sanctum underneath, and a sandstone mausoleum
	 * over a deepslate ruin would break the one thread joining them.
	 */
	enum class Ground {
		EARTH,
		SAND,
		SNOW,
		;

		companion object {
			fun byName(name: String): Ground = entries.firstOrNull { it.name == name } ?: EARTH
		}
	}

	companion object {
		/** SOUTH is the identity transform. Null orientation silently places nothing. */
		private val IDENTITY_FACING = Direction.SOUTH

		private val AIR: BlockState = Blocks.AIR.defaultBlockState()

		private const val TAG_GROUND = "Ground"

		/**
		 * Half-extents of the plot. Kept inside the ruin's own footprint so the
		 * structure's overall box, and everything keyed off its centre, is
		 * unchanged.
		 */
		const val HALF_X: Int = 16
		const val HALF_Z: Int = 13

		private const val LAST_X = HALF_X * 2
		private const val LAST_Z = HALF_Z * 2

		/**
		 * Headroom reserved above the plot.
		 *
		 * Doing double duty: it is how [plotLevel] is recovered from the box, and
		 * it is the reach the footprint clearing has to work with. A dark oak
		 * canopy is the tallest thing likely to be standing here, so this has to
		 * clear one.
		 */
		const val ABOVE_PLOT: Int = 26

		/** Slack over the live surface height when clearing, for snow and litter. */
		private const val CANOPY_MARGIN = 2

		/**
		 * How far the terrace will prop up a slope before giving up.
		 *
		 * Matched to the fall the structure is willing to site on, so anything that
		 * passes that test can be stood up properly. Bounded rather than run to the
		 * first solid block on purpose: an unbounded fill over a cave mouth would
		 * pour into the cavern the ruin has just finished hollowing.
		 */
		const val TERRACE_DEPTH: Int = 16

		private const val RAILING_HEIGHT = 2
		private const val POST_EVERY = 4
		private const val GATE_HALF = 1
		private const val PATH_HALF = 1

		/** Grave grid. Rows sit either side of the path, which owns the middle. */
		private val GRAVE_ROWS = intArrayOf(3, 7, 17, 21)
		private const val GRAVE_FIRST_COL = 12
		private const val GRAVE_LAST_COL = 30
		private const val GRAVE_COL_STEP = 3
		private const val GRAVE_LENGTH = 2
		private const val PIT_DEPTH = 2

		/** The mausoleum's east wall; its west wall is the plot boundary. */
		private const val MAUS_EAST = 7
		private const val MAUS_NORTH = 9
		private const val MAUS_SOUTH = 17
		private const val MAUS_HEIGHT = 4

		/**
		 * Centre of the shaft, in plot-local coordinates.
		 *
		 * Lands at ruin-local (4, 17): clear of the sanctum, which starts at 9, and
		 * seventeen blocks from the stairwell the Sentinel wards -- well outside
		 * its six block trigger, so the descent ends in silence.
		 */
		private const val SHAFT_X = 3
		private const val SHAFT_Z = 13

		/** Half-extent of the shaft including its wall, so a 5x5 tube. */
		private const val SHAFT_OUTER = 2

		/** The eight cells around the newel, in walking order. */
		private val RING = arrayOf(
			-1 to 0, -1 to -1, 0 to -1, 1 to -1,
			1 to 0, 1 to 1, 0 to 1, -1 to 1,
		)

		/**
		 * Index into [RING] the stair must finish on: the east cell, so the mouth
		 * opens toward the sanctum.
		 */
		private const val EXIT_CELL = 4

		/**
		 * Index into [RING] that gets a lantern, which works out at one every turn
		 * and so one every eight blocks of descent.
		 *
		 * Not left dark for atmosphere. A hundred block stairwell below light level
		 * seven is a mob farm with a handrail, and the fight this leads to should
		 * be the one at the bottom.
		 */
		private const val LANTERN_CELL = 0

		/** Air cut above each tread. Two would do; three stops it feeling like a crawl. */
		private const val HEADROOM = 3

		/**
		 * Positional hash. Every structural decision in this file goes through it
		 * rather than through the piece random, because `postProcess` hands each
		 * chunk its own random and geometry drawn from one would not agree with
		 * itself across a chunk border.
		 */
		private fun hash(a: Int, b: Int, salt: Int): Int {
			var h = (a * 73_856_093) xor (b * 19_349_663) xor (salt * 83_492_791)
			h = h xor (h ushr 13)
			h *= 1_274_126_177
			return h xor (h ushr 16)
		}

		private fun chance(a: Int, b: Int, salt: Int, p: Float): Boolean =
			(hash(a, b, salt) ushr 8 and 0xFFFF) < (p * 0xFFFF).toInt()
	}
}
