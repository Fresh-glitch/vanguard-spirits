package io.github.freshglitch.vanguardspirits.client.screen

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.lore.MuralLore
import io.github.freshglitch.vanguardspirits.registry.ModSounds
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.util.FormattedCharSequence
import org.lwjgl.glfw.GLFW

/**
 * Reading a mural.
 *
 * Not an `AbstractContainerScreen`: there is nothing to hold and no slots to
 * line up, so it is a plain [Screen] and the whole panel is ours to lay out.
 *
 * ## One pair of arrows, not two
 *
 * There are two things a reader might want to page through -- the lines of a
 * passage too long for one panel, and the other passages they have already
 * found -- and giving each its own control makes a lore screen feel like a
 * filing cabinet. So both are flattened into a single ordered list of
 * [Spread]s, and one pair of arrows walks it end to end. Paging back off the
 * first page of passage V lands on the last page of passage IV, which is what a
 * book does and needs no explaining.
 *
 * Only passages the player has actually read appear. Gaps are deliberate: a
 * reader who goes III, V, VI can see they walked past something.
 */
class MuralScreen(
	private val opened: Int,
	private val read: Int,
) : Screen(MuralLore.title(opened)) {

	/** One panel's worth of text: which passage, which page of it, and the lines. */
	private class Spread(
		val passage: Int,
		val page: Int,
		val pages: Int,
		val lines: List<FormattedCharSequence>,
	)

	private var spreads: List<Spread> = emptyList()
	private var index = 0

	private var panelX = 0
	private var panelY = 0

	override fun init() {
		super.init()

		panelX = (width - PANEL_W) / 2
		panelY = (height - PANEL_H) / 2

		// Built here rather than in the constructor because wrapping needs the
		// font, and `font` is only populated once the screen has been added.
		// `init` also runs again on resize, which is exactly when the wrap would
		// need redoing anyway.
		spreads = buildSpreads()
		index = spreads.indexOfFirst { it.passage == opened && it.page == 0 }.coerceAtLeast(0)
	}

	/**
	 * Every read passage, wrapped and cut into panel-sized pages.
	 *
	 * Paragraphs are split here rather than left to [net.minecraft.client.gui.Font.split],
	 * so the blank line between them is one we placed and can reason about --
	 * in particular, a page never opens on one.
	 */
	private fun buildSpreads(): List<Spread> {
		val out = mutableListOf<Spread>()

		for (passage in 0 until MuralLore.COUNT) {
			// The passage in front of the player is always shown, even in the
			// impossible case where the codex snapshot disagrees -- being stood
			// at a mural that refuses to show itself would be baffling.
			val found = (read and (1 shl passage)) != 0 || passage == opened
			if (!found) continue

			val lines = wrap(MuralLore.body(passage))
			val pages = lines.chunked(LINES_PER_PAGE)
				.map { page -> page.dropWhile { it === BLANK } }
				.ifEmpty { listOf(emptyList()) }

			pages.forEachIndexed { page, content ->
				out += Spread(passage, page, pages.size, content)
			}
		}

		return out
	}

	/** Wraps a body to the text column, keeping its paragraph breaks. */
	private fun wrap(body: Component): List<FormattedCharSequence> {
		val lines = mutableListOf<FormattedCharSequence>()

		body.string.split(PARAGRAPH).forEachIndexed { i, paragraph ->
			if (i > 0) lines += BLANK
			lines += font.split(Component.literal(paragraph), TEXT_W)
		}

		return lines
	}

	// ------------------------------------------------------------- navigation

	/**
	 * Reading does not stop the world.
	 *
	 * Vanilla's own read-only book screen does the same, but the reason to
	 * change it here was a symptom: the mural's read sound was not playing until
	 * the player *closed* the screen. A pausing screen pauses the sound engine
	 * with everything else, so the sound the server had already sent sat queued
	 * until the game ran again -- which looked like a bug in the block, and was
	 * really a property of the screen sitting in front of it.
	 */
	override fun isPauseScreen(): Boolean = false

	private fun turn(by: Int) {
		if (spreads.isEmpty()) return

		val moved = (index + by).coerceIn(0, spreads.size - 1)
		if (moved == index) return
		index = moved

		// Straight into the UI channel: this belongs to the interface, so it
		// should not fall off with distance or be heard by anyone else in the
		// room. Pitched slightly by direction, so paging back and paging on are
		// distinguishable without looking.
		minecraft?.soundManager?.play(
			SimpleSoundInstance.forUI(ModSounds.MURAL_TURN, if (by > 0) 1.0f else 0.92f),
		)
	}

	private fun canTurn(by: Int): Boolean =
		spreads.isNotEmpty() && (index + by) in spreads.indices

	override fun mouseClicked(event: MouseButtonEvent, doubled: Boolean): Boolean {
		if (event.button() == 0) {
			if (overPrev(event.x(), event.y()) && canTurn(-1)) {
				turn(-1)
				return true
			}
			if (overNext(event.x(), event.y()) && canTurn(1)) {
				turn(1)
				return true
			}
		}
		return super.mouseClicked(event, doubled)
	}

	override fun keyPressed(event: KeyEvent): Boolean {
		when (event.key()) {
			GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP -> if (canTurn(-1)) { turn(-1); return true }
			GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN -> if (canTurn(1)) { turn(1); return true }
		}
		return super.keyPressed(event)
	}

	private fun overPrev(mx: Double, my: Double): Boolean =
		inside(mx, my, panelX + PREV_X, panelY + ARROW_Y)

	private fun overNext(mx: Double, my: Double): Boolean =
		inside(mx, my, panelX + NEXT_X, panelY + ARROW_Y)

	private fun inside(mx: Double, my: Double, x: Int, y: Int): Boolean =
		mx >= x && mx < x + ARROW_W && my >= y && my < y + ARROW_H

	// ---------------------------------------------------------------- drawing

	override fun extractBackground(
		extractor: GuiGraphicsExtractor,
		mouseX: Int,
		mouseY: Int,
		partialTick: Float,
	) {
		// Dims the world behind, as every screen does.
		super.extractBackground(extractor, mouseX, mouseY, partialTick)

		extractor.blit(
			RenderPipelines.GUI_TEXTURED,
			TEXTURE,
			panelX, panelY,
			0.0f, 0.0f,
			PANEL_W, PANEL_H,
			SHEET, SHEET,
		)
	}

	override fun extractRenderState(
		extractor: GuiGraphicsExtractor,
		mouseX: Int,
		mouseY: Int,
		partialTick: Float,
	) {
		super.extractRenderState(extractor, mouseX, mouseY, partialTick)

		val spread = spreads.getOrNull(index) ?: return
		val centre = panelX + PANEL_W / 2

		extractor.centeredText(font, MuralLore.title(spread.passage), centre, panelY + TITLE_Y, AMBER)

		val marker = if (spread.pages > 1) {
			Component.translatable(
				"gui.${VanguardSpirits.MOD_ID}.mural.marker.paged",
				MuralLore.numeral(spread.passage),
				MuralLore.TOTAL_NUMERAL,
				spread.page + 1,
				spread.pages,
			)
		} else {
			Component.translatable(
				"gui.${VanguardSpirits.MOD_ID}.mural.marker",
				MuralLore.numeral(spread.passage),
				MuralLore.TOTAL_NUMERAL,
			)
		}
		extractor.centeredText(font, marker, centre, panelY + MARKER_Y, EMBER)

		var y = panelY + BODY_Y
		for (line in spread.lines) {
			// No shadow. Pale text on dark stone already separates cleanly, and a
			// shadow under every glyph turns a block of body copy to mud.
			extractor.text(font, line, panelX + TEXT_X, y, BONE, false)
			y += font.lineHeight
		}

		drawArrows(extractor, mouseX, mouseY)
	}

	private fun drawArrows(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {
		if (canTurn(-1)) {
			val hovered = overPrev(mouseX.toDouble(), mouseY.toDouble())
			extractor.blit(
				RenderPipelines.GUI_TEXTURED,
				TEXTURE,
				panelX + PREV_X, panelY + ARROW_Y,
				if (hovered) (PREV_U + ARROW_W).toFloat() else PREV_U.toFloat(), ARROW_V.toFloat(),
				ARROW_W, ARROW_H,
				SHEET, SHEET,
			)
		}
		if (canTurn(1)) {
			val hovered = overNext(mouseX.toDouble(), mouseY.toDouble())
			extractor.blit(
				RenderPipelines.GUI_TEXTURED,
				TEXTURE,
				panelX + NEXT_X, panelY + ARROW_Y,
				if (hovered) (NEXT_U + ARROW_W).toFloat() else NEXT_U.toFloat(), ARROW_V.toFloat(),
				ARROW_W, ARROW_H,
				SHEET, SHEET,
			)
		}
	}

	companion object {
		private val TEXTURE = VanguardSpirits.id("textures/gui/mural.png")

		/** The panel sits in the top-left of a 256x256 sheet, as vanilla's do. */
		private const val SHEET = 256

		const val PANEL_W: Int = 216
		const val PANEL_H: Int = 190

		/** Text column, inset from the carved border on both sides. */
		private const val TEXT_X = 22
		const val TEXT_W: Int = PANEL_W - 2 * TEXT_X

		private const val TITLE_Y = 15
		private const val MARKER_Y = 28
		private const val BODY_Y = 46

		/**
		 * How many lines of body fit between the rule and the footer.
		 *
		 * Fixed rather than derived from `font.lineHeight`, because the panel art
		 * has the band drawn into it -- a resource pack with a taller font would
		 * overflow the frame either way, and a constant is what the art was cut
		 * against.
		 */
		private const val LINES_PER_PAGE = 12

		const val ARROW_W: Int = 18
		const val ARROW_H: Int = 11
		private const val ARROW_Y = 168
		private const val PREV_X = 26
		private const val NEXT_X = PANEL_W - 26 - ARROW_W

		/** Arrow sprites, in a strip under the panel. Normal then hover, each. */
		const val ARROW_V: Int = 192
		const val PREV_U: Int = 0
		const val NEXT_U: Int = 2 * ARROW_W

		/** Bodies mark their paragraph breaks with a blank line. */
		private const val PARAGRAPH = "\n\n"

		private val BLANK: FormattedCharSequence = FormattedCharSequence.EMPTY

		// The mod's own ember/amber/bone ramp, the same one the logo is cut from.
		private val AMBER = 0xFFD6A244.toInt()
		private val EMBER = 0xFFB17D2A.toInt()
		private val BONE = 0xFFD8D0BC.toInt()
	}
}
