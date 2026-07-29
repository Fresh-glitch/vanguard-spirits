package io.github.freshglitch.vanguardspirits.client.render

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState

/**
 * What the renderer needs to know about a sentinel beyond the vanilla state.
 *
 * Only the one flag. Walk and idle motion already fall out of
 * `walkAnimationSpeed`, but head tracking does not -- a statue that follows the
 * player with its eyes gives the whole thing away, so the model has to be told
 * explicitly to hold still.
 */
class StoneSentinelRenderState : LivingEntityRenderState() {
	@JvmField
	var dormant: Boolean = true
}
