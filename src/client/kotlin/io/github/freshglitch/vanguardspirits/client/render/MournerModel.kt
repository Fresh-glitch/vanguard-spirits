package io.github.freshglitch.vanguardspirits.client.render

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import net.minecraft.client.model.EntityModel
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.PartPose
import net.minecraft.client.model.geom.builders.CubeDeformation
import net.minecraft.client.model.geom.builders.CubeListBuilder
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.model.geom.builders.MeshDefinition
import net.minecraft.util.Mth

/**
 * The Mourner's geometry.
 *
 * **A transcription of Blockbench's Java export.** The source is
 * `blockbench/mourner.bbmodel`; edit there and re-export.
 */
class MournerModel(root: ModelPart) : EntityModel<MournerRenderState>(root) {

	private val body: ModelPart = root.getChild("body")
	private val head: ModelPart = root.getChild("head")
	private val wingRight: ModelPart = root.getChild("wing_right")
	private val wingLeft: ModelPart = root.getChild("wing_left")
	private val legRight: ModelPart = root.getChild("leg_right")
	private val legLeft: ModelPart = root.getChild("leg_left")

	override fun setupAnim(state: MournerRenderState) {
		super.setupAnim(state)

		if (state.perched) {
			// Folded, level, and standing on its feet. Only a faint shuffle, so
			// it is still clearly alive without looking like it is about to go.
			val settle = Mth.sin((state.ageInTicks * SETTLE_RATE).toDouble()) * SETTLE
			wingRight.zRot = FOLD + settle
			wingLeft.zRot = -FOLD - settle
			body.xRot = 0.0f
			head.xRot = state.xRot * Mth.DEG_TO_RAD * HEAD_STEADY
			head.yRot = 0.0f
			legRight.xRot = 0.0f
			legLeft.xRot = 0.0f
			return
		}

		// Mostly gliding. A slow deep beat with a long hold at the top reads as
		// a big bird riding a thermal; an even flap reads as a pigeon.
		val beat = Mth.sin((state.ageInTicks * BEAT_RATE).toDouble())
		val flap = if (beat > 0.0f) beat * BEAT_UP else beat * BEAT_DOWN

		wingRight.zRot = flap
		wingLeft.zRot = -flap

		// The tail and body follow a beat behind, so the whole bird moves rather
		// than just its wings.
		body.xRot = Mth.sin((state.ageInTicks * BEAT_RATE - LAG).toDouble()) * PITCH
		head.xRot = -body.xRot * HEAD_STEADY
		head.yRot = 0.0f

		// Legs tucked back in flight.
		legRight.xRot = TUCK
		legLeft.xRot = TUCK
	}

	companion object {
		val LAYER: ModelLayerLocation = ModelLayerLocation(VanguardSpirits.id("mourner"), "main")

		private const val BEAT_RATE = 0.16f
		private const val BEAT_UP = 0.55f
		private const val BEAT_DOWN = 0.25f
		private const val LAG = 0.9f
		private const val PITCH = 0.08f
		private const val HEAD_STEADY = 0.8f
		private const val TUCK = 0.8f

		/** Wings held in against the body while sitting, with a faint shuffle. */
		private const val FOLD = 1.45f
		private const val SETTLE_RATE = 0.05f
		private const val SETTLE = 0.05f

		fun createLayer(): LayerDefinition {
			val mesh = MeshDefinition()
			val root = mesh.root
			val none = CubeDeformation(0.0f)

			root.addOrReplaceChild(
				"body",
				CubeListBuilder.create()
					.texOffs(0, 0).addBox(-3.0f, -5.0f, -5.0f, 6.0f, 5.0f, 10.0f, none)
					.texOffs(44, 36).addBox(-3.5f, -6.0f, -6.0f, 7.0f, 3.0f, 3.0f, none)
					.texOffs(0, 36).addBox(-3.0f, -4.0f, 5.0f, 6.0f, 1.0f, 7.0f, none),
				PartPose.offset(0.0f, 16.0f, 0.0f),
			)

			root.addOrReplaceChild(
				"head",
				CubeListBuilder.create()
					.texOffs(27, 36).addBox(-2.0f, -3.0f, -4.0f, 4.0f, 4.0f, 4.0f, none)
					.texOffs(46, 45).addBox(-1.0f, -2.0f, -7.0f, 2.0f, 2.0f, 3.0f, none),
				PartPose.offset(0.0f, 11.0f, -5.0f),
			)

			root.addOrReplaceChild(
				"wing_right",
				CubeListBuilder.create()
					.texOffs(0, 16).addBox(-12.0f, -1.0f, -4.0f, 12.0f, 1.0f, 8.0f, none)
					.texOffs(0, 45).addBox(-12.0f, -1.0f, 4.0f, 6.0f, 1.0f, 5.0f, none),
				PartPose.offset(-3.0f, 12.0f, 0.0f),
			)

			root.addOrReplaceChild(
				"wing_left",
				CubeListBuilder.create()
					.texOffs(0, 26).addBox(0.0f, -1.0f, -4.0f, 12.0f, 1.0f, 8.0f, none)
					.texOffs(23, 45).addBox(6.0f, -1.0f, 4.0f, 6.0f, 1.0f, 5.0f, none),
				PartPose.offset(3.0f, 12.0f, 0.0f),
			)

			root.addOrReplaceChild(
				"leg_right",
				CubeListBuilder.create()
					.texOffs(57, 45).addBox(-0.5f, 0.0f, -1.0f, 1.0f, 3.0f, 2.0f, none),
				PartPose.offset(-1.5f, 16.0f, 0.0f),
			)

			root.addOrReplaceChild(
				"leg_left",
				CubeListBuilder.create()
					.texOffs(0, 52).addBox(-0.5f, 0.0f, -1.0f, 1.0f, 3.0f, 2.0f, none),
				PartPose.offset(1.5f, 16.0f, 0.0f),
			)

			return LayerDefinition.create(mesh, 64, 64)
		}
	}
}
