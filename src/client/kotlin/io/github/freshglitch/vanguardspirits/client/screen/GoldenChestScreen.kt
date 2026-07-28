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
	}
}
