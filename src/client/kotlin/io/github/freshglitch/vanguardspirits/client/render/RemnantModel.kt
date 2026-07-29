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
 * The Remnant's geometry.
 *
 * **A transcription of Blockbench's Java export, not hand-authored.** The
 * source of truth is `blockbench/remnant.bbmodel`; edit there and re-export
 * rather than adjusting the numbers below, or the texture will land on the
 * wrong faces.
 */
class RemnantModel(root: ModelPart) : EntityModel<RemnantRenderState>(root) {

	private val head: ModelPart = root.getChild("head")
	private val body: ModelPart = root.getChild("body")
	private val armRight: ModelPart = root.getChild("arm_right")
	private val armLeft: ModelPart = root.getChild("arm_left")
	private val legRight: ModelPart = root.getChild("leg_right")
	private val legLeft: ModelPart = root.getChild("leg_left")

	override fun setupAnim(state: RemnantRenderState) {
		super.setupAnim(state)

		head.xRot = state.xRot * Mth.DEG_TO_RAD
		head.yRot = state.yRot * Mth.DEG_TO_RAD

		val swing = state.walkAnimationPos * STRIDE_RATE
		val amount = state.walkAnimationSpeed

		legRight.xRot = Mth.cos(swing.toDouble()) * LEG_SWING * amount
		legLeft.xRot = Mth.cos((swing + Mth.PI).toDouble()) * LEG_SWING * amount

		// Arms held up and forward rather than swinging with the stride. It runs
		// with them raised, which is what separates it at a glance from anything
		// else moving in the dark.
		val reach = Mth.cos((state.ageInTicks * TWITCH_RATE).toDouble()) * TWITCH
		armRight.xRot = -ARMS_UP + reach
		armLeft.xRot = -ARMS_UP - reach
		armRight.zRot = ARMS_OUT
		armLeft.zRot = -ARMS_OUT

		// A permanent stoop. It never straightened up after whatever happened.
		body.xRot = STOOP
	}

	companion object {
		val LAYER: ModelLayerLocation =
			ModelLayerLocation(VanguardSpirits.id("remnant"), "main")

		private const val STRIDE_RATE = 0.6662f

		/** Longer stride than the sentinel's. It covers ground. */
		private const val LEG_SWING = 1.5f

		private const val ARMS_UP = 1.15f
		private const val ARMS_OUT = 0.16f

		private const val TWITCH_RATE = 0.28f
		private const val TWITCH = 0.14f

		private const val STOOP = 0.22f

		fun createLayer(): LayerDefinition {
			val mesh = MeshDefinition()
			val root = mesh.root
			val none = CubeDeformation(0.0f)

			root.addOrReplaceChild(
				"leg_right",
				CubeListBuilder.create()
					.texOffs(63, 0).addBox(-1.5f, 0.0f, -2.0f, 3.0f, 12.0f, 4.0f, none),
				PartPose.offset(-2.5f, 12.0f, 0.0f),
			)

			root.addOrReplaceChild(
				"leg_left",
				CubeListBuilder.create()
					.texOffs(78, 0).addBox(-1.5f, 0.0f, -2.0f, 3.0f, 12.0f, 4.0f, none),
				PartPose.offset(2.5f, 12.0f, 0.0f),
			)

			// Ribs and jaw are painted into the texture rather than modelled.
			// Bolted on as their own cubes they read as slabs stuck to the front;
			// as shading they leave the silhouette clean.
			root.addOrReplaceChild(
				"body",
				CubeListBuilder.create()
					.texOffs(30, 0).addBox(-5.0f, -12.0f, -3.0f, 10.0f, 12.0f, 6.0f, none)
					.texOffs(0, 21).addBox(-6.0f, -13.0f, 3.0f, 12.0f, 11.0f, 2.0f, none),
				PartPose.offset(0.0f, 12.0f, 0.0f),
			)

			root.addOrReplaceChild(
				"head",
				CubeListBuilder.create()
					.texOffs(93, 0).addBox(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f, none),
				PartPose.offset(0.0f, 0.0f, 0.0f),
			)

			root.addOrReplaceChild(
				"arm_right",
				CubeListBuilder.create()
					.texOffs(0, 0).addBox(-2.0f, -1.0f, -2.0f, 3.0f, 16.0f, 4.0f, none)
					.texOffs(29, 21).addBox(-3.0f, -2.0f, -3.0f, 5.0f, 4.0f, 6.0f, none)
					.texOffs(75, 21).addBox(-2.0f, 15.0f, -2.0f, 4.0f, 4.0f, 4.0f, none),
				PartPose.offset(-6.0f, 1.0f, 0.0f),
			)

			root.addOrReplaceChild(
				"arm_left",
				CubeListBuilder.create()
					.texOffs(15, 0).addBox(-1.0f, -1.0f, -2.0f, 3.0f, 16.0f, 4.0f, none)
					.texOffs(52, 21).addBox(-2.0f, -2.0f, -3.0f, 5.0f, 4.0f, 6.0f, none)
					.texOffs(92, 21).addBox(-2.0f, 15.0f, -2.0f, 4.0f, 4.0f, 4.0f, none),
				PartPose.offset(6.0f, 1.0f, 0.0f),
			)

			return LayerDefinition.create(mesh, 128, 128)
		}
	}
}
