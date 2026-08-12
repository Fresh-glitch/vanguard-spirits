package io.github.freshglitch.vanguardspirits.client.render

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.entity.Nymph
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.resources.Identifier

class NymphRenderer(context: EntityRendererProvider.Context) :
	MobRenderer<Nymph, NymphRenderState, NymphModel>(
		context,
		NymphModel(context.bakeLayer(NymphModel.LAYER)),
		SHADOW,
	) {

	override fun createRenderState(): NymphRenderState = NymphRenderState()

	override fun extractRenderState(entity: Nymph, state: NymphRenderState, partialTick: Float) {
		super.extractRenderState(entity, state, partialTick)
		state.wrath = entity.wrathAmount
		state.run = entity.runAmount
	}

	override fun getTextureLocation(state: NymphRenderState): Identifier = TEXTURE

	companion object {
		private val TEXTURE = VanguardSpirits.id("textures/entity/nymph.png")

		/** Narrow, because she is. Her widest point is the sash at seven units. */
		private const val SHADOW = 0.4f
	}
}
