package io.github.freshglitch.vanguardspirits.client.render

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState

class MournerRenderState : LivingEntityRenderState() {
	/** Sitting on a branch rather than flying. */
	@JvmField
	var perched: Boolean = false

	/**
	 * Standing on the ground rather than in the air.
	 *
	 * Separate from [perched], which is the sentry's deliberate stop on a branch.
	 * This one is the wild bird simply being on its feet, and it is what puts the
	 * wings away and the legs to work.
	 */
	@JvmField
	var afoot: Boolean = false
}
