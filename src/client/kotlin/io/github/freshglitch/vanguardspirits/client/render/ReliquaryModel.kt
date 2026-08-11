package io.github.freshglitch.vanguardspirits.client.render

import net.minecraft.client.model.Model
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.rendertype.RenderTypes

/**
 * The Gilded Reliquary's geometry, with its lid angle carried as *state*.
 *
 * ## Why this class exists at all
 *
 * The renderer used to set `lid.xRot` itself and hand the bare [ModelPart] to
 * `submitModelPart`. That reads as though it draws the lid there and then. It
 * does not: `SubmitNodeCollection.submitModel` copies the **pose** and stores
 * the **model by reference**, then queues a node to be drawn later in the frame.
 * One `ModelPart` field on the renderer is therefore shared by every Reliquary
 * on screen, and when the queue finally runs, all of them draw with whatever
 * angle the last one wrote.
 *
 * With one chest in the world that is invisible, which is why it survived from
 * the day the Reliquary was added. With two it is unmistakable, and exactly
 * asymmetric: open the chest that happens to be submitted *last* and both lids
 * swing; open the other and neither moves, though the sound plays once because
 * the server was never confused -- only the drawing was.
 *
 * Vanilla hit the same wall and answered it with [Model] taking a type
 * parameter. The openness travels as the `S` handed to `submitModel`, the node
 * keeps that value alongside the model, and [setupAnim] is called against it
 * immediately before *that* node draws. `ChestModel` is `Model<Float>` for
 * precisely this reason; so is this.
 */
class ReliquaryModel(root: ModelPart) : Model<Float>(root, RenderTypes::entityCutout) {

	private val body: ModelPart = root.getChild(ChestParts.BODY)
	private val lid: ModelPart = root.getChild(ChestParts.LID)

	/**
	 * Sets the lid for the chest about to be drawn.
	 *
	 * Positive, not negative. The hinge sits at the back and the slab runs
	 * toward -z, so rotating about X gives `y' = -z * sin(theta)` -- a positive
	 * angle lifts the front edge. Negating it swings the lid down through the
	 * chest instead.
	 *
	 * Ease-out on the swing: chests should fall open, not snap.
	 */
	override fun setupAnim(openness: Float) {
		super.setupAnim(openness)

		val swing = 1.0f - (1.0f - openness).let { it * it * it }
		lid.xRot = swing * MAX_LID_ANGLE
	}

	companion object {
		/**
		 * About 26 degrees -- ajar rather than thrown wide.
		 *
		 * The lid is fourteen units deep, so its front edge sweeps a long arc:
		 * even a quarter turn stands it upright and well clear of the block. A
		 * shallow angle keeps the reliquary looking sealed and heavy.
		 */
		private const val MAX_LID_ANGLE = 0.45f
	}
}
