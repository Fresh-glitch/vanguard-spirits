package io.github.freshglitch.vanguardspirits.client.render

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState

class NymphRenderState : LivingEntityRenderState() {
	/**
	 * How far into her temper she is, 0 calm to 1 wrathful.
	 *
	 * Already eased when it arrives -- the Nymph lerps it on both sides in her
	 * own tick, so the client has a smooth number without a second synced field
	 * and without the renderer keeping state of its own. That last part matters:
	 * a renderer is shared by every Nymph on screen, so anything remembered on
	 * it would be remembered for all of them at once.
	 */
	@JvmField
	var wrath: Float = 0.0f

	/**
	 * Which gait she is in, 0 walking to 1 running, eased on the entity for the
	 * same reason [wrath] is.
	 *
	 * It answers whether she is *closing on somebody*, not how fast she happens
	 * to be moving -- see `Nymph.runAmount`. How fast she is moving is a separate
	 * question and `walkAnimationSpeed` already answers it; the model needs both,
	 * because the pair of them is what separates a Nymph running at you from a
	 * Nymph standing over you swinging.
	 */
	@JvmField
	var run: Float = 0.0f
}
