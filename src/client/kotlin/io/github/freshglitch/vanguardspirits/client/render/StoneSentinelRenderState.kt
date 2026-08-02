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

	/** 0 when not waking, otherwise 1..WAKE_TICKS. Drives the whole sequence. */
	@JvmField
	var wakeTick: Int = 0

	@JvmField
	var attackKind: Byte = 0

	@JvmField
	var attackTick: Int = 0

	/** 0..1. Dim amber ember at rest, white at full. */
	@JvmField
	var eyeGlow: Float = 0.0f

	/** 0 when not spinning, otherwise 1..GYRE_TICKS. */
	@JvmField
	var gyreTick: Int = 0

	/** 0 when standing, otherwise how long it has been curled up. Unbounded. */
	@JvmField
	var bulwarkTick: Int = 0
}
