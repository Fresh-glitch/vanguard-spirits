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
 * The Stone Sentinel's geometry.
 *
 * **This is a transcription of Blockbench's Java export, not hand-authored.**
 * The source of truth is `blockbench/stone_sentinel.bbmodel` in the repo; the
 * texOffs values below are whatever Blockbench packed the UVs to. Editing the
 * numbers here without touching the model puts the two out of step, and the
 * texture will paint onto the wrong faces -- reopen the .bbmodel, change it
 * there, and re-export.
 *
 * Bones are flat under the root rather than nested, matching vanilla's humanoid
 * layout, so limb swing works the same way it does for every other biped.
 */
class StoneSentinelModel(root: ModelPart) : EntityModel<StoneSentinelRenderState>(root) {

	private val head: ModelPart = root.getChild("head")
	private val body: ModelPart = root.getChild("body")
	private val armRight: ModelPart = root.getChild("arm_right")
	private val armLeft: ModelPart = root.getChild("arm_left")
	private val legRight: ModelPart = root.getChild("leg_right")
	private val legLeft: ModelPart = root.getChild("leg_left")

	override fun setupAnim(state: StoneSentinelRenderState) {
		super.setupAnim(state)

		// Every rotation is assigned outright each frame. The parts are shared
		// between all sentinels on screen, so anything left unset would carry
		// over from whichever one was drawn last.
		if (state.dormant) {
			head.xRot = 0.0f
			head.yRot = 0.0f
			body.xRot = 0.0f
			armRight.xRot = 0.0f
			armLeft.xRot = 0.0f
			armRight.zRot = 0.0f
			armLeft.zRot = 0.0f
			legRight.xRot = 0.0f
			legLeft.xRot = 0.0f
			return
		}

		head.xRot = state.xRot * Mth.DEG_TO_RAD
		head.yRot = state.yRot * Mth.DEG_TO_RAD

		val swing = state.walkAnimationPos * STRIDE_RATE
		val amount = state.walkAnimationSpeed

		legRight.xRot = Mth.cos(swing.toDouble()) * LEG_SWING * amount
		legLeft.xRot = Mth.cos((swing + Mth.PI).toDouble()) * LEG_SWING * amount
		armRight.xRot = Mth.cos((swing + Mth.PI).toDouble()) * ARM_SWING * amount
		armLeft.xRot = Mth.cos(swing.toDouble()) * ARM_SWING * amount

		// A slow list from side to side, so it never stands perfectly straight
		// once it is awake. Two seconds a cycle, independent of walking.
		val sway = Mth.cos((state.ageInTicks * SWAY_RATE).toDouble()) * SWAY
		armRight.zRot = -sway
		armLeft.zRot = sway
		body.xRot = LEAN
	}

	companion object {
		val LAYER: ModelLayerLocation =
			ModelLayerLocation(VanguardSpirits.id("stone_sentinel"), "main")

		/** Radians per unit of limb swing. Vanilla's biped constant. */
		private const val STRIDE_RATE = 0.6662f

		/** Shorter steps than a player's -- it is carrying a lot of rock. */
		private const val LEG_SWING = 0.9f
		private const val ARM_SWING = 0.6f

		private const val SWAY_RATE = 0.08f
		private const val SWAY = 0.06f

		/** A permanent slight forward lean once it is hunting. */
		private const val LEAN = 0.05f

		fun createLayer(): LayerDefinition {
			val mesh = MeshDefinition()
			val root = mesh.root
			val none = CubeDeformation(0.0f)

			root.addOrReplaceChild(
				"leg_right",
				CubeListBuilder.create()
					.texOffs(53, 0).addBox(-3.0f, 0.0f, -3.0f, 5.0f, 14.0f, 6.0f, none)
					.texOffs(0, 59).addBox(-3.5f, 5.0f, -4.0f, 6.0f, 6.0f, 8.0f, none),
				PartPose.offset(-4.0f, 10.0f, 0.0f),
			)

			root.addOrReplaceChild(
				"leg_left",
				CubeListBuilder.create()
					.texOffs(76, 0).addBox(-2.0f, 0.0f, -3.0f, 5.0f, 14.0f, 6.0f, none)
					.texOffs(29, 59).addBox(-2.5f, 5.0f, -4.0f, 6.0f, 6.0f, 8.0f, none),
				PartPose.offset(4.0f, 10.0f, 0.0f),
			)

			root.addOrReplaceChild(
				"body",
				CubeListBuilder.create()
					.texOffs(58, 59).addBox(-6.0f, -5.0f, -4.0f, 12.0f, 5.0f, 8.0f, none)
					.texOffs(0, 0).addBox(-8.0f, -16.0f, -5.0f, 16.0f, 11.0f, 10.0f, none)
					.texOffs(29, 74).addBox(-8.0f, -16.0f, 5.0f, 16.0f, 10.0f, 2.0f, none)
					.texOffs(66, 74).addBox(-5.0f, -15.0f, -6.0f, 10.0f, 5.0f, 1.0f, none),
				PartPose.offset(0.0f, 10.0f, 0.0f),
			)

			root.addOrReplaceChild(
				"head",
				CubeListBuilder.create()
					.texOffs(0, 22).addBox(-5.0f, -10.0f, -5.0f, 10.0f, 10.0f, 10.0f, none)
					.texOffs(0, 43).addBox(-6.0f, -12.0f, -6.0f, 12.0f, 3.0f, 12.0f, none)
					.texOffs(89, 74).addBox(-5.0f, -8.0f, -6.0f, 10.0f, 2.0f, 1.0f, none),
				PartPose.offset(0.0f, -6.0f, 0.0f),
			)

			root.addOrReplaceChild(
				"arm_right",
				CubeListBuilder.create()
					.texOffs(41, 22).addBox(-4.0f, 0.0f, -3.0f, 5.0f, 13.0f, 6.0f, none)
					.texOffs(49, 43).addBox(-5.0f, -2.0f, -5.0f, 7.0f, 5.0f, 10.0f, none)
					.texOffs(99, 59).addBox(-4.5f, 12.0f, -4.0f, 6.0f, 5.0f, 8.0f, none),
				PartPose.offset(-9.0f, -5.0f, 0.0f),
			)

			root.addOrReplaceChild(
				"arm_left",
				CubeListBuilder.create()
					.texOffs(64, 22).addBox(-1.0f, 0.0f, -3.0f, 5.0f, 13.0f, 6.0f, none)
					.texOffs(84, 43).addBox(-2.0f, -2.0f, -5.0f, 7.0f, 5.0f, 10.0f, none)
					.texOffs(0, 74).addBox(-1.5f, 12.0f, -4.0f, 6.0f, 5.0f, 8.0f, none),
				PartPose.offset(9.0f, -5.0f, 0.0f),
			)

			return LayerDefinition.create(mesh, 128, 128)
		}
	}
}
