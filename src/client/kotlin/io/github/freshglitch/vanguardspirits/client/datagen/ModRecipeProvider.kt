package io.github.freshglitch.vanguardspirits.client.datagen

import io.github.freshglitch.vanguardspirits.registry.ModItems
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider
import net.minecraft.core.HolderLookup
import net.minecraft.data.recipes.RecipeCategory
import net.minecraft.data.recipes.RecipeOutput
import net.minecraft.data.recipes.RecipeProvider
import net.minecraft.world.item.Items
import net.minecraft.world.level.ItemLike
import java.util.concurrent.CompletableFuture

class ModRecipeProvider(
	output: FabricPackOutput,
	registryLookup: CompletableFuture<HolderLookup.Provider>,
) : FabricRecipeProvider(output, registryLookup) {

	override fun createRecipeProvider(
		lookup: HolderLookup.Provider,
		recipeOutput: RecipeOutput,
	): RecipeProvider = object : RecipeProvider(lookup, recipeOutput) {

		override fun buildRecipes() {
			// Four memories ringing a core that matches the spirit being bound.
			charm(ModItems.CHARM_OF_THE_LEAPER, Items.RABBIT_FOOT)
			charm(ModItems.CHARM_OF_THE_WANDERER, Items.FEATHER)
			charm(ModItems.CHARM_OF_THE_DELVER, Items.GOLDEN_CARROT)
			charm(ModItems.CHARM_OF_THE_RETURNED, Items.SHIELD)
		}

		/**
		 * Memories in a cross, ruin masonry at the corners, and the core between.
		 *
		 * The deepslate is what the ruins are built from, so a charm now costs a
		 * piece of the place it came out of rather than the memories alone.
		 */
		private fun charm(result: ItemLike, core: ItemLike) {
			shaped(RecipeCategory.MISC, result)
				.define('M', ModItems.FRACTURED_MEMORY)
				.define('B', Items.DEEPSLATE_BRICKS)
				.define('C', core)
				.pattern("BMB")
				.pattern("MCM")
				.pattern("BMB")
				.unlockedBy("has_fractured_memory", has(ModItems.FRACTURED_MEMORY))
				.save(recipeOutput)
		}
	}

	override fun getName(): String = "Vanguard Spirits Recipes"
}
