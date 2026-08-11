package io.github.freshglitch.vanguardspirits.client.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.block.GoldenChestBlock
import io.github.freshglitch.vanguardspirits.block.entity.GoldenChestBlockEntity
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.core.Direction
import net.minecraft.world.phys.Vec3

/**
 * Draws the Gilded Reliquary and swings its lid.
 *
 * The body and lid are separate parts of one baked model; only the lid is
 * rotated, about the hinge pivot baked into [ChestParts].
 */
class GoldenChestRenderer(context: BlockEntityRendererProvider.Context) :
	BlockEntityRenderer<GoldenChestBlockEntity, GoldenChestRenderState> {

	private val model = ReliquaryModel(context.bakeLayer(ChestParts.LAYER))

	override fun createRenderState(): GoldenChestRenderState = GoldenChestRenderState()

	override fun extractRenderState(
		entity: GoldenChestBlockEntity,
		state: GoldenChestRenderState,
		partialTick: Float,
		cameraPos: Vec3,
		crumbling: ModelFeatureRenderer.CrumblingOverlay?,
	) {
		BlockEntityRenderState.extractBase(entity, state, crumbling)

		state.openness = entity.getOpenNess(partialTick)
		state.facing = entity.blockState.getValue(GoldenChestBlock.FACING)
	}

	override fun submit(
		state: GoldenChestRenderState,
		poseStack: PoseStack,
		collector: SubmitNodeCollector,
		camera: CameraRenderState,
	) {
		poseStack.pushPose()

		// Turn the whole chest to face its block state, about the block centre.
		//
		// The half turn is not cosmetic: ModelPart renders Y-down, so the flip
		// below is required, and without the extra 180 the chest ends up looking
		// away from the face it was placed against.
		poseStack.translate(0.5f, 0.0f, 0.5f)
		poseStack.mulPose(Axis.YP.rotationDegrees(HALF_TURN - state.facing.toYRot()))
		poseStack.translate(-0.5f, 0.0f, -0.5f)

		// No scale and no flip, both verified the hard way.
		//
		// ModelPart divides by 16 internally, so the geometry already arrives
		// block-scaled; scaling again renders the chest at 1/256 size. And there
		// is no automatic Y inversion for a block entity the way there is for
		// entities, so boxes authored Y-up in ChestParts render upright as they
		// stand. `scale(1, -1, 1)` followed by `translate(0, -1, 0)` composes to
		// y -> 1 - y, which flips the chest about the middle of its own block and
		// buries the ruby crown in the ground.

		// The openness goes in as the model's *state*, not as a field poked into
		// a shared ModelPart before submitting it. Drawing here is deferred: the
		// node keeps this value and calls `setupAnim` with it just before it is
		// drawn, so two Reliquaries in one frame cannot end up sharing a lid
		// angle. See [ReliquaryModel] for what that looked like when they did.
		collector.submitModel(
			model,
			state.openness,
			poseStack,
			RenderTypes.entityCutout(TEXTURE),
			state.lightCoords,
			OVERLAY,
			0,
			state.breakProgress,
		)

		poseStack.popPose()
	}

	companion object {
		/**
		 * Lives under textures/block, not textures/entity, because the item's
		 * block model has to reference the same sheet -- and a block model can
		 * only name textures that are stitched into the block atlas. The renderer
		 * loads it by path either way, so block/ is the location that serves both.
		 */
		val TEXTURE = VanguardSpirits.id("textures/block/golden_chest.png")

		private const val HALF_TURN = 180.0f

		private val OVERLAY = OverlayTexture.NO_OVERLAY
	}
}
