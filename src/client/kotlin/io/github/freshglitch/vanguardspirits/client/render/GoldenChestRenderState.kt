package io.github.freshglitch.vanguardspirits.client.render

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.core.Direction

/**
 * The snapshot the renderer works from.
 *
 * 26.2 splits block entity rendering in two: [GoldenChestRenderer.extractRenderState]
 * reads the world once, then `submit` draws from this and never touches the
 * block entity again. Anything the draw needs has to be copied here.
 */
class GoldenChestRenderState : BlockEntityRenderState() {
	/** Lid angle, 0 shut to 1 fully open, already interpolated for the frame. */
	@JvmField
	var openness: Float = 0.0f

	@JvmField
	var facing: Direction = Direction.NORTH
}
