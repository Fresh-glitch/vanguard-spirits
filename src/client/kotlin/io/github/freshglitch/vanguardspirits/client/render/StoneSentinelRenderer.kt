package io.github.freshglitch.vanguardspirits.client.render

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.entity.StoneSentinel
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.client.renderer.entity.RenderLayerParent
import net.minecraft.client.renderer.entity.layers.EyesLayer
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.resources.Identifier

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
	}

	override fun getTextureLocation(state: StoneSentinelRenderState): Identifier = TEXTURE

	/**
	 * The glowing eyes.
	 *
	 * A separate emissive pass rather than bright pixels in the body texture --
	 * `RenderTypes.eyes` ignores world light, so they stay lit in an unlit crypt,
	 * which is the whole point of them.
	 *
	 * Drawn in both states. A statue whose eyes are already burning is a warning
	 * the player can walk past; eyes that only light up on waking would be a
	 * warning that arrives too late to act on.
	 */
	private class EyesOfStone(
		parent: RenderLayerParent<StoneSentinelRenderState, StoneSentinelModel>,
	) : EyesLayer<StoneSentinelRenderState, StoneSentinelModel>(parent) {
		override fun renderType(): RenderType = EYES
	}

	companion object {
		private val TEXTURE = VanguardSpirits.id("textures/entity/stone_sentinel.png")
		private val EYES: RenderType =
			RenderTypes.eyes(VanguardSpirits.id("textures/entity/stone_sentinel_eyes.png"))

		private const val SHADOW = 0.9f
	}
}
