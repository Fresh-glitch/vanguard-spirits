package io.github.freshglitch.vanguardspirits.client.render

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState

class MournerRenderState : LivingEntityRenderState() {
	/** Sitting on a branch rather than flying. */
	@JvmField
	var perched: Boolean = false
}
