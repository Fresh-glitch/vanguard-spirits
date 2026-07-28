package io.github.freshglitch.vanguardspirits.client

import io.github.freshglitch.vanguardspirits.client.datagen.ModEnglishProvider
import io.github.freshglitch.vanguardspirits.client.datagen.ModModelProvider
import io.github.freshglitch.vanguardspirits.client.datagen.ModRecipeProvider
import io.github.freshglitch.vanguardspirits.client.datagen.ModWorldgenBootstrap
import io.github.freshglitch.vanguardspirits.client.datagen.ModWorldgenProvider
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator
import net.minecraft.core.RegistrySetBuilder
import net.minecraft.core.registries.Registries

object VanguardSpiritsDataGenerator : DataGeneratorEntrypoint {
	override fun onInitializeDataGenerator(fabricDataGenerator: FabricDataGenerator) {
		val pack = fabricDataGenerator.createPack()

		pack.addProvider(::ModModelProvider)
		pack.addProvider(::ModEnglishProvider)
		pack.addProvider(::ModRecipeProvider)
		pack.addProvider(::ModWorldgenProvider)
	}

	/**
	 * Worldgen objects live in dynamic registries, so they have to be built here
	 * before any provider can serialise them.
	 */
	override fun buildRegistry(registryBuilder: RegistrySetBuilder) {
		registryBuilder.add(Registries.STRUCTURE, ModWorldgenBootstrap::structures)
		registryBuilder.add(Registries.STRUCTURE_SET, ModWorldgenBootstrap::structureSets)
	}
}
