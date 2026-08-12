package io.github.freshglitch.vanguardspirits.registry

import net.fabricmc.fabric.api.biome.v1.BiomeModifications
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.entity.SpawnPlacementTypes
import net.minecraft.world.entity.SpawnPlacements
import net.minecraft.world.level.levelgen.Heightmap

/**
 * Where the world puts our mobs without being asked.
 *
 * The Mourner and the Nymph. Sentinels and Remnants are placed by the structure
 * and have no business appearing anywhere else, and giving them natural spawns
 * would quietly turn the mod's set pieces into ordinary wildlife.
 */
object ModSpawns {

	/**
	 * A Mourner in the woods, very occasionally.
	 *
	 * The ones over a ruin are a landmark and mean something. This is the other
	 * kind: a bird that is just a bird, so that coming across one is not proof of
	 * anything and a player cannot read every Mourner as treasure. Which is only
	 * worth having if it stays rare enough to be a surprise -- hence a weight of
	 * one *and* [WILD_ROLL] on top, because weight alone is relative to whatever
	 * else shares the category and would not stay rare if that changed.
	 */
	fun register() {
		SpawnPlacements.register(
			ModEntities.MOURNER,
			SpawnPlacementTypes.ON_GROUND,
			Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
		) { _, level, reason, pos, random ->
			// Spawn eggs, commands and anything the structure does are deliberate
			// and answer to nobody. Only the world's own attempts are rationed.
			if (reason != EntitySpawnReason.NATURAL) {
				true
			} else {
				random.nextFloat() < WILD_ROLL &&
					level.getBlockState(pos.below()).`is`(BlockTags.ANIMALS_SPAWNABLE_ON) &&
					level.getRawBrightness(pos, 0) > MIN_LIGHT
			}
		}

		BiomeModifications.addSpawn(
			BiomeSelectors.tag(ModTags.MOURNER_BIOMES),
			MobCategory.AMBIENT,
			ModEntities.MOURNER,
			WILD_WEIGHT,
			WILD_GROUP,
			WILD_GROUP,
		)

		registerNymph()
	}

	/**
	 * A Nymph, almost never.
	 *
	 * Rarer than the Mourner by a factor of four, and that is the headline
	 * feature rather than a tuning choice: everything she is built to do -- the
	 * conversation, the grievance, the making of amends -- only lands if meeting
	 * one is an event. A player who runs into three of them in an afternoon has
	 * met a mob; a player who finds one in a season has met somebody.
	 *
	 * Note she never despawns once found (`Nymph.removeWhenFarAway`), so the roll
	 * has to be this punishing. Each success is permanent.
	 */
	private fun registerNymph() {
		SpawnPlacements.register(
			ModEntities.NYMPH,
			SpawnPlacementTypes.ON_GROUND,
			Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
		) { _, level, reason, pos, random ->
			// **Both** reasons, and the second one is the important one.
			//
			// `CHUNK_GENERATION` is a separate value from `NATURAL`, and it is
			// the one a `MobCategory.CREATURE` mob almost always arrives by --
			// vanilla populates passive mobs when a chunk is first generated,
			// not on the running spawn cycle. Rationing only `NATURAL`, as the
			// Mourner does, therefore let every roll through: the rarest mob in
			// the mod was gated by its biome weight alone.
			//
			// It took logging the reason to see it. A Nymph in a flower forest
			// is a Nymph in a flower forest however she got there, and the
			// number that was supposed to make her rare was never consulted.
			//
			// The Mourner is correct as written, for what it is worth: it is
			// AMBIENT, and only creatures are placed at chunk generation.
			val rationed = reason == EntitySpawnReason.NATURAL ||
				reason == EntitySpawnReason.CHUNK_GENERATION

			if (!rationed) {
				// Spawn eggs and commands are deliberate and answer to nobody.
				true
			} else {
				// Standing in the flowers, not on the stone beside them. The
				// ground check is what stops her generating on a cliff face or a
				// gravel bank inside an otherwise qualifying biome.
				random.nextFloat() < NYMPH_ROLL &&
					level.getBlockState(pos.below()).`is`(BlockTags.ANIMALS_SPAWNABLE_ON) &&
					level.getRawBrightness(pos, 0) > MIN_LIGHT
			}
		}

		BiomeModifications.addSpawn(
			BiomeSelectors.tag(ModTags.NYMPH_BIOMES),
			MobCategory.CREATURE,
			ModEntities.NYMPH,
			NYMPH_WEIGHT,
			WILD_GROUP,
			WILD_GROUP,
		)
	}

	/**
	 * One in five hundred attempts that clear every other test.
	 *
	 * Deliberately punishing. A player should be able to spend a long time in
	 * oak woodland and never meet one.
	 */
	private const val WILD_ROLL = 0.002f

	/** Lowest weight the category accepts, on top of the roll above. */
	private const val WILD_WEIGHT = 1

	/** Always alone. A flock would read as a ruin marker, which it is not. */
	private const val WILD_GROUP = 1

	/** Daylight only. It hunts by sight. */
	private const val MIN_LIGHT = 8

	/**
	 * Uncommon, not unheard of -- and the two numbers below multiply.
	 *
	 * The flower forest's own creature pool totals **44**: sheep 12, pig 10,
	 * chicken 10, cow 8, rabbit 4. A weight of 2 against that is 2/46, or 4.3%
	 * of the groups the biome places, and the roll halves it again to about
	 * **2.2% of creature spawn attempts** -- roughly one Nymph per fifty
	 * groups of animals.
	 *
	 * That is a real change of intent rather than a tuning nudge. The figure
	 * used to be one in two thousand, chosen when the roll was never consulted
	 * at all (see [registerNymph] and `EntitySpawnReason.CHUNK_GENERATION`);
	 * applied properly, that number would have made her effectively
	 * unfindable. The biome is doing most of the work here, because a flower
	 * forest is uncommon to begin with, so what is left to do is stop her being
	 * the first thing standing in one.
	 *
	 * Both dials are kept because they fail differently. The weight is relative
	 * to whatever else shares the CREATURE category, so it drifts if another
	 * mod adds animals to this biome; the roll is absolute and does not.
	 */
	private const val NYMPH_ROLL = 0.5f

	/** One above the floor. With the roll, about 2.2% of creature attempts. */
	private const val NYMPH_WEIGHT = 2
}
