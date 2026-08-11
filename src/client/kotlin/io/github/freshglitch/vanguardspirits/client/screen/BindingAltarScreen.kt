package io.github.freshglitch.vanguardspirits.client.screen

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.charm.Binding
import io.github.freshglitch.vanguardspirits.item.CharmItem
import io.github.freshglitch.vanguardspirits.menu.BindingAltarMenu
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

/**
 * The altar's panel: a charm, its price in memories, and what it becomes.
 *
 * The one thing this draws beyond the backdrop is the price line, and it is the
 * reason the screen exists rather than reusing a container: a player who puts a
 * charm in and nothing else has no way to find out what the binding costs. The
 * number comes from [Binding], shared with the server, so the panel cannot quote
 * a price the menu will not honour.
 */
class BindingAltarScreen(
	menu: BindingAltarMenu,
	inventory: Inventory,
	title: Component,
) : AbstractContainerScreen<BindingAltarMenu>(menu, inventory, title) {

	override fun init() {
		super.init()

		titleLabelX = (imageWidth - font.width(title)) / 2
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

		priceLine()?.let { line ->
			extractor.centeredText(font, line, x + imageWidth / 2, y + PRICE_Y, PRICE_COLOUR)
		}
	}

	/**
	 * What the altar has to say about the charm currently on it.
	 *
	 * Nothing at all when the charm slot is empty -- an empty station should look
	 * like an empty station, not like an error. Once a charm is there the line is
	 * either the price or, when the memories are already down, what the charm is
	 * about to become.
	 */
	private fun priceLine(): Component? {
		val charm = menu.slots[BindingAltarMenu.CHARM_SLOT].item
		val item = charm.item as? CharmItem ?: return null

		val next = CharmItem.depthOf(charm) + 1
		if (next > item.maxDepth) return null

		val paid = menu.slots[BindingAltarMenu.PAYMENT_SLOT].item.count
		val price = Binding.priceOf(next)

		return if (paid >= price) {
			Component.translatable(READY_KEY, CharmItem.numeral(next))
		} else {
			Component.translatable(PRICE_KEY, price - paid)
		}
	}

	companion object {
		const val PRICE_KEY: String = "container.vanguard-spirits.binding_altar.price"
		const val READY_KEY: String = "container.vanguard-spirits.binding_altar.ready"

		private val TEXTURE = VanguardSpirits.id("textures/gui/container/binding_altar.png")

		/** The panel occupies the top-left of a 256x256 sheet, as vanilla's do. */
		private const val SHEET = 256

		/** Just above the slot row, where the anvil puts its level cost. */
		private const val PRICE_Y = 33

		/** The same amber the murals are carved in. */
		private const val PRICE_COLOUR = 0xFFD6A244.toInt()
	}
}
