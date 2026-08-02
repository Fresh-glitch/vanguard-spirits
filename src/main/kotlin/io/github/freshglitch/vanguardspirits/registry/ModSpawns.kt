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
 * Only the Mourner. Sentinels and Remnants are placed by the structure and have
 * no business appearing anywhere else, and giving them natural spawns would
 * quietly turn the mod's set pieces into ordinary wildlife.
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
}
