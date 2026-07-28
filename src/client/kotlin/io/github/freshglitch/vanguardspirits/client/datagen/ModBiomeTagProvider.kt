package io.github.freshglitch.vanguardspirits.client.datagen

import io.github.freshglitch.vanguardspirits.registry.ModTags
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.biome.Biome
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
		// The ruins sit deep in the deepslate layer now, so what is on the surface
		// above them no longer matters -- the dry-land restriction that kept them
		// out of oceans is obsolete. Kept as our own tag rather than referencing
		// the vanilla one directly so packs still have a single place to retune.
		//
		// The reference must be *optional*: addTag() validates that the target is
		// defined by this provider, and vanilla's tags are not, so the strict form
		// fails datagen outright.
		builder(ModTags.GUARDED_RUINS_BIOMES)
			.addOptionalTag(BiomeTags.IS_OVERWORLD)
	}
}
