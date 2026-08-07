package io.github.freshglitch.vanguardspirits.worldgen

import io.github.freshglitch.vanguardspirits.block.GoldenChestBlock
import io.github.freshglitch.vanguardspirits.block.MuralBlock
import io.github.freshglitch.vanguardspirits.block.entity.GoldenChestBlockEntity
import io.github.freshglitch.vanguardspirits.registry.ModBlocks
import io.github.freshglitch.vanguardspirits.registry.ModEntities
import io.github.freshglitch.vanguardspirits.registry.ModLootTables
import io.github.freshglitch.vanguardspirits.registry.ModStructures
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.util.RandomSource
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.StructureManager
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkGenerator
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.level.levelgen.structure.StructurePiece
import net.minecraft.world.level.block.entity.SpawnerBlockEntity
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * One Guarded Ruin: a deepslate sanctum standing in a cavern of its own making,
 * buried deep in the deepslate layer.
 *
 * The cavern is hollowed rather than found. Caves are carved *after* structures
 * choose their position, so a structure cannot reliably detect an existing one
 * at placement time -- vanilla's underground structures do not try either. The
 * piece therefore digs its own dome and builds the sanctum inside it.
 *
 * Two coordinate systems are in play. Local coordinates run from the bounding
 * box minimum and span the whole cavern; sanctum coordinates are offset by
 * [MARGIN] on both horizontal axes and are what [put] and [fill] take, so the
 * building code can stay in terms of the ruin itself.
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
		hollowCavern(level, box)
		dressCavern(level, box, random)
		carveDeeps(level, box, random)
		carveCrypt(level, box, random)
		layPlatform(level, box, random)
		raiseColonnade(level, box, random)
		buildAltar(level, box, random)
		placeBraziers(level, box)
		cutStairwell(level, box, random)
		breakCryptFloor(level, box, random)
		setMurals(level, box)
		setSentinel(level, box)
	}

	// --------------------------------------------------------------- murals

	/**
	 * The six passages below ground, one per level of the descent.
	 *
	 * Placed last of the block work, so nothing that runs after can write over
	 * them. Each sits in a wall the chamber has *already* cast solid -- both the
	 * crypt and the deeps build a shell and then hollow it, so the outer faces
	 * are known to exist and the only choice left is which one.
	 *
	 * ## Why these positions and not a scatter
	 *
	 * Descending is the reading order. The sanctum gets III, the crypt IV, and
	 * the two deeps levels V and VI, so a player who simply keeps going down
	 * reads the account in the order it was written. VII and VIII sit at the
	 * bottom: VII on the wall the vault door is cut through, so the reveal of
	 * who the Sentinel was lands *after* the fight rather than before it, and
	 * VIII inside the vault beside the Reliquary it gives permission to open.
	 *
	 * ## Two constraints that are not obvious
	 *
	 * Every coordinate here is a constant. `postProcess` runs once per chunk and
	 * hands each one its own random, so a position drawn from [RandomSource]
	 * would disagree with itself across a chunk border -- the mural would be
	 * placed twice, in two different blocks, or not at all. `placeBlock` already
	 * drops writes outside the chunk being built, so a fixed position is placed
	 * exactly once, by whichever chunk happens to contain it.
	 *
	 * And every mural needs stone *behind* it, not just in front. That is what
	 * ruled out the sanctum's colonnade, which is one block thick with open
	 * cavern on the far side.
	 */
	private fun setMurals(level: WorldGenLevel, box: BoundingBox) {
		val deepLower = FLOOR_PAD
		val deepUpper = FLOOR_PAD + LEVEL_HEIGHT
		val deepFar = SANCTUM_LAST - DEEP_INSET
		val mid = SANCTUM_LAST / 2

		// III -- the side wall of the stairwell, one step down from the mouth.
		//
		// Not the sanctum's own perimeter, which was the first choice and is
		// wrong: the colonnade is a *ruined* wall one block thick standing in an
		// open cavern, so a mural in it has the sanctum on one face and the
		// cavern on the other and reads as a loose slab someone propped up. The
		// stairwell is cast as a solid block of masonry before its steps are cut
		// out, so its walls have rock behind them -- and it is on the way down,
		// which the perimeter is not.
		val stairTread = SURFACE - 1 - 1
		putMural(level, box, 2, Direction.SOUTH, STAIR_HEAD - 1, stairTread + MURAL_EYE, mid - 3)

		// IV -- the crypt.
		putMural(level, box, 3, Direction.EAST, CRYPT_INSET, CRYPT_FLOOR + MURAL_EYE, mid)

		// V and VI -- the two deeps levels, on the near wall of each.
		putMural(level, box, 4, Direction.EAST, DEEP_INSET, deepUpper + MURAL_EYE, 5)
		putMural(level, box, 5, Direction.EAST, DEEP_INSET, deepLower + MURAL_EYE, 5)

		// VII -- the far wall of the lowest floor, the one the vault is cut
		// through. Clear of the vault passage, which owns z 7..9.
		putMural(level, box, 6, Direction.WEST, deepFar, deepLower + MURAL_EYE, 5)

		// VIII -- inside the vault, on the side wall beside the Reliquary.
		putMural(level, box, 7, Direction.SOUTH, VAULT_MURAL_X, deepLower + MURAL_EYE, mid - 4)
	}

	private fun putMural(
		level: WorldGenLevel,
		box: BoundingBox,
		passage: Int,
		facing: Direction,
		x: Int,
		y: Int,
		z: Int,
	) = put(
		level, box,
		ModBlocks.MURAL.defaultBlockState()
			.setValue(MuralBlock.FACING, facing)
			.setValue(MuralBlock.PASSAGE, passage),
		x, y, z,
	)

	// -------------------------------------------------------------- sentinel

	/**
	 * Stands a dormant [StoneSentinel] on the altar, watching the stairwell.
	 *
	 * An entity rather than a block that swaps for one later: the statue and the
	 * enemy are the same thing in two states, so there is no seam to hide.
	 *
	 * `postProcess` runs once per chunk the piece touches, so this would fire
	 * several times and stack sentinels on top of each other. The box check is
	 * what keeps it to one -- only the chunk actually containing the altar
	 * passes.
	 */
	private fun setSentinel(level: WorldGenLevel, box: BoundingBox) {
		val mid = SANCTUM_LAST / 2
		val at = BlockPos(
			getWorldX(MARGIN + mid, MARGIN + mid),
			getWorldY(SURFACE + 4),
			getWorldZ(MARGIN + mid, MARGIN + mid),
		)
		if (!box.isInside(at)) return

		val sentinel = ModEntities.STONE_SENTINEL.create(level.level, EntitySpawnReason.STRUCTURE) ?: return

		// Facing the stairwell it guards, which is the +X end of the sanctum.
		// Yaw runs 0 = south, so east is -90.
		sentinel.snapTo(at.x + 0.5, at.y.toDouble(), at.z + 0.5, FACING_STAIRWELL, 0.0f)
		sentinel.setWard(
			BlockPos(
				getWorldX(MARGIN + STAIR_HEAD, MARGIN + mid),
				getWorldY(SURFACE + 1),
				getWorldZ(MARGIN + STAIR_HEAD, MARGIN + mid),
			),
		)
		sentinel.setHome(at)
		sentinel.sleep()
		sentinel.setPersistenceRequired()
		level.addFreshEntity(sentinel)
	}

	// --------------------------------------------------------------- cavern

	/**
	 * Hollows a domed void around the sanctum.
	 *
	 * The roof falls away toward the rim so the chamber reads as a cavern rather
	 * than a box. Nothing below the sanctum floor is touched -- that rock is the
	 * cavern floor, and the crypt is cut into it separately.
	 */
	private fun hollowCavern(level: WorldGenLevel, box: BoundingBox) {
		for (x in 0..LAST) {
			for (z in 0..LAST) {
				val dome = domeHeight(x, z)
				if (dome < MIN_DOME) continue

				generateBox(level, box, x, SURFACE + 1, z, x, SURFACE + dome, z, AIR, AIR, false)
			}
		}
	}

	/**
	 * Height of the cavern roof above the floor at a column, or zero outside it.
	 *
	 * Deliberately a pure function of position rather than a draw from the piece
	 * random. `postProcess` runs once per chunk the piece touches, each with its
	 * own random, so a roof shaped from random draws would not agree with itself
	 * across a chunk border -- and [dressCavern] needs to ask where the roof is
	 * long after it was carved.
	 */
	private fun domeHeight(x: Int, z: Int): Int {
		val centre = LAST / 2.0
		val nx = (x - centre) / centre
		val nz = (z - centre) / centre
		val spread = sqrt(nx * nx + nz * nz)
		if (spread > 1.0) return 0

		val base = (ROOF_LIFT * (1.0 - spread * spread)).toInt()
		// Cheap positional hash: enough waviness to break the curve, identical
		// every time it is asked.
		val jitter = (((x * 73_856_093) xor (z * 19_349_663)) ushr 4) and 3
		return base + jitter - 1
	}

	/**
	 * Scatters the cavern floor with rubble, rock columns and the stumps of an
	 * outer colonnade that did not survive, so the ruin looks like the centre of
	 * something larger rather than a building dropped into a bubble.
	 */
	private fun dressCavern(level: WorldGenLevel, box: BoundingBox, random: RandomSource) {
		for (x in 0..LAST) {
			for (z in 0..LAST) {
				if (inSanctum(x, z)) continue

				val dome = domeHeight(x, z)
				if (dome < MIN_DOME) continue

				// Rock columns joining floor to roof. Only where there is real
				// height, or they read as pillars in a crawlspace.
				if (dome >= COLUMN_MIN_DOME && random.nextFloat() < COLUMN_CHANCE) {
					for (y in 1..dome) {
						placeBlock(level, native(random), x, SURFACE + y, z, box)
					}
					continue
				}

				// Stumps of the outer colonnade, in brick rather than raw stone so
				// they read as built and not as cave litter.
				if (random.nextFloat() < STUMP_CHANCE) {
					val height = 1 + random.nextInt(4)
					for (y in 1..height) {
						placeBlock(level, deepslate(random), x, SURFACE + y, z, box)
					}
					continue
				}

				if (random.nextFloat() < RUBBLE_CHANCE) {
					placeBlock(level, native(random), x, SURFACE + 1, z, box)
				} else if (random.nextFloat() < CAVERN_SCULK_CHANCE) {
					placeBlock(level, Blocks.SCULK.defaultBlockState(), x, SURFACE + 1, z, box)
				}
			}
		}
	}

	/** True for columns the sanctum itself occupies. */
	private fun inSanctum(x: Int, z: Int): Boolean =
		x >= MARGIN && x <= MARGIN + SANCTUM_LAST && z >= MARGIN && z <= MARGIN + SANCTUM_LAST

	// ---------------------------------------------------------------- deeps

	/**
	 * The rooms below the crypt.
	 *
	 * Wider than the crypt and squarer than the sanctum, so the ruin reads as
	 * opening out the further down it goes rather than tapering to a point.
	 * Each level is one shell divided into four rooms by a cross wall, which is
	 * cheap to build and gives a player something to be cornered in.
	 */
	private fun carveDeeps(level: WorldGenLevel, box: BoundingBox, random: RandomSource) {
		for (i in 0 until DEEPS_LEVELS) {
			carveDeep(level, box, random, FLOOR_PAD + i * LEVEL_HEIGHT)
		}
	}

	/**
	 * A caved-in patch of floor, in place of a second stair.
	 *
	 * There is exactly one way down from here and it is a drop. Rubble is piled
	 * underneath so the landing costs a couple of hearts rather than a real
	 * fall, and so the hole reads as something that gave way rather than
	 * something that was cut.
	 */
	private fun breakFloor(
		level: WorldGenLevel,
		box: BoundingBox,
		random: RandomSource,
		floorY: Int,
		atX: Int,
		atZ: Int,
	) {
		for (dx in -1..1) {
			for (dz in -1..1) {
				// Corners survive, so the opening is ragged instead of a neat
				// square anyone would read as a hatch.
				if (dx != 0 && dz != 0 && random.nextBoolean()) continue

				// One course above the floor as well, or the sculk that was
				// growing on it is left hanging over the hole.
				fill(level, box, atX + dx, floorY - 1, atZ + dz, atX + dx, floorY + 1, atZ + dz, AIR)
			}
		}

		val landing = floorY - LEVEL_HEIGHT + 1
		for (dx in -1..1) {
			for (dz in -1..1) {
				put(level, box, native(random), atX + dx, landing, atZ + dz)
				if (random.nextFloat() < RUBBLE_MOUND_CHANCE) {
					put(level, box, native(random), atX + dx, landing + 1, atZ + dz)
				}
			}
		}
	}

	private fun carveDeep(
		level: WorldGenLevel,
		box: BoundingBox,
		random: RandomSource,
		floorY: Int,
	) {
		val lo = DEEP_INSET
		val hi = SANCTUM_LAST - DEEP_INSET
		val roof = floorY + LEVEL_HEIGHT - 1

		// Shell first, then hollow it -- simpler than tracking wall faces, and
		// the same trick the crypt uses.
		fill(level, box, lo, floorY, lo, hi, roof, hi, deepslate(random))
		fill(level, box, lo + 1, floorY + 1, lo + 1, hi - 1, roof - 1, hi - 1, AIR)

		for (x in (lo + 1)..(hi - 1)) {
			for (z in (lo + 1)..(hi - 1)) {
				put(level, box, deepslate(random), x, floorY, z)
				if (random.nextFloat() < DEEP_SCULK_CHANCE) {
					put(level, box, Blocks.SCULK.defaultBlockState(), x, floorY + 1, z)
				}
			}
		}

		partition(level, box, random, floorY, roof, lo, hi)

		val mid = (lo + hi) / 2
		val near = (lo + mid) / 2
		val far = (mid + hi) / 2
		val rooms = arrayOf(
			intArrayOf(near, near),
			intArrayOf(near, far),
			intArrayOf(far, near),
			intArrayOf(far, far),
		)

		// Every level but the lowest gives way somewhere. The spawner goes in a
		// different room, so the drop is never the first thing that finds you.
		val holeRoom = if (floorY > FLOOR_PAD) random.nextInt(rooms.size) else -1
		if (holeRoom >= 0) {
			breakFloor(level, box, random, floorY, rooms[holeRoom][0], rooms[holeRoom][1])
		}

		var spawnerRoom = random.nextInt(rooms.size)
		if (spawnerRoom == holeRoom) spawnerRoom = (spawnerRoom + 1) % rooms.size
		sinkSpawner(level, box, rooms[spawnerRoom][0], floorY, rooms[spawnerRoom][1])

		// One lantern per level. Enough to tell there is a room, not enough to
		// see what is already in it.
		put(level, box, Blocks.SOUL_LANTERN.defaultBlockState(), lo + 1, roof - 1, lo + 1)
		put(level, box, Blocks.SOUL_LANTERN.defaultBlockState(), hi - 1, roof - 1, hi - 1)

		// Only the deepest floor gets the vault. Whatever was worth burying is
		// as far from the entrance as the ruin goes.
		if (floorY == FLOOR_PAD) buildVault(level, box, random, floorY, roof, hi)
	}

	/**
	 * The vault: a sealed room off the lowest floor with the Reliquary in it.
	 *
	 * Cut into the rock outside the chamber shell rather than partitioned off
	 * inside it. The quadrants are barely five blocks across, so a room within
	 * a room would have swallowed one whole -- and a door in an outside wall
	 * reads as somewhere deliberately sealed rather than a corner someone
	 * bricked up.
	 *
	 * Cast solid first, then carved. Facing a passage after cutting it never
	 * covers the back wall or the floor beneath, which is the same trap the
	 * sanctum stairwell hit.
	 */
	private fun buildVault(
		level: WorldGenLevel,
		box: BoundingBox,
		random: RandomSource,
		floorY: Int,
		roof: Int,
		chamberEdge: Int,
	) {
		val mid = SANCTUM_LAST / 2
		val doorLo = mid - 1
		val doorHi = mid + 1

		val outer = chamberEdge + VAULT_REACH
		fill(level, box, chamberEdge + 1, floorY, mid - 4, outer, roof, mid + 4, deepslate(random))

		// Passage through the chamber wall and along to the vault door.
		fill(level, box, chamberEdge, floorY + 1, doorLo, chamberEdge + 3, floorY + 2, doorHi, AIR)

		// The room itself.
		val inner = chamberEdge + 4
		fill(level, box, inner, floorY + 1, mid - 3, outer - 1, roof - 1, mid + 3, AIR)
		for (x in inner..(outer - 1)) {
			for (z in (mid - 3)..(mid + 3)) {
				put(level, box, tiles(random), x, floorY, z)
			}
		}

		// Reliquary against the far wall, turned to face whoever comes in.
		stockReliquary(level, box, random, outer - 2, floorY + 1, mid)

		put(level, box, Blocks.SOUL_LANTERN.defaultBlockState(), inner + 1, roof - 1, mid - 2)
		put(level, box, Blocks.SOUL_LANTERN.defaultBlockState(), inner + 1, roof - 1, mid + 2)
	}

	/** A cross wall with two doorways per arm, so all four rooms interconnect. */
	private fun partition(
		level: WorldGenLevel,
		box: BoundingBox,
		random: RandomSource,
		floorY: Int,
		roof: Int,
		lo: Int,
		hi: Int,
	) {
		val mid = (lo + hi) / 2
		val doorA = (lo + mid) / 2
		val doorB = (mid + hi) / 2

		for (i in (lo + 1)..(hi - 1)) {
			if (abs(i - doorA) <= 1 || abs(i - doorB) <= 1) continue
			for (y in (floorY + 1)..(roof - 1)) {
				put(level, box, deepslate(random), mid, y, i)
				put(level, box, deepslate(random), i, y, mid)
			}
		}
	}

	/**
	 * Sinks one spawner into the floor.
	 *
	 * Flush with the ground rather than standing on it -- a cage sitting proud
	 * of the floor announces itself from the doorway, and the point is to be
	 * halfway across the room before anything happens.
	 */
	/**
	 * Places the Reliquary and tells it what it is holding.
	 *
	 * The table is assigned rather than the contents written: the chest rolls it
	 * the first time somebody opens it, so what is inside is decided when it is
	 * found rather than when the world was made. A seed is set from the piece
	 * random so the same ruin gives the same haul however often it is reloaded.
	 */
	private fun stockReliquary(
		level: WorldGenLevel,
		box: BoundingBox,
		random: RandomSource,
		x: Int,
		y: Int,
		z: Int,
	) {
		put(
			level, box,
			ModBlocks.GOLDEN_CHEST.defaultBlockState().setValue(GoldenChestBlock.FACING, Direction.WEST),
			x, y, z,
		)

		val at = BlockPos(
			getWorldX(MARGIN + x, MARGIN + z),
			getWorldY(y),
			getWorldZ(MARGIN + x, MARGIN + z),
		)
		if (!box.isInside(at)) return

		val chest = level.getBlockEntity(at)
		if (chest is GoldenChestBlockEntity) {
			chest.setLootTable(ModLootTables.GILDED_RELIQUARY)
			chest.setLootTableSeed(random.nextLong())
		}
	}

	private fun sinkSpawner(level: WorldGenLevel, box: BoundingBox, x: Int, y: Int, z: Int) {
		put(level, box, Blocks.SPAWNER.defaultBlockState(), x, y, z)

		val at = BlockPos(
			getWorldX(MARGIN + x, MARGIN + z),
			getWorldY(y),
			getWorldZ(MARGIN + x, MARGIN + z),
		)
		// placeBlock silently drops anything outside this chunk's slice, so the
		// block entity only exists for the chunk that actually owns the spot.
		if (!box.isInside(at)) return

		val spawner = level.getBlockEntity(at)
		if (spawner is SpawnerBlockEntity) {
			spawner.setEntityId(ModEntities.REMNANT, level.random)
		}
	}

	/**
	 * Opens the way out of the crypt: a patch of its floor that gave way.
	 *
	 * The stair down from the sanctum stays a stair, because that descent is the
	 * one the sentinel is watching and a player needs to walk it. Everything
	 * past the crypt is a drop, which is less to build and a better answer to
	 * why nobody ever came back up.
	 *
	 * Kept two blocks clear of the crypt walls so it never opens under a corner
	 * lantern or into the mouth of the stair above.
	 */
	private fun breakCryptFloor(level: WorldGenLevel, box: BoundingBox, random: RandomSource) {
		val lo = CRYPT_INSET + 2
		val hi = SANCTUM_LAST - CRYPT_INSET - 2
		val span = hi - lo + 1

		breakFloor(
			level, box, random,
			CRYPT_FLOOR,
			lo + random.nextInt(span),
			lo + random.nextInt(span),
		)
	}

	// ---------------------------------------------------------------- crypt

	/** Hollows the buried chamber and lines it, sculk creeping across the floor. */
	private fun carveCrypt(level: WorldGenLevel, box: BoundingBox, random: RandomSource) {
		val lo = CRYPT_INSET
		val hi = SANCTUM_LAST - CRYPT_INSET

		// Shell first, then hollow it out -- simpler than tracking wall faces.
		fill(level, box, lo, CRYPT_FLOOR, lo, hi, SURFACE - 1, hi, deepslate(random))
		fill(level, box, lo + 1, CRYPT_FLOOR + 1, lo + 1, hi - 1, SURFACE - 1, hi - 1, AIR)

		for (x in (lo + 1)..(hi - 1)) {
			for (z in (lo + 1)..(hi - 1)) {
				// Relay the floor so it is not a flat sheet of one texture.
				put(level, box, deepslate(random), x, CRYPT_FLOOR, z)
				if (random.nextFloat() < SCULK_CHANCE) {
					put(level, box, Blocks.SCULK.defaultBlockState(), x, CRYPT_FLOOR + 1, z)
				}
			}
		}

		// Four lanterns at the chamber corners: the only light down here.
		for (x in intArrayOf(lo + 1, hi - 1)) {
			for (z in intArrayOf(lo + 1, hi - 1)) {
				put(level, box, Blocks.SOUL_LANTERN.defaultBlockState(), x, CRYPT_FLOOR + 1, z)
			}
		}
	}

	// ------------------------------------------------------------- platform

	/** The sanctum floor, which doubles as the crypt ceiling. */
	private fun layPlatform(level: WorldGenLevel, box: BoundingBox, random: RandomSource) {
		for (x in 0..SANCTUM_LAST) {
			for (z in 0..SANCTUM_LAST) {
				put(level, box, deepslate(random), x, SURFACE, z)
			}
		}
	}

	// ------------------------------------------------------------ colonnade

	/** Pillars at the corners and cardinal midpoints, plus the broken wall between. */
	private fun raiseColonnade(level: WorldGenLevel, box: BoundingBox, random: RandomSource) {
		for (i in 0..SANCTUM_LAST) {
			ruinedWall(level, box, random, i, 0)
			ruinedWall(level, box, random, i, SANCTUM_LAST)
			ruinedWall(level, box, random, 0, i)
			ruinedWall(level, box, random, SANCTUM_LAST, i)
		}

		val mid = SANCTUM_LAST / 2
		val inner = SANCTUM_LAST - 1

		// Corners are 2x2 shafts. A one-block column at this height reads as a
		// fence post, not a pillar.
		for ((cx, cz) in listOf(0 to 0, 0 to inner, inner to 0, inner to inner)) {
			val top = PILLAR_TALL - random.nextInt(3)
			for (dx in 0..1) {
				for (dz in 0..1) {
					for (y in 1..top) {
						put(level, box, deepslate(random), cx + dx, SURFACE + y, cz + dz)
					}
					put(
						level, box, Blocks.CHISELED_DEEPSLATE.defaultBlockState(),
						cx + dx, SURFACE + top + 1, cz + dz,
					)
				}
			}
		}

		for ((x, z) in listOf(mid to 0, mid to SANCTUM_LAST, 0 to mid, SANCTUM_LAST to mid)) {
			val top = PILLAR_SHORT - random.nextInt(3)
			for (y in 1..top) {
				put(level, box, deepslate(random), x, SURFACE + y, z)
			}
			put(level, box, Blocks.CHISELED_DEEPSLATE.defaultBlockState(), x, SURFACE + top + 1, z)
		}
	}

	/**
	 * One wall column. The bottom [SOLID_COURSES] are never skipped, so the
	 * perimeter reads as a collapsed wall rather than a row of loose stones.
	 */
	private fun ruinedWall(
		level: WorldGenLevel,
		box: BoundingBox,
		random: RandomSource,
		x: Int,
		z: Int,
	) {
		for (y in 1..SOLID_COURSES) {
			put(level, box, deepslate(random), x, SURFACE + y, z)
		}

		if (random.nextFloat() < GAP_CHANCE) return

		val height = SOLID_COURSES + 1 + random.nextInt(WALL_MAX)
		for (y in (SOLID_COURSES + 1)..height) {
			put(level, box, deepslate(random), x, SURFACE + y, z)
		}
	}

	// ---------------------------------------------------------------- altar

	/** Stepped plinth at the centre -- where the memory will eventually rest. */
	private fun buildAltar(level: WorldGenLevel, box: BoundingBox, random: RandomSource) {
		val mid = SANCTUM_LAST / 2

		for (x in (mid - 2)..(mid + 2)) {
			for (z in (mid - 2)..(mid + 2)) {
				put(level, box, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), x, SURFACE + 1, z)
			}
		}
		for (x in (mid - 1)..(mid + 1)) {
			for (z in (mid - 1)..(mid + 1)) {
				put(level, box, tiles(random), x, SURFACE + 2, z)
			}
		}
		put(level, box, Blocks.CHISELED_DEEPSLATE.defaultBlockState(), mid, SURFACE + 3, mid)

		for (x in intArrayOf(mid - 2, mid + 2)) {
			for (z in intArrayOf(mid - 2, mid + 2)) {
				put(level, box, Blocks.SOUL_LANTERN.defaultBlockState(), x, SURFACE + 2, z)
			}
		}
	}

	/**
	 * Soul campfires on low plinths. They light the cavern blue, throw a particle
	 * column, and burn anything that walks into them -- the ruin's only standing
	 * defence until the guardians arrive.
	 */
	private fun placeBraziers(level: WorldGenLevel, box: BoundingBox) {
		val near = CRYPT_INSET
		val far = SANCTUM_LAST - CRYPT_INSET

		for (x in intArrayOf(near, far)) {
			for (z in intArrayOf(near, far)) {
				put(level, box, Blocks.POLISHED_DEEPSLATE.defaultBlockState(), x, SURFACE + 1, z)
				put(level, box, Blocks.SOUL_CAMPFIRE.defaultBlockState(), x, SURFACE + 2, z)
			}
		}
	}

	// ------------------------------------------------------------ stairwell

	/**
	 * Sinks the descent into the crypt as a stairwell mouth in the sanctum floor.
	 *
	 * The buried run is cast as one solid block of masonry and the steps cut out
	 * of it: facing a shaft after cutting it never covers everything, because the
	 * back wall and the ground below the treads stay bare.
	 *
	 * Run last -- it deliberately overwrites the platform laid above it.
	 */
	private fun cutStairwell(level: WorldGenLevel, box: BoundingBox, random: RandomSource) {
		val mid = SANCTUM_LAST / 2
		val zLo = mid - 2
		val zHi = mid + 2
		val headX = SANCTUM_LAST - 1
		val descent = SURFACE - CRYPT_FLOOR - 1

		fill(
			level, box,
			CRYPT_OUTER + 1, CRYPT_FLOOR, zLo - 1,
			headX + 1, SURFACE, zHi + 1,
			deepslate(random),
		)

		for (step in 0..descent) {
			val x = headX - step
			val treadY = SURFACE - 1 - step
			val buried = x > CRYPT_OUTER

			// Outside the crypt the shaft is cut up through the platform to open
			// the mouth. Once inside, the chamber is already hollow and the
			// platform is its ceiling -- carrying the cut on would punch a hole in
			// the sanctum floor and open the crypt to the cavern.
			val ceiling = if (buried) SURFACE else SURFACE - 1
			fill(level, box, x, treadY + 1, zLo, x, ceiling, zHi, AIR)

			for (z in zLo..zHi) {
				put(level, box, deepslate(random), x, treadY, z)
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

	/** Unworked stone, for the cavern itself rather than the ruin standing in it. */
	private fun native(random: RandomSource): BlockState = when (random.nextInt(8)) {
		0, 1 -> Blocks.COBBLED_DEEPSLATE
		2 -> Blocks.TUFF
		3 -> Blocks.POLISHED_DEEPSLATE
		else -> Blocks.DEEPSLATE
	}.defaultBlockState()

	private fun tiles(random: RandomSource): BlockState =
		if (random.nextBoolean()) {
			Blocks.DEEPSLATE_TILES.defaultBlockState()
		} else {
			Blocks.CRACKED_DEEPSLATE_TILES.defaultBlockState()
		}

	// ----------------------------------------------------- sanctum-relative

	/** Places a block in sanctum coordinates. */
	private fun put(
		level: WorldGenLevel,
		box: BoundingBox,
		state: BlockState,
		x: Int,
		y: Int,
		z: Int,
	) = placeBlock(level, state, MARGIN + x, y, MARGIN + z, box)

	/** Fills a solid box in sanctum coordinates. */
	private fun fill(
		level: WorldGenLevel,
		box: BoundingBox,
		x1: Int, y1: Int, z1: Int,
		x2: Int, y2: Int, z2: Int,
		state: BlockState,
	) = generateBox(
		level, box,
		MARGIN + x1, y1, MARGIN + z1,
		MARGIN + x2, y2, MARGIN + z2,
		state, state, false,
	)

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

		/** Footprint of the ruin itself. */
		const val SANCTUM_WIDTH: Int = 17

		/** Rock left between the ruin and the cavern rim, on each side. */
		private const val MARGIN = 9

		/** Full cavern footprint. */
		const val WIDTH: Int = SANCTUM_WIDTH + 2 * MARGIN

		private const val SANCTUM_LAST = SANCTUM_WIDTH - 1
		private const val LAST = WIDTH - 1

		/** Solid rock kept beneath the lowest floor. */
		private const val FLOOR_PAD = 2

		/** Depth of the crypt below the sanctum floor. */
		const val CRYPT_DEPTH: Int = 6

		/** Levels of rooms below the crypt, and the floor-to-floor drop of each. */
		private const val DEEPS_LEVELS = 2
		private const val LEVEL_HEIGHT = 7
		private const val DEEPS = DEEPS_LEVELS * LEVEL_HEIGHT

		private const val CRYPT_FLOOR = DEEPS + FLOOR_PAD

		/** Local Y of the sanctum floor. Crypt below, cavern above. */
		private const val SURFACE = CRYPT_FLOOR + CRYPT_DEPTH

		/**
		 * How far the piece reaches below the sanctum floor.
		 *
		 * Read by [GuardedRuinsStructure] for its bedrock check. Deriving both
		 * from the same constant is what keeps the depth band honest when the
		 * deeps grow: everything below is expressed relative to [SURFACE], so the
		 * box floor drops on its own and the sanctum stays at the same world Y.
		 */
		const val DEPTH: Int = SURFACE

		/** Headroom the cavern dome reaches at its centre. */
		private const val ROOF_LIFT = 26

		/** Below this the dome is too low to be worth carving at all. */
		private const val MIN_DOME = 3

		/** Total height of the piece above the sanctum floor. */
		const val ABOVE: Int = ROOF_LIFT + 2

		/** Inset of the crypt shell from the sanctum edge. */
		private const val CRYPT_INSET = 4

		/** The deeps sit wider than the crypt, so the ruin opens out as it drops. */
		private const val DEEP_INSET = 2

		private const val DEEP_SCULK_CHANCE = 0.10f

		/**
		 * How far past the chamber wall the vault reaches.
		 *
		 * Nine blocks of margin sit between the sanctum and the cavern rim, and
		 * all of it is solid below the sanctum floor, so there is room to cut
		 * into -- but not much. Ten keeps the far wall inside the bounding box.
		 */
		private const val VAULT_REACH = 10

		/** How much of the debris under a collapsed floor stands two blocks high. */
		private const val RUBBLE_MOUND_CHANCE = 0.45f

		/** Sanctum X of the crypt's far shell wall -- the boundary the stair pierces. */
		private const val CRYPT_OUTER = SANCTUM_LAST - CRYPT_INSET

		/** Sanctum X of the stairwell mouth, which is what the sentinel watches. */
		private const val STAIR_HEAD = SANCTUM_LAST - 1

		/** Yaw for east, the direction the stairwell lies in. */
		private const val FACING_STAIRWELL = -90.0f

		/**
		 * Height of a mural above the floor it is read from.
		 *
		 * Shared with the graveyard's own two, so every mural in the mod sits at
		 * the same height off its floor.
		 */
		private const val MURAL_EYE = GraveyardPiece.MURAL_EYE

		/**
		 * Sanctum X of the vault's mural, between the vault door and its far
		 * wall. The vault room runs from `chamberEdge + 4` to `chamberEdge +
		 * VAULT_REACH - 1`, which is 18 to 23, so this is the middle of the
		 * side wall and not behind the Reliquary standing at 22.
		 */
		private const val VAULT_MURAL_X = 20

		private const val GAP_CHANCE = 0.40f
		private const val SCULK_CHANCE = 0.18f

		/** Cavern dressing. Sparse on purpose -- clutter reads as noise, not scale. */
		private const val COLUMN_CHANCE = 0.012f
		private const val COLUMN_MIN_DOME = 8
		private const val STUMP_CHANCE = 0.020f
		private const val RUBBLE_CHANCE = 0.10f
		private const val CAVERN_SCULK_CHANCE = 0.06f
		private const val WALL_MAX = 4
		private const val SOLID_COURSES = 2
		private const val PILLAR_TALL = 10
		private const val PILLAR_SHORT = 6

		private fun boxAround(centre: BlockPos): BoundingBox {
			val half = WIDTH / 2
			return BoundingBox(
				centre.x - half,
				centre.y - SURFACE,
				centre.z - half,
				centre.x - half + LAST,
				centre.y + ABOVE,
				centre.z - half + LAST,
			)
		}
	}
}
