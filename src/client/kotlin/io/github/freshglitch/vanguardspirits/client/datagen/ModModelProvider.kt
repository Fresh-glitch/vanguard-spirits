package io.github.freshglitch.vanguardspirits.client.datagen

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.item.CharmItem
import io.github.freshglitch.vanguardspirits.registry.ModItems
import net.fabricmc.fabric.api.client.datagen.v1.provider.FabricModelProvider
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.minecraft.client.data.models.BlockModelGenerators
import net.minecraft.client.data.models.ItemModelGenerators
import net.minecraft.client.data.models.model.ModelTemplates
import net.minecraft.client.renderer.item.CompositeModel
import net.minecraft.client.renderer.item.CuboidItemModelWrapper
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import java.util.Optional

/** Every item is a flat sprite, so this stays a one-liner per item -- bar the charms. */
class ModModelProvider(output: FabricPackOutput) : FabricModelProvider(output) {

	override fun generateBlockStateModels(generators: BlockModelGenerators) = Unit

	override fun generateItemModels(generators: ItemModelGenerators) {
		ModItems.ALL.forEach { item ->
			if (burns(item)) glowing(generators, item)
			else generators.generateFlatItem(item, ModelTemplates.FLAT_ITEM)
		}
	}

	/**
	 * Whether this item has a part of itself that stays lit in the dark.
	 *
	 * The charms' runes, and the Fractured Memory's break -- the crack only, not
	 * the stone it runs through. The Echo of Kinship burns whole, which is the
	 * cheaper case: its glowing half wears the item's own texture rather than a
	 * sheet cut for it, so there is one animation instead of two to keep in step.
	 *
	 * Hand-kept against the generators that draw the matching sheets
	 * (`tools/make_charm_textures.py` and `tools/make_fractured_memory.py`), and
	 * safe to keep that way because the two cannot disagree quietly: name an item
	 * here with no `_glow` model written for it and the game logs a failure to
	 * load it on every start.
	 */
	private fun burns(item: Item): Boolean =
		item is CharmItem ||
			item === ModItems.FRACTURED_MEMORY ||
			item === ModItems.ECHO_OF_KINSHIP

	/**
	 * Two models stacked: the item, and the lit part of it burning over the top.
	 *
	 * ## Why this is data and not a renderer
	 *
	 * A model element may carry `light_emission`; `FaceBakery` bakes it into the
	 * quad's `MaterialInfo`, and `VertexConsumer.putBakedQuad` -- the *pose*
	 * taking one, which is what item and entity rendering both end at -- reads it
	 * back when the quad is drawn. So marking the glyph emissive once lights it
	 * everywhere a charm can appear: in the hand, dropped on the ground, in a
	 * frame, and lying on a Binding Altar. The altar had its own overlay for a
	 * while and no longer needs it.
	 *
	 * The glyph has to be a **second model** rather than a second texture layer,
	 * because `light_emission` belongs to an element and `item/generated` builds
	 * its elements itself from the sprite -- there is nowhere to hang it. The
	 * emissive half is therefore hand-written by `tools/make_charm_textures.py`,
	 * which cuts its texture from the very pixels it draws into the plate, and
	 * carries a copy of vanilla's own `item/generated` display transforms so the
	 * two halves move together.
	 */
	private fun glowing(generators: ItemModelGenerators, item: Item) {
		val plain = generators.createFlatItemModel(item, ModelTemplates.FLAT_ITEM)
		val glow = VanguardSpirits.id("item/${pathOf(item)}_glow")

		generators.itemModelOutput.accept(
			item,
			CompositeModel.Unbaked(
				listOf(
					CuboidItemModelWrapper.Unbaked(plain, Optional.empty(), listOf()),
					CuboidItemModelWrapper.Unbaked(glow, Optional.empty(), listOf()),
				),
				Optional.empty(),
			),
		)
	}

	private fun pathOf(item: Item): String = BuiltInRegistries.ITEM.getKey(item).path
}
