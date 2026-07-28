package io.github.freshglitch.vanguardspirits.client.datagen

import io.github.freshglitch.vanguardspirits.registry.ModItems
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.minecraft.client.data.models.BlockModelGenerators
import net.minecraft.client.data.models.ItemModelGenerators
import net.minecraft.client.data.models.model.ModelTemplates

/** Every item so far is a flat sprite, so this stays a one-liner per item. */
class ModModelProvider(output: FabricPackOutput) : FabricModelProvider(output) {

	override fun generateBlockStateModels(generators: BlockModelGenerators) = Unit

	override fun generateItemModels(generators: ItemModelGenerators) {
		ModItems.ALL.forEach { generators.generateFlatItem(it, ModelTemplates.FLAT_ITEM) }
	}
}
