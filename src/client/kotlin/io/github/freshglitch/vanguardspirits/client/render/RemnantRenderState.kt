package io.github.freshglitch.vanguardspirits.client.render

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState

class RemnantRenderState : LivingEntityRenderState() {
	/** 0..1. Dark until it has something to chase. */
	@JvmField
	var eyeGlow: Float = 0.0f
}
