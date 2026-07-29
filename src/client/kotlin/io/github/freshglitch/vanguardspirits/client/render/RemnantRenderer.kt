package io.github.freshglitch.vanguardspirits.client.render

import com.mojang.blaze3d.vertex.PoseStack
import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.entity.Remnant
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

class RemnantRenderer(context: EntityRendererProvider.Context) :
	MobRenderer<Remnant, RemnantRenderState, RemnantModel>(
		context,
		RemnantModel(context.bakeLayer(RemnantModel.LAYER)),
		SHADOW,
	) {

	init {
		addLayer(WakingEyes(this))
	}

	override fun createRenderState(): RemnantRenderState = RemnantRenderState()

	override fun extractRenderState(entity: Remnant, state: RemnantRenderState, partialTick: Float) {
		super.extractRenderState(entity, state, partialTick)
		state.eyeGlow = entity.glow / 100.0f
	}

	override fun getTextureLocation(state: RemnantRenderState): Identifier = TEXTURE

	/**
	 * The eyes, which only burn once it has seen you.
	 *
	 * Same approach as the sentinel's: an emissive pass tinted through the third
	 * int of `submitModel`, which vanilla's [net.minecraft.client.renderer.entity.layers.EyesLayer]
	 * hardcodes to -1. Skipped entirely while dark, so an unaware Remnant is
	 * genuinely just a silhouette.
	 */
	private class WakingEyes(
		parent: RenderLayerParent<RemnantRenderState, RemnantModel>,
	) : RenderLayer<RemnantRenderState, RemnantModel>(parent) {

		override fun submit(
			pose: PoseStack,
			collector: SubmitNodeCollector,
			light: Int,
			state: RemnantRenderState,
			yRot: Float,
			xRot: Float,
		) {
			val glow = state.eyeGlow.coerceIn(0.0f, 1.0f)
			if (glow <= 0.02f) return

			val level = (Mth.lerp(glow, 0.0f, 255.0f)).toInt()
			collector.order(1).submitModel(
				parentModel,
				state,
				pose,
				EYES,
				light,
				OverlayTexture.NO_OVERLAY,
				ARGB.color(255, level, level, level),
				null,
				state.outlineColor,
				null,
			)
		}
	}

	companion object {
		private val TEXTURE = VanguardSpirits.id("textures/entity/remnant.png")
		private val EYES: RenderType =
			RenderTypes.eyes(VanguardSpirits.id("textures/entity/remnant_eyes.png"))

		private const val SHADOW = 0.5f
	}
}
