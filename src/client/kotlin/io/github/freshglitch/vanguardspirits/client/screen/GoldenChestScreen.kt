package io.github.freshglitch.vanguardspirits.client.screen

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.menu.GoldenChestMenu
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

/**
 * The Reliquary's container screen.
 *
 * Only the backdrop differs from a vanilla container -- the slot positions come
 * from [GoldenChestMenu] and are laid out on vanilla's grid, so the panel art
 * and the interactive slots agree.
 */
class GoldenChestScreen(
	menu: GoldenChestMenu,
	inventory: Inventory,
	title: Component,
) : AbstractContainerScreen<GoldenChestMenu>(menu, inventory, title) {

	override fun init() {
		super.init()

		// Centre the title. Left-aligned it runs straight over the corner boss,
		// and centred suits the symmetry of the panel anyway.
		titleLabelX = (imageWidth - font.width(title)) / 2

		// Two pixels higher than vanilla, to clear the divider rule beneath it.
		inventoryLabelY = imageHeight - 96
	}

	/**
	 * The panel's own titles, in colours that can be read on it.
	 *
	 * Vanilla draws both labels at `0xFF404040` with no shadow, which suits its
	 * pale parchment and is very nearly the background on this mod's dark stone.
	 * The Reliquary had shipped that way since it was added and nobody noticed,
	 * because you can guess what the title of a chest you just opened says.
	 *
	 * The gold is sampled from the panel art rather than picked: `#E6CB7C` is
	 * the light tone of its own inlay, so the title belongs to the frame around
	 * it instead of being a fourth yellow.
	 */
	override fun extractLabels(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		extractor.text(font, title, titleLabelX, titleLabelY, TITLE_COLOUR, true)
		extractor.text(
			font,
			playerInventoryTitle,
			inventoryLabelX,
			inventoryLabelY,
			LABEL_COLOUR,
			true,
		)
	}

	override fun extractBackground(
		extractor: GuiGraphicsExtractor,
		mouseX: Int,
		mouseY: Int,
		partialTick: Float,
	) {
		super.extractBackground(extractor, mouseX, mouseY, partialTick)

		val x = (width - imageWidth) / 2
		val y = (height - imageHeight) / 2
		extractor.blit(
			RenderPipelines.GUI_TEXTURED,
			TEXTURE,
			x, y,
			0.0f, 0.0f,
			imageWidth, imageHeight,
			SHEET, SHEET,
		)
	}

	companion object {
		private val TEXTURE = VanguardSpirits.id("textures/gui/container/golden_chest.png")

		/** The panel occupies the top-left of a 256x256 sheet, as vanilla's do. */
		private const val SHEET = 256

		/** The light tone of the panel's own gilding. */
		private const val TITLE_COLOUR = 0xFFE6CB7C.toInt()

		/** The player's inventory: bone, so it reads without competing. */
		private const val LABEL_COLOUR = 0xFFD8D0BC.toInt()
	}
}
