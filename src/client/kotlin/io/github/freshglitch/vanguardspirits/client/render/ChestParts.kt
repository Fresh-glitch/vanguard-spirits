package io.github.freshglitch.vanguardspirits.client.render

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.PartPose
import net.minecraft.client.model.geom.builders.CubeListBuilder
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.model.geom.builders.MeshDefinition
import net.minecraft.client.model.geom.builders.PartDefinition

/**
 * Geometry for the Gilded Reliquary.
 *
 * **The texOffs values here and the net offsets in the atlas generator are one
 * fact written twice.** Each `texOffs(u, v)` names the top-left of that part's
 * unwrapped net, and the generator paints the nets at exactly those coordinates.
 * Change a box's size or offset here and the atlas has to be regenerated to
 * match, or the part samples someone else's pixels.
 *
 * The model is symmetric about both horizontal axes: the plinth and lid are
 * centred, and the four corner posts are the same box mirrored into each corner.
 */
object ChestParts {

	val LAYER: ModelLayerLocation =
		ModelLayerLocation(VanguardSpirits.id("golden_chest"), "main")

	const val BODY: String = "body"
	const val LID: String = "lid"

	/** Texture sheet size. Must match the generated atlas. */
	private const val ATLAS = 128

	/**
	 * Y of the hinge line, in model units.
	 *
	 * The lid pivots about the back edge of its own slab, so opening swings the
	 * crown and latch with it rather than shearing them off.
	 */
	private const val HINGE_Y = 15.0f
	private const val HINGE_Z = 15.0f

	fun createLayer(): LayerDefinition {
		val mesh = MeshDefinition()
		val root = mesh.root

		body(root)
		lid(root)

		return LayerDefinition.create(mesh, ATLAS, ATLAS)
	}

	/** Everything that stays put: plinth, walls and the four corner posts. */
	private fun body(root: PartDefinition) {
		val body = root.addOrReplaceChild(BODY, CubeListBuilder.create(), PartPose.ZERO)

		body.addOrReplaceChild(
			"plinth",
			CubeListBuilder.create().texOffs(0, 0).addBox(0.0f, 0.0f, 0.0f, 16.0f, 2.0f, 16.0f),
			PartPose.ZERO,
		)

		body.addOrReplaceChild(
			"walls",
			CubeListBuilder.create().texOffs(0, 20).addBox(1.0f, 2.0f, 1.0f, 14.0f, 9.0f, 14.0f),
			PartPose.ZERO,
		)

		// One box, four corners. Sharing the net keeps them identical by
		// construction rather than by four hand-copied offsets.
		val corners = listOf(
			"post_nw" to (0.0f to 0.0f),
			"post_ne" to (13.0f to 0.0f),
			"post_sw" to (0.0f to 13.0f),
			"post_se" to (13.0f to 13.0f),
		)
		for ((name, spot) in corners) {
			val (x, z) = spot
			body.addOrReplaceChild(
				name,
				CubeListBuilder.create().texOffs(64, 20).addBox(x, 2.0f, z, 3.0f, 11.0f, 3.0f),
				PartPose.ZERO,
			)
		}
	}

	/** Everything that swings: the slab, its crown, and the latch. */
	private fun lid(root: PartDefinition) {
		val lid = root.addOrReplaceChild(
			LID,
			CubeListBuilder.create(),
			PartPose.offset(0.0f, HINGE_Y, HINGE_Z),
		)

		// Children are positioned relative to the hinge, so subtract it back out.
		lid.addOrReplaceChild(
			"slab",
			CubeListBuilder.create().texOffs(0, 46)
				.addBox(1.0f, 11.0f - HINGE_Y, 1.0f - HINGE_Z, 14.0f, 4.0f, 14.0f),
			PartPose.ZERO,
		)

		lid.addOrReplaceChild(
			"crown",
			CubeListBuilder.create().texOffs(0, 66)
				.addBox(2.0f, 15.0f - HINGE_Y, 2.0f - HINGE_Z, 12.0f, 1.0f, 12.0f),
			PartPose.ZERO,
		)

		lid.addOrReplaceChild(
			"latch",
			CubeListBuilder.create().texOffs(64, 36)
				.addBox(7.0f, 10.0f - HINGE_Y, -0.5f - HINGE_Z, 2.0f, 4.0f, 2.0f),
			PartPose.ZERO,
		)
	}
}
