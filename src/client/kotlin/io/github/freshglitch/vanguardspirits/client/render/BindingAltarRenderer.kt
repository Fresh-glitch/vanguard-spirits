package io.github.freshglitch.vanguardspirits.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import io.github.freshglitch.vanguardspirits.block.BindingAltarBlock
import io.github.freshglitch.vanguardspirits.block.entity.BindingAltarBlockEntity
import io.github.freshglitch.vanguardspirits.item.MemoryHue
import net.minecraft.util.LightCoordsUtil
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.item.ItemModelResolver
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.Mth
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.phys.Vec3

/**
 * Draws what is lying on a Binding Altar: the charm flat in the rune ring, and
 * the Fractured Memories circling above it.
 *
 * ## Where the numbers come from
 *
 * All of them are read off `blockbench/binding_altar.bbmodel` rather than tuned
 * by eye, because the ring the charm sits in is small and being a pixel out
 * reads as the charm floating beside its setting.
 *
 * The table is the cuboid `[1, 9, 1]` to `[15, 12, 15]`, so its working surface
 * is at y = 12/16 and it is fourteen wide. Its `up` face maps the whole 16x16
 * `binding_altar_top.png` across those fourteen, so **one texture pixel is
 * 14/16 of a block pixel** -- which is the conversion every figure below turns
 * on, and the reason the ring is smaller in the world than it looks in the file.
 *
 * The ring itself is `ring_mask(5.2)` about texture (8, 8), a stroke running
 * from radius 4.6 to 5.8. Its centre therefore lands on the block centre, and
 * the clear ground inside it is 4.6 x 14/16 = 4.03 block pixels of radius --
 * about half a block across. That is what [CHARM_SCALE] is sized against.
 */
