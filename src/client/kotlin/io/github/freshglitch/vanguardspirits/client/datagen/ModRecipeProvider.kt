package io.github.freshglitch.vanguardspirits.client.datagen

import io.github.freshglitch.vanguardspirits.registry.ModBlocks
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
			//
			// The Wanderer's used to be a plain vanilla feather, which made it
			// the one charm whose core cost nothing next to a rabbit's foot, a
			// golden carrot and a shield. A Mourner's feather is the same idea
			// and an actual errand -- and it is an errand that sends the player
			// to a ruin rather than to a chicken.
			charm(ModItems.CHARM_OF_THE_LEAPER, Items.RABBIT_FOOT)
			charm(ModItems.CHARM_OF_THE_WANDERER, ModItems.MOURNER_FEATHER)
			charm(ModItems.CHARM_OF_THE_DELVER, Items.GOLDEN_CARROT)
			charm(ModItems.CHARM_OF_THE_RETURNED, Items.SHIELD)

			// The altar itself. Deepslate walls around a memory, because it is a
			// piece of ruin masonry with one of the things it was built to hold
			// set into it.
			//
			// Craftable rather than only found, so a player who has read the
			// fourth passage can build the station at home instead of walking
			// back down a ruin every time they can afford a binding. One
			// generates in each sanctum anyway, which is where they meet it.
			shaped(RecipeCategory.MISC, ModBlocks.BINDING_ALTAR)
				.define('B', Items.DEEPSLATE_BRICKS)
				.define('M', ModItems.FRACTURED_MEMORY)
				.pattern("BBB")
				.pattern("BMB")
				.pattern("BBB")
				.unlockedBy("has_fractured_memory", has(ModItems.FRACTURED_MEMORY))
				.save(recipeOutput)
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
