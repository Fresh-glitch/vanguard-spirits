package io.github.freshglitch.vanguardspirits.client.render

import com.mojang.blaze3d.vertex.PoseStack
import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.entity.StoneSentinel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.RenderLayer
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.ARGB
import net.minecraft.util.Mth

class StoneSentinelRenderer(context: EntityRendererProvider.Context) :
	MobRenderer<StoneSentinel, StoneSentinelRenderState, StoneSentinelModel>(
		context,
		StoneSentinelModel(context.bakeLayer(StoneSentinelModel.LAYER)),
		SHADOW,
	) {

	init {
		addLayer(EyesOfStone(this))
	}

	override fun createRenderState(): StoneSentinelRenderState = StoneSentinelRenderState()

	override fun extractRenderState(
		entity: StoneSentinel,
		state: StoneSentinelRenderState,
		partialTick: Float,
	) {
		super.extractRenderState(entity, state, partialTick)
		state.dormant = entity.isDormant
		state.wakeTick = entity.wakeTick
		state.attackKind = entity.attackKind
		state.attackTick = entity.attackTick
		state.eyeGlow = entity.glow / 100.0f
	}

	override fun getTextureLocation(state: StoneSentinelRenderState): Identifier = TEXTURE

	/**
	 * The glowing eyes.
	 *
	 * A separate emissive pass rather than bright pixels in the body texture --
	 * `RenderTypes.eyes` ignores world light, so they stay lit in an unlit crypt,
	 * which is the whole point of them.
	 *
	 * Not vanilla's [EyesLayer], which draws at a fixed brightness. This one
	 * tints the pass so the light can come up as the sentinel wakes and drain
	 * away as it goes back to stone. The third int of `submitModel` is an ARGB
	 * multiplier -- vanilla passes -1 there, which is why its eyes never vary.
	 *
	 * At rest they hold a dim amber ember rather than going out. A statue whose
	 * eyes are lit is a warning a player can walk past; one that only lights up
	 * on waking would be a warning that arrives too late to act on.
	 */
	private class EyesOfStone(
		parent: RenderLayerParent<StoneSentinelRenderState, StoneSentinelModel>,
	) : RenderLayer<StoneSentinelRenderState, StoneSentinelModel>(parent) {

		override fun submit(
			pose: PoseStack,
			collector: SubmitNodeCollector,
			light: Int,
			state: StoneSentinelRenderState,
			yRot: Float,
			xRot: Float,
		) {
			val glow = state.eyeGlow.coerceIn(0.0f, 1.0f)
			if (glow <= 0.02f) return

			collector.order(1).submitModel(
				parentModel,
				state,
				pose,
				EYES,
				light,
				OverlayTexture.NO_OVERLAY,
				tint(glow),
				null,
				state.outlineColor,
				null,
			)
		}

		/** Amber at rest through to white at full, dimming as it goes. */
		private fun tint(glow: Float): Int {
			val scale = Mth.lerp(glow, EMBER_SCALE, 1.0f)
			return ARGB.color(
				255,
				(Mth.lerp(glow, EMBER_R, 255.0f) * scale).toInt(),
				(Mth.lerp(glow, EMBER_G, 255.0f) * scale).toInt(),
				(Mth.lerp(glow, EMBER_B, 255.0f) * scale).toInt(),
			)
		}

		private companion object {
			const val EMBER_R = 255.0f
			const val EMBER_G = 150.0f
			const val EMBER_B = 60.0f

			/** How far the ember is knocked back from full brightness. */
			const val EMBER_SCALE = 0.32f
		}
	}

	companion object {
		private val TEXTURE = VanguardSpirits.id("textures/entity/stone_sentinel.png")
		private val EYES: RenderType =
			RenderTypes.eyes(VanguardSpirits.id("textures/entity/stone_sentinel_eyes.png"))

		private const val SHADOW = 0.9f
	}
}
