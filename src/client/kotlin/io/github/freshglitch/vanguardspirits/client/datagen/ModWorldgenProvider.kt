package io.github.freshglitch.vanguardspirits.client.datagen

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import java.util.concurrent.CompletableFuture

/**
 * Writes the dynamic-registry entries built by [ModWorldgenBootstrap] out to JSON.
 */
class ModWorldgenProvider(
	output: FabricPackOutput,
	registryLookup: CompletableFuture<HolderLookup.Provider>,
) : FabricDynamicRegistryProvider(output, registryLookup) {

	override fun configure(registries: HolderLookup.Provider, entries: Entries) {
		entries.addAll(registries.lookupOrThrow(Registries.STRUCTURE))
		entries.addAll(registries.lookupOrThrow(Registries.STRUCTURE_SET))
	}

	override fun getName(): String = "Vanguard Spirits Worldgen"
}
