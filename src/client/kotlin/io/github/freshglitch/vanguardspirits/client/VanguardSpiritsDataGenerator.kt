package io.github.freshglitch.vanguardspirits.client

import io.github.freshglitch.vanguardspirits.client.datagen.ModEnglishProvider
import io.github.freshglitch.vanguardspirits.client.datagen.ModModelProvider
import io.github.freshglitch.vanguardspirits.client.datagen.ModRecipeProvider
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator

object VanguardSpiritsDataGenerator : DataGeneratorEntrypoint {
	override fun onInitializeDataGenerator(fabricDataGenerator: FabricDataGenerator) {
		val pack = fabricDataGenerator.createPack()

		pack.addProvider(::ModModelProvider)
		pack.addProvider(::ModEnglishProvider)
		pack.addProvider(::ModRecipeProvider)
	}
}
