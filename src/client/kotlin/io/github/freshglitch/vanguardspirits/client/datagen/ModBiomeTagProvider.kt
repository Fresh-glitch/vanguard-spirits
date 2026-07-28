package io.github.freshglitch.vanguardspirits.client.datagen

import io.github.freshglitch.vanguardspirits.registry.ModTags
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.biome.Biomes
import java.util.concurrent.CompletableFuture

/**
 * Restricts Guarded Ruins to dry land.
 *
 * Oceans, deep oceans, rivers and beaches are deliberately absent: these are
 * land ruins, and drowned ones are both wrong for the theme and awkward to loot.
 */
class ModBiomeTagProvider(
	output: FabricPackOutput,
	registryLookup: CompletableFuture<HolderLookup.Provider>,
) : FabricTagsProvider<Biome>(output, Registries.BIOME, registryLookup) {

	override fun addTags(registries: HolderLookup.Provider) {
		builder(ModTags.GUARDED_RUINS_BIOMES)
			// Whole categories that are dry by definition.
			//
			// These must be *optional* references: addTag() validates that the
			// target is defined by this provider, and vanilla's tags are not, so
			// the strict form fails datagen outright.
			.addOptionalTag(BiomeTags.IS_FOREST)
			.addOptionalTag(BiomeTags.IS_TAIGA)
			.addOptionalTag(BiomeTags.IS_JUNGLE)
			.addOptionalTag(BiomeTags.IS_SAVANNA)
			.addOptionalTag(BiomeTags.IS_BADLANDS)
			.addOptionalTag(BiomeTags.IS_HILL)
			.addOptionalTag(BiomeTags.IS_MOUNTAIN)
			// Open biomes that belong to no category tag.
			.add(Biomes.PLAINS)
			.add(Biomes.SUNFLOWER_PLAINS)
			.add(Biomes.SNOWY_PLAINS)
			.add(Biomes.DESERT)
			.add(Biomes.SWAMP)
			.add(Biomes.MEADOW)
			.add(Biomes.GROVE)
			.add(Biomes.SNOWY_SLOPES)
			.add(Biomes.CHERRY_GROVE)
			.add(Biomes.STONY_PEAKS)
	}
}