class BindingAltarRenderer(context: BlockEntityRendererProvider.Context) :
	BlockEntityRenderer<BindingAltarBlockEntity, BindingAltarRenderState> {

	private val itemModelResolver: ItemModelResolver = context.itemModelResolver()

	override fun createRenderState(): BindingAltarRenderState = BindingAltarRenderState()

	override fun extractRenderState(
		entity: BindingAltarBlockEntity,
		state: BindingAltarRenderState,
		partialTick: Float,
		cameraPos: Vec3,
		crumbling: ModelFeatureRenderer.CrumblingOverlay?,
	) {
		BlockEntityRenderState.extractBase(entity, state, crumbling)

		val level = entity.level ?: return
		val payment = entity.payment

		state.facing = entity.blockState.getValue(BindingAltarBlock.FACING)
		state.memories = payment.count

		// Distinct seeds, so a model that varies per item does not give the charm
		// and the memories the same roll.
		val seed = entity.blockPos.asLong().toInt()
		itemModelResolver.updateForTopItem(
			state.charm,
			entity.charm,
			ItemDisplayContext.FIXED,
			level,
			null,
			seed,
		)
		itemModelResolver.updateForTopItem(
			state.memory,
			payment,
			ItemDisplayContext.FIXED,
			level,
			null,
			seed + 1,
		)

		state.moteLight = LightCoordsUtil.lightCoordsWithEmission(state.lightCoords, glow())

		// Off `gameTime` rather than anything counted here: it is the one clock
		// that survives a reload and that every altar in the world agrees on.
		val time = level.gameTime
		state.spin = ((time % SPIN_TICKS).toFloat() + partialTick) / SPIN_TICKS
		state.bob = ((time % BOB_TICKS).toFloat() + partialTick) / BOB_TICKS
	}

	/**
	 * How brightly a Fractured Memory is burning at this instant.
	 *
	 * ## There is no such thing as coloured light here
	 *
	 * Vanilla stores light as two four-bit numbers, one sky and one block, and
	 * looks the pair up in a 16x16 table -- `DataLayer` is literally a nibble
	 * array. So a hue cannot be emitted; the colour the player sees is the
	 * *sprite's*, and all this does is stop the world dimming it.
	 *
	 * What can still be honest is the **amount**. The memory's texture drifts
	 * through the four charms' rune colours, and those differ in luminance --
	 * the Delver's gold is far brighter than the Wanderer's green -- so driving
	 * the emission off the colour currently showing makes the glow rise and fall
	 * in step with the cycle instead of sitting at a flat maximum.
	 *
	 * [MemoryHue] is the right clock and the only one to use: it is advanced from
	 * `TextureManager.tick` by mixin, the very call that steps the sprite. A
	 * second counter of our own would drift below twenty frames a second and
	 * across every pause, which is the mistake that feature was built to fix.
	 */
	private fun glow(): Int {
		val colour = MemoryHue.current()

		val luma = (
			LUMA_RED * ((colour shr 16) and 0xFF) +
				LUMA_GREEN * ((colour shr 8) and 0xFF) +
				LUMA_BLUE * (colour and 0xFF)
			) / 255.0f

		// Stretched across the range the cycle actually visits, not across
		// [0, 1]. Mapping the raw figure wastes almost all of the band -- the
		// four colours only span 0.59 to 0.82, which came out as a single level
		// of difference between the brightest and dimmest of them.
		val across = ((luma - LUMA_FLOOR) / (LUMA_CEILING - LUMA_FLOOR)).coerceIn(0.0f, 1.0f)

		return Math.round(DIMMEST_GLOW + (BRIGHTEST_GLOW - DIMMEST_GLOW) * across)
			.coerceIn(DIMMEST_GLOW, BRIGHTEST_GLOW)
	}

	override fun submit(
		state: BindingAltarRenderState,
		poseStack: PoseStack,
		collector: SubmitNodeCollector,
		camera: CameraRenderState,
	) {
		if (!state.charm.isEmpty) {
			poseStack.pushPose()

			// Centre of the ring, then square to the altar's own facing, so the
			// charm reads the right way up to whoever placed the block.
			//
			// The half turn is not decoration. FACING is set to the *opposite* of
			// the way the player was looking, so it points back at them, and the
			// composition of that yaw with the quarter turn below lands the top of
			// the sprite -- the charm's gold loop -- on the far side of the ring.
			// Which is to say the derivation said this was already right and the
			// screenshot said otherwise; the screenshot wins, as it did for the
			// arm rotations in the Sentinel's animations.
			poseStack.translate(0.5f, TABLE_TOP + CHARM_LIFT, 0.5f)
			poseStack.mulPose(Axis.YP.rotationDegrees(HALF_TURN - state.facing.toYRot()))

			// Face up. An item model arrives standing in the XY plane and centred
			// on the origin -- no translate is needed to bring it to the middle,
			// only the quarter turn to lay it down.
			poseStack.mulPose(Axis.XP.rotationDegrees(QUARTER_TURN))
			poseStack.scale(CHARM_SCALE, CHARM_SCALE, CHARM_SCALE)

			// Nothing here lights the rune. The charm's own model carries a second,
			// emissive element for that, so it burns wherever the charm is rather
			// than only on this block -- see ModModelProvider.glowingCharm.
			state.charm.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0)
			poseStack.popPose()
		}

		if (state.memories <= 0 || state.memory.isEmpty) return

		// Two rings, filling outward-first, counter-rotating.
		//
		// [RING_SIZE] twice over is sixteen, which is exactly a full stack of
		// Fractured Memories -- so the orbit is an honest count for every stack
		// that can legally be put here, not a cap that quietly stops rising. It
		// also matches the eight links of the binding chain on the panel, so a
		// player who has learned to read one has learned to read the other.
		val outer = minOf(state.memories, RING_SIZE)
		val inner = minOf(state.memories - outer, RING_SIZE)

		orbit(state, poseStack, collector, outer, OUTER_RADIUS, OUTER_HEIGHT, 1.0f)
		orbit(state, poseStack, collector, inner, INNER_RADIUS, INNER_HEIGHT, -1.0f)
	}

	/**
	 * One ring of memories, evenly spaced and turning.
	 *
	 * [direction] is +1 or -1; the two rings run opposite ways so the pair reads
	 * as motion rather than as a single wider band.
	 */
	private fun orbit(
		state: BindingAltarRenderState,
		poseStack: PoseStack,
		collector: SubmitNodeCollector,
		count: Int,
		radius: Float,
		height: Float,
		direction: Float,
	) {
		for (i in 0 until count) {
			val share = i.toFloat() / count
			val turn = state.spin * direction + share

			val angle = turn * Mth.TWO_PI
			val x = 0.5f + Mth.cos(angle.toDouble()) * radius
			val z = 0.5f + Mth.sin(angle.toDouble()) * radius

			// Phase-shifted per mote and on a period that is not a factor of the
			// spin's, so the ring never settles into rising and falling together.
			val lift = Mth.sin(((state.bob + share) * Mth.TWO_PI).toDouble()) * BOB_HEIGHT

			poseStack.pushPose()
			poseStack.translate(x, height + lift, z)

			// Face outward. A sprite's normal is +Z, and a yaw of b sends that to
			// (sin b, 0, cos b); the outward direction here is (cos a, 0, sin a),
			// so b = 90 - a.
			poseStack.mulPose(Axis.YP.rotationDegrees(QUARTER_TURN - turn * FULL_TURN))
			poseStack.scale(MOTE_SCALE, MOTE_SCALE, MOTE_SCALE)

			state.memory.submit(poseStack, collector, state.moteLight, OverlayTexture.NO_OVERLAY, 0)
			poseStack.popPose()
		}
	}

	companion object {
		/** The table's working surface: the top of the cuboid at y = 12/16. */
		private const val TABLE_TOP = 0.75f

		/**
		 * Sized so the charm fills its setting rather than sits in the middle of it.
		 *
		 * **A sprite is not its texture.** Every charm's drawing occupies 12 x 14
		 * of its 16 x 16 sheet -- the rest is the transparent margin the outline
		 * convention leaves -- so what lands on the stone is 12/16 of whatever
		 * scale is asked for, not all of it. Sized against the full sheet the
		 * charm came out filling 60% of the ring, which is what the first pass
		 * did and what a screenshot showed: a charm floating in a setting made
		 * for something bigger.
		 *
		 * Against the drawing instead: 12 x 0.48 = 5.8 block pixels across and
		 * 14 x 0.48 = 6.7 deep, inside a clear ring about 8.05 across. That
		 * leaves a pixel of stone at the sides and two thirds of one front and
		 * back -- set in, with the ring still visibly a ring around it.
		 */
		private const val CHARM_SCALE = 0.48f

		/**
		 * Just enough to rest the charm on the stone instead of half inside it.
		 *
		 * An `item/generated` model is one pixel deep and centred on its own
		 * origin, so half its thickness -- scale/32 -- is what hangs below the
		 * pivot. The last thousandth is clearance: laid exactly flush, the two
		 * surfaces fight over which is in front and the charm flickers.
		 */
		private const val CHARM_LIFT = CHARM_SCALE / 32.0f + 0.002f

		/** How many memories one ring holds. Two rings is a full stack of them. */
		private const val RING_SIZE = 8

		/**
		 * Radii, in blocks from the block centre.
		 *
		 * The outer ring clears the rune stroke, whose outside edge is at
		 * 5.8 x 14/16 = 5.08 block pixels; the inner one runs just inside it,
		 * over the charm. Neither reaches the table's edge at 7 block pixels even
		 * with a mote's own width added.
		 */
		private const val OUTER_RADIUS = 0.36f
		private const val INNER_RADIUS = 0.26f

		/**
		 * And their heights, which are set by what the motes must clear.
		 *
		 * The first pass tucked the outer ring under the finial tips so the
		 * stonework would frame it, and that was too clever by half: the rim runs
		 * to 13/16, and a mote hanging half its own height plus a bob below its
		 * centre reaches 0.0875 + [BOB_HEIGHT] down. At 0.92 that bottomed out at
		 * 0.8025, *below* the rim -- so twice a revolution every mote sank into
		 * the stone. It read as the ring resting on the altar rather than
		 * circling above it, which is the opposite of the point.
		 *
		 * 0.97 puts the worst case at 0.8525, a comfortable two thirds of a pixel
		 * clear of the rim, and leaves the mote's centre just above the finials
		 * at 15/16 -- floating over the altar rather than sitting in it.
		 */
		private const val OUTER_HEIGHT = TABLE_TOP + 0.22f
		private const val INNER_HEIGHT = TABLE_TOP + 0.36f

		/**
		 * Under two pixels: a mote, not a second charm.
		 *
		 * The same sprite-is-not-its-texture correction as [CHARM_SCALE] -- the
		 * Fractured Memory's drawing is 12 of its 16 pixels wide, so this puts
		 * 1.8 block pixels on screen. At 0.20 they came out two thirds the size
		 * of the charm and a full sixteen of them buried it; against a charm now
		 * 5.8 pixels across, the subject wins by better than three to one.
		 */
		private const val MOTE_SCALE = 0.15f

		/** Ticks per revolution. Six seconds -- a drift, not a spin. */
		private const val SPIN_TICKS = 120L

		/**
		 * And per rise and fall. Deliberately not a factor of [SPIN_TICKS]: a
		 * period that divided it would put every mote back at the same height on
		 * the same beat every revolution, and the ring would pulse.
		 */
		private const val BOB_TICKS = 53L

		/** Half a pixel up and half a pixel down. */
		private const val BOB_HEIGHT = 0.03f

		/**
		 * The band the memories' glow moves through, in block-light levels.
		 *
		 * A floor, not an override: `lightCoordsWithEmission` takes the greater
		 * of this and the world's own light, so out in daylight the motes are lit
		 * like everything else and none of this shows. It is only in the dark
		 * that they burn on their own, which is where they belong.
		 *
		 * Eleven to fifteen, straddling a torch at fourteen. Measured, not
		 * guessed -- with [LUMA_FLOOR] and [LUMA_CEILING] below, the four rune
		 * colours land on 11 (Wanderer green), 13 (Returned violet), 14 (Delver
		 * gold) and 15 (Leaper blue).
		 */
		private const val DIMMEST_GLOW = 11
		private const val BRIGHTEST_GLOW = 15

		/**
		 * The luminance range the memory's cycle actually covers.
		 *
		 * **Tuned to `MemoryHue.STOPS`**, whose four colours run 0.586 to 0.819;
		 * these sit just outside that so the extremes are reached without being
		 * clipped flat. Retune them alongside the stops, the same way those are
		 * already hand-kept in step with `tools/make_fractured_memory.py` -- get
		 * it wrong and the glow does not break, it just stops breathing.
		 */
		private const val LUMA_FLOOR = 0.55f
		private const val LUMA_CEILING = 0.85f

		/** Rec. 601 luma weights: the eye is far more sensitive to green than blue. */
		private const val LUMA_RED = 0.299f
		private const val LUMA_GREEN = 0.587f
		private const val LUMA_BLUE = 0.114f

		private const val QUARTER_TURN = 90.0f
		private const val HALF_TURN = 180.0f
		private const val FULL_TURN = 360.0f
	}
}
