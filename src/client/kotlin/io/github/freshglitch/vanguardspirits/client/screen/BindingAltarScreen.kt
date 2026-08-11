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

	/**
	 * The panel's own titles, in colours that can be read on it.
	 *
	 * Vanilla draws both labels at `0xFF404040` with no shadow, which is tuned
	 * for its own pale parchment panel. On this mod's dark stone it is very
	 * nearly the background: in the first screenshot of this screen the words
	 * "Binding Altar" were legible only because you knew they were there. Amber
	 * for the block's own name and bone for the player's inventory, both with a
	 * shadow, which is what lifts small text off a noisy stone ground.
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

		chain(extractor, x, y)

		priceLine()?.let { line ->
			extractor.centeredText(font, line, x + imageWidth / 2, y + PRICE_Y, PRICE_COLOUR)
		}
	}

	/**
	 * The binding chain: dead stone, with as much of it closed as has been paid for.
	 *
	 * This replaced an arrow, and the arrow was not merely dull -- it was a lie.
	 * An arrow between two slots is the furnace idiom and promises a process with
	 * a duration; the altar has none, so it never moved. There is only a price
	 * and how near you are to it, which is exactly what a chain filling link by
	 * link says. It is also the one readout that answers the question a player
	 * actually has while standing here, without them having to read the number.
	 *
	 * Drawn as the dark strip in full with the lit strip laid over its left-hand
	 * part, so a half-paid binding is a chain half closed rather than a row of
	 * lamps.
	 */
	private fun chain(extractor: GuiGraphicsExtractor, x: Int, y: Int) {
		val closed = closedLinks()

		extractor.blit(
			RenderPipelines.GUI_TEXTURED,
			TEXTURE,
			x + CHAIN_X, y + CHAIN_Y,
			0.0f, CHAIN_DARK_V.toFloat(),
			CHAIN_LINKS * LINK_W, LINK_H,
			SHEET, SHEET,
		)

		if (closed <= 0) return

		extractor.blit(
			RenderPipelines.GUI_TEXTURED,
			TEXTURE,
			x + CHAIN_X, y + CHAIN_Y,
			0.0f, CHAIN_LIT_V.toFloat(),
			closed * LINK_W, LINK_H,
			SHEET, SHEET,
		)
	}

	/**
	 * How many of the eight links the payment has closed.
	 *
	 * Rounds *down*, and only reaches eight when the price is actually met -- a
	 * chain that looked closed while the result slot stayed empty would be the
	 * panel contradicting itself. Nothing on the altar at all leaves it dark
	 * rather than full: an empty station should look idle, not finished.
	 */
	private fun closedLinks(): Int {
		val charm = menu.slots[BindingAltarMenu.CHARM_SLOT].item
		val item = charm.item as? CharmItem ?: return 0

		val next = CharmItem.depthOf(charm) + 1
		if (next > item.maxDepth) return 0

		val price = Binding.priceOf(next)
		if (price <= 0) return CHAIN_LINKS

		val paid = menu.slots[BindingAltarMenu.PAYMENT_SLOT].item.count
		if (paid >= price) return CHAIN_LINKS

		return (paid * CHAIN_LINKS / price).coerceIn(0, CHAIN_LINKS - 1)
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

		// The chain, matching `make_binding_altar.py`. Both files name these
		// numbers and the generator asserts the price line against this one, so a
		// change here fails the art rather than sliding the chain off its groove.
		private const val CHAIN_X = 76
		private const val CHAIN_Y = 51
		private const val CHAIN_LINKS = 8
		private const val LINK_W = 6
		private const val LINK_H = 10

		/** Where the two strips sit on the sheet, below the panel. */
		private const val CHAIN_DARK_V = 172
		private const val CHAIN_LIT_V = 186

		/** The same amber the murals are carved in. */
		private const val PRICE_COLOUR = 0xFFD6A244.toInt()

		/** The block's own name, in its own colour. */
		private const val TITLE_COLOUR = 0xFFD6A244.toInt()

		/** The player's inventory: bone, so it reads without competing. */
		private const val LABEL_COLOUR = 0xFFD8D0BC.toInt()
	}
}
