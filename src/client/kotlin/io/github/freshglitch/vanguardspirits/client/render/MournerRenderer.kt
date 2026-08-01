package io.github.freshglitch.vanguardspirits.client.render

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.entity.Mourner
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.MobRenderer
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.resources.Identifier

class MournerRenderer(context: EntityRendererProvider.Context) :
	MobRenderer<Mourner, LivingEntityRenderState, MournerModel>(
		context,
		MournerModel(context.bakeLayer(MournerModel.LAYER)),
		SHADOW,
	) {

	override fun createRenderState(): LivingEntityRenderState = LivingEntityRenderState()

	override fun getTextureLocation(state: LivingEntityRenderState): Identifier = TEXTURE

	companion object {
		private val TEXTURE = VanguardSpirits.id("textures/entity/mourner.png")

		/** Barely there. It is always well above the ground it would fall on. */
		private const val SHADOW = 0.3f
	}
}
