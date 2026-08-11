package io.github.freshglitch.vanguardspirits.client.render

import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.core.Direction

/**
 * The snapshot the altar renderer draws from.
 *
 * As with the Reliquary, 26.2 splits the work in two: extract reads the world
 * once per frame, submit never touches the block entity. Anything the draw needs
 * is copied here.
 *
 * The two [ItemStackRenderState]s are fields rather than freshly allocated each
 * frame, which vanilla's campfire does. `ItemModelResolver.updateForTopItem`
 * clears the state before it fills it -- that is the first line of its body --
 * so reusing one cannot accumulate stale layers, and it saves an allocation per
 * altar per frame. [memory] is resolved once and drawn for every mote in the
 * orbit, since they are all the same item.
 */
class BindingAltarRenderState : BlockEntityRenderState() {
	/** The charm lying in the ring. Empty when the altar is bare. */
	@JvmField
	val charm: ItemStackRenderState = ItemStackRenderState()

	/** One Fractured Memory, drawn [memories] times around the charm. */
	@JvmField
	val memory: ItemStackRenderState = ItemStackRenderState()

	/** How many memories are on the altar, and so how many motes to draw. */
	@JvmField
	var memories: Int = 0

	/** Which way the altar was placed; the charm lies square to it. */
	@JvmField
	var facing: Direction = Direction.NORTH

	/**
	 * Packed light for the motes: the block's own, floored by the memory's glow.
	 *
	 * Separate from [lightCoords] because only the memories are self-lit. The
	 * charm takes the world's light like any other object, and it needs to --
	 * with both of them glowing there is nothing for the glow to read against,
	 * and a dark crypt just looks evenly lit.
	 */
	@JvmField
	var moteLight: Int = 0


	/**
	 * Where the orbit has got to, and where the bob has, each as a fraction of
	 * one full turn.
	 *
	 * Fractions rather than a raw tick count on purpose. `gameTime` is a long and
	 * a world that has run for a few weeks overflows a float's precision, at
	 * which point the spin would visibly step instead of turn. Reducing modulo
	 * the period before the conversion keeps both of these small forever, and
	 * costs nothing -- a whole period is a whole revolution, so the wrap is
	 * seamless.
	 */
	@JvmField
	var spin: Float = 0.0f

	@JvmField
	var bob: Float = 0.0f
}
