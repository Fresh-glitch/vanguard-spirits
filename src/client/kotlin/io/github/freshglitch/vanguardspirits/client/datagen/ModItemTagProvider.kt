package io.github.freshglitch.vanguardspirits.client.datagen

import io.github.freshglitch.vanguardspirits.registry.ModItems
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalItemTags
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.world.item.Item
import java.util.concurrent.CompletableFuture

/**
 * Makes a Mourner's Feather count as a feather.
 *
 * Vanilla has no feather tag at all -- `arrow`, `brush`, `writable_book` and
 * `firework_star` each name `minecraft:feather` outright -- so this tag on its
 * own changes nothing. It is half of the job; the other half is the four recipe
 * overrides under `resources/data/minecraft/recipe/`, which repoint those
 * ingredients at `c:feathers`.
 *
 * `c:feathers` rather than a tag of our own because Fabric's
 * `fabric-convention-tags-v2` already ships it holding `minecraft:feather`, and
 * datapack tags merge -- so adding to it yields both feathers without our having
 * to restate vanilla's. That module arrives with `fabric-api`, which this mod
 * depends on outright, so the tag is never missing at runtime. Worth being sure
 * of: had it been empty, the overrides would have left arrows uncraftable from
 * an ordinary feather.
 *
 * Note the substitution only runs one way. The Wanderer charm names the
 * Mourner's Feather directly, so a chicken feather will not do for it.
 */
class ModItemTagProvider(
	output: FabricPackOutput,
	registryLookup: CompletableFuture<HolderLookup.Provider>,
) : FabricTagsProvider<Item>(output, Registries.ITEM, registryLookup) {

	override fun addTags(registries: HolderLookup.Provider) {
		// Looked up from the registry rather than rebuilt from the path string,
		// so renaming the item cannot quietly leave this pointing at nothing.
		builder(ConventionalItemTags.FEATHERS)
			.add(BuiltInRegistries.ITEM.getResourceKey(ModItems.MOURNER_FEATHER).orElseThrow())
	}
}
