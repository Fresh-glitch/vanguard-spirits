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
 * The Nymph's geometry.
 *
 * **A transcription of Blockbench's Java export.** The source is
 * `blockbench/nymph.bbmodel`; edit there and re-export.
 *
 * ## The skeleton
 *
 * Sixteen bones, and every one of them earns its place by being *moved* in
 * [setupAnim] -- a joint nothing bends is just an extra name.
 *
 * ```
 * hips ── torso ── neck ── head ── hair
 * arm_right ── forearm_right          (elbow)
 * arm_left  ── forearm_left
 * leg_right ── shin_right ── foot_right   (knee, ankle)
 * leg_left  ── shin_left  ── foot_left
 * sash
 * ```
 *
 * The legs hang off the root rather than off the hips, which is vanilla's
 * arrangement for a humanoid and is what keeps the walk simple: the hips can
 * bob and the torso can twist without either of them dragging the legs along.
 *
 * She is thirty-two units tall and almost all of it is limb. That is the whole
 * reason she reads at a distance, and it was arrived at the hard way -- the
 * first two drafts gave her a gown, and a gown at this resolution is a box.
 *
 * ## The outer layer
 *
 * Seven of these boxes appear twice: once plain and once inflated, mapped to
 * their own region of the sheet, exactly as a player skin carries a hat, a
 * jacket and sleeves. Everything the garment does not cover is transparent, and
 * an entity model's render type is a cutout, so the alpha is a straight
 * keep-or-discard.
 *
 * The wraps sit on the *forearms* and the moss on the *shins* rather than on
 * whole limbs, because a band drawn across a joint tears in half the moment the
 * joint bends.
 */
class NymphModel(root: ModelPart) : EntityModel<NymphRenderState>(root) {

	// Every bone, held whether or not this class poses it by hand. The walk now
	// drives most of them *by name* through the baked animation, so these
	// lookups are what turns a bone renamed in Blockbench into a loud failure
	// at bake time rather than a limb that quietly stops moving.
	private val hips: ModelPart = root.getChild("hips")
	private val torso: ModelPart = hips.getChild("torso")
	private val neck: ModelPart = torso.getChild("neck")
	private val head: ModelPart = neck.getChild("head")
	private val hair: ModelPart = head.getChild("hair")

	private val armRight: ModelPart = root.getChild("arm_right")
	private val armLeft: ModelPart = root.getChild("arm_left")
	private val forearmRight: ModelPart = armRight.getChild("forearm_right")
	private val forearmLeft: ModelPart = armLeft.getChild("forearm_left")

	private val legRight: ModelPart = root.getChild("leg_right")
	private val legLeft: ModelPart = root.getChild("leg_left")
	private val shinRight: ModelPart = legRight.getChild("shin_right")
	private val shinLeft: ModelPart = legLeft.getChild("shin_left")
	private val footRight: ModelPart = shinRight.getChild("foot_right")
	private val footLeft: ModelPart = shinLeft.getChild("foot_left")

	private val sash: ModelPart = root.getChild("sash")

	/**
	 * Both gaits, baked once against this model's parts.
	 *
	 * Baking resolves the bone *names* in the definition to the actual
	 * `ModelPart`s here, so it has to happen per instance and only once --
	 * doing it per frame would rebuild the whole lookup sixty times a second.
	 */
	private val walkAnimation = NymphAnimation.WALK.bake(root)
	private val runAnimation = NymphAnimation.RUN.bake(root)

	override fun setupAnim(state: NymphRenderState) {
		// `Model.setupAnim` calls `resetPose()`, which restores every part to its
		// PartPose -- checked in the bytecode, not assumed, because everything
		// below *adds* to what it finds and would otherwise wind a little
		// further round on every frame.
		super.setupAnim(state)

		stride(state)

		// Everything from here on adds to the animation rather than replacing
		// it, so a breath still moves her while she walks and a look still
		// reaches its target while her torso is twisting under it.
		val breath = Mth.sin((state.ageInTicks * DRIFT_RATE).toDouble())
		torso.xRot += breath * BREATH
		sash.zRot += breath * SASH_SWAY
		hair.xRot += breath * HAIR_DRIFT

		look(state)

		if (state.wrath > 0.0f) wrathen(state, state.wrath)
	}

	/**
	 * The walk and the run, mixed.
	 *
	 * This is `applyWalk` taken apart and put back together, because that method
	 * decides the blend weight itself and there is no way to ask it for a
	 * fraction. Its body is two lines -- `timeMs = limbSwing * 50 * rate` and
	 * `weight = min(limbSwingAmount * blend, 1)` -- and then it calls
	 * `apply(long, float)`, which is public. So the same two numbers are
	 * computed here and the weight is *split* between the two gaits.
	 *
	 * Two things make this a real crossfade rather than a dissolve between two
	 * unrelated poses.
	 *
	 * **The animations add rather than overwrite.** `AnimationChannel.Targets.
	 * ROTATION` is `ModelPart::offsetRotation`, whose body is three `+=` -- so
	 * applying two definitions at complementary weights sums to a genuine linear
	 * blend of the two poses, and each is a delta on the rest pose rather than
	 * an absolute replacement of it. (Which is also why the arms keep the small
	 * outward roll baked into their `PartPose` throughout.)
	 *
	 * **They share `timeMs`.** Both cycles are one second long and both put the
	 * right thigh at its rearmost at t=0, so mid-blend the two agree about where
	 * in the stride she is. Give them separate rates and the legs would drift
	 * out of phase and cancel each other halfway through every gait change.
	 *
	 * The shared rate is [WALK_RATE] for both, and the run therefore gains its
	 * cadence purely from her moving faster. That is a compromise -- a real run
	 * lengthens the stride as well -- but rate appears inside `timeMs`, which is
	 * multiplied by an ever-growing `walkAnimationPos`, so changing it would
	 * jump the phase by an arbitrary amount rather than easing it.
	 */
	private fun stride(state: NymphRenderState) {
		// How much she is moving at all. Standing still this is zero and neither
		// gait contributes anything, which is what leaves the idle underneath
		// visible rather than fighting it.
		val amount = Mth.clamp(state.walkAnimationSpeed * WALK_BLEND, 0.0f, 1.0f)
		if (amount <= 0.0f) return

		val timeMs = (state.walkAnimationPos * MS_PER_TICK * WALK_RATE).toLong()
		val run = state.run

		if (run < 1.0f) walkAnimation.apply(timeMs, amount * (1.0f - run))
		if (run > 0.0f) runAnimation.apply(timeMs, amount * run)
	}

	/**
	 * Where she is looking, spent across the neck and the head.
	 *
	 * Giving the whole turn to the head makes it swivel on a post. Splitting it
	 * means the neck leans into a look and the skull finishes it, which is what
	 * the neck joint was added for.
	 *
	 * What is added is the **residual** -- how far short of the target the
	 * animation has already left her. Head, neck and torso compose, and the walk
	 * turns all three, so applying the raw look on top of it would carry her
	 * well past whatever she meant to look at. Subtracting what is already there
	 * lands the total exactly on the target while leaving the walk's own sway
	 * intact underneath. Called after the animation for that reason.
	 */
	private fun look(state: NymphRenderState) {
		val yaw = state.yRot * Mth.DEG_TO_RAD - (torso.yRot + neck.yRot)
		val pitch = state.xRot * Mth.DEG_TO_RAD - (torso.xRot + neck.xRot)

		neck.yRot += yaw * NECK_SHARE
		neck.xRot += pitch * NECK_SHARE
		head.yRot += yaw * (1.0f - NECK_SHARE)
		head.xRot += pitch * (1.0f - NECK_SHARE)
	}

	/**
	 * What being wronged does to her posture.
	 *
	 * Blended rather than switched, because the temper it follows is itself
	 * eased on the entity -- a guardian who snapped from standing to raised arms
	 * in one frame read as a glitch rather than as anger.
	 *
	 * The arms go **out**, which is worth stating because getting it backwards
	 * is this repo's most-repeated modelling bug: `ModelPart` composes with
	 * `Quaternionf.rotationZYX`, so for an arm whose bone sits at model x = -3.5
	 * with its cubes hanging down +y, positive `zRot` carries the hand away from
	 * the body. Away is therefore **positive on the right, negative on the
	 * left**, and four animations in this mod once shipped inverted for exactly
	 * this reason.
	 *
	 * The arms give way to the run, and only the arms. This pose was authored
	 * for a Nymph standing or walking, and the two want the limbs for opposite
	 * purposes -- wrath spreads them wide and opens the elbows, a run tucks them
	 * in and locks the elbows at a right angle to pump. Applied on top of one
	 * another they average into a flail. The lean and the chin conflict with
	 * nothing, so they stay at full strength and go on saying what the pose is
	 * for while she runs.
	 */
	private fun wrathen(state: NymphRenderState, wrath: Float) {
		val tremor = Mth.sin((state.ageInTicks * TREMOR_RATE).toDouble()) * TREMOR * wrath
		val armed = wrath * (1.0f - state.run)

		armRight.zRot += (WRATH_SPREAD + tremor) * armed
		armLeft.zRot -= (WRATH_SPREAD + tremor) * armed
		armRight.xRot -= WRATH_RAISE * armed
		armLeft.xRot -= WRATH_RAISE * armed

		// Elbows open out of their resting bend. She is reaching, not braced.
		forearmRight.xRot += WRATH_ELBOW * armed
		forearmLeft.xRot += WRATH_ELBOW * armed

		// Chin up and shoulders back. She is not squaring up to swing at anyone;
		// she is a thing that has decided about you.
		torso.xRot -= WRATH_LEAN * wrath
		neck.xRot -= WRATH_CHIN * wrath
	}

	companion object {
		val LAYER: ModelLayerLocation = ModelLayerLocation(VanguardSpirits.id("nymph"), "main")

		/**
		 * How fast the cycle runs against how far she has walked.
		 *
		 * `applyWalk` is misleadingly named in its third argument: the bytecode
		 * reads `time = (long)(limbSwing * 50 * arg3)` in milliseconds, so this
		 * is a *rate*, not an angle. The animation is one second long, so a full
		 * stride takes a `walkAnimationPos` of `20 / WALK_RATE`. At 2.0 that is
		 * ten, which for her movement speed is an unhurried pace of a bit over a
		 * second a cycle -- which is the entire character of the walk, so it is
		 * the first number to reach for if she ever looks like she is scurrying.
		 */
		private const val WALK_RATE = 2.0f

		/** Amplitude against `walkAnimationSpeed`; the product is clamped to 1. */
		private const val WALK_BLEND = 2.5f

		/**
		 * What `applyWalk` multiplies by, spelled out because we do its job now.
		 *
		 * Its first line is `(long)(limbSwing * 50.0f * rate)` -- fifty
		 * milliseconds being one tick, so `walkAnimationPos` is being read as a
		 * count of ticks travelled and turned into a time.
		 */
		private const val MS_PER_TICK = 50.0f

		/** How much of a look the neck takes before the head finishes it. */
		private const val NECK_SHARE = 0.35f

		private const val DRIFT_RATE = 0.035f
		private const val BREATH = 0.04f
		private const val HAIR_DRIFT = 0.05f
		private const val SASH_SWAY = 0.06f
		private const val SASH_LAG = 0.5f

		private const val WRATH_SPREAD = 0.55f
		private const val WRATH_RAISE = 0.35f
		private const val WRATH_ELBOW = 0.30f
		private const val WRATH_LEAN = 0.10f
		private const val WRATH_CHIN = 0.12f
		private const val TREMOR_RATE = 0.9f
		private const val TREMOR = 0.06f

		fun createLayer(): LayerDefinition {
			val mesh = MeshDefinition()
			val root = mesh.root
			val none = CubeDeformation(0.0f)

			// The outer layer's inflations.
			//
			// HOOD is 0.55 and not the round 0.5 it started at. At 0.5 its faces
			// landed exactly on x = +-3.0, y = 31.5 and z = +-3.0 -- precisely
			// where the hair cap's and the hair fall's faces already were.
			// Coplanar faces z-fight, and the back of her head flickered. Taking
			// the inflation off a round number puts the hood just outside
			// everything it covers instead of exactly on it.
			val hood = CubeDeformation(0.55f)
			val mantle = CubeDeformation(0.45f)
			val wrap = CubeDeformation(0.35f)

			val hips = root.addOrReplaceChild(
				"hips",
				CubeListBuilder.create()
					.texOffs(32, 24).addBox(-1.5f, -3.0f, -1.5f, 3.0f, 4.0f, 3.0f, none),
				PartPose.offset(0.0f, 9.0f, 0.0f),
			)

			val torso = hips.addOrReplaceChild(
				"torso",
				CubeListBuilder.create()
					.texOffs(0, 14).addBox(-2.5f, -6.0f, -2.0f, 5.0f, 6.0f, 4.0f, none)
					.texOffs(18, 14).addBox(-2.5f, -6.0f, -2.0f, 5.0f, 6.0f, 4.0f, mantle),
				PartPose.offset(0.0f, -2.0f, 0.0f),
			)

			val neck = torso.addOrReplaceChild(
				"neck",
				CubeListBuilder.create()
					.texOffs(80, 24).addBox(-1.0f, -3.0f, -1.0f, 2.0f, 3.0f, 2.0f, none),
				PartPose.offset(0.0f, -6.0f, 0.0f),
			)

			// The skull, the hair cap growing over it, and the hood.
			//
			// The cap is a box on the head rather than on the `hair` bone
			// deliberately: it has to stay rigid with the skull it sits on.
			val head = neck.addOrReplaceChild(
				"head",
				CubeListBuilder.create()
					.texOffs(80, 0).addBox(-2.5f, -5.0f, -2.5f, 5.0f, 5.0f, 5.0f, none)
					.texOffs(8, 24).addBox(-3.0f, -5.5f, -3.0f, 6.0f, 1.0f, 6.0f, none)
					.texOffs(100, 0).addBox(-2.5f, -5.0f, -2.5f, 5.0f, 5.0f, 5.0f, hood),
				PartPose.offset(0.0f, -3.0f, 0.0f),
			)

			head.addOrReplaceChild(
				"bloom_r1",
				CubeListBuilder.create()
					.texOffs(44, 24).addBox(-4.0f, -6.6f, -1.5f, 3.0f, 2.0f, 3.0f, none),
				PartPose.offsetAndRotation(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.2094f),
			)

			// The coronet. Six identical petals, one bone each because Blockbench
			// exports a rotated cube that way, differing only in the yaw that
			// spreads them around her brow.
			val coronet = listOf(
				"petal_b_r1" to Triple(88, 24, 0.0f),
				"petal_br_r1" to Triple(100, 24, -1.0472f),
				"petal_bl_r1" to Triple(112, 24, 1.0472f),
				"petal_fr_r1" to Triple(0, 32, -2.0944f),
				"petal_fl_r1" to Triple(12, 32, 2.0944f),
				"petal_f_r1" to Triple(24, 32, -3.1416f),
			)
			for ((name, spec) in coronet) {
				val (u, v, yaw) = spec
				head.addOrReplaceChild(
					name,
					CubeListBuilder.create()
						.texOffs(u, v).addBox(-1.5f, -0.5f, 1.5f, 3.0f, 1.0f, 3.0f, none),
					PartPose.offsetAndRotation(0.0f, -3.5f, 0.0f, 0.3491f, yaw, 0.0f),
				)
			}

			head.addOrReplaceChild(
				"hair",
				CubeListBuilder.create()
					.texOffs(0, 0).addBox(-2.5f, -0.8f, 0.6f, 5.0f, 13.0f, 1.0f, none)
					.texOffs(12, 0).addBox(-4.2f, -1.0f, -4.0f, 1.0f, 9.0f, 4.0f, none)
					.texOffs(22, 0).addBox(3.2f, -1.0f, -4.0f, 1.0f, 9.0f, 4.0f, none),
				PartPose.offset(0.0f, -4.0f, 2.0f),
			)

			val armRight = root.addOrReplaceChild(
				"arm_right",
				CubeListBuilder.create()
					.texOffs(36, 14).addBox(-1.0f, -1.0f, -1.0f, 2.0f, 8.0f, 2.0f, none),
				PartPose.offsetAndRotation(-3.5f, 2.0f, 0.0f, 0.0f, 0.0f, 0.1222f),
			)

			armRight.addOrReplaceChild(
				"forearm_right",
				CubeListBuilder.create()
					.texOffs(68, 14).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 7.0f, 2.0f, none)
					.texOffs(76, 14).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 7.0f, 2.0f, wrap),
				PartPose.offset(0.0f, 7.0f, 0.0f),
			)

			val armLeft = root.addOrReplaceChild(
				"arm_left",
				CubeListBuilder.create()
					.texOffs(44, 14).addBox(-1.0f, -1.0f, -1.0f, 2.0f, 8.0f, 2.0f, none),
				PartPose.offsetAndRotation(3.5f, 2.0f, 0.0f, 0.0f, 0.0f, -0.1222f),
			)

			armLeft.addOrReplaceChild(
				"forearm_left",
				CubeListBuilder.create()
					.texOffs(84, 14).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 7.0f, 2.0f, none)
					.texOffs(92, 14).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 7.0f, 2.0f, wrap),
				PartPose.offset(0.0f, 7.0f, 0.0f),
			)

			val legRight = root.addOrReplaceChild(
				"leg_right",
				CubeListBuilder.create()
					.texOffs(52, 14).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 8.0f, 2.0f, none),
				PartPose.offset(-1.5f, 9.0f, 0.0f),
			)

			val shinRight = legRight.addOrReplaceChild(
				"shin_right",
				CubeListBuilder.create()
					.texOffs(100, 14).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 6.0f, 2.0f, none)
					.texOffs(108, 14).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 6.0f, 2.0f, wrap),
				PartPose.offset(0.0f, 8.0f, 0.0f),
			)

			shinRight.addOrReplaceChild(
				"foot_right",
				CubeListBuilder.create()
					.texOffs(56, 24).addBox(-1.0f, 0.0f, -3.0f, 2.0f, 1.0f, 4.0f, none),
				PartPose.offset(0.0f, 6.0f, 0.0f),
			)

			val legLeft = root.addOrReplaceChild(
				"leg_left",
				CubeListBuilder.create()
					.texOffs(60, 14).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 8.0f, 2.0f, none),
				PartPose.offset(1.5f, 9.0f, 0.0f),
			)

			val shinLeft = legLeft.addOrReplaceChild(
				"shin_left",
				CubeListBuilder.create()
					.texOffs(116, 14).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 6.0f, 2.0f, none)
					.texOffs(0, 24).addBox(-1.0f, 0.0f, -1.0f, 2.0f, 6.0f, 2.0f, wrap),
				PartPose.offset(0.0f, 8.0f, 0.0f),
			)

			shinLeft.addOrReplaceChild(
				"foot_left",
				CubeListBuilder.create()
					.texOffs(68, 24).addBox(-1.0f, 0.0f, -3.0f, 2.0f, 1.0f, 4.0f, none),
				PartPose.offset(0.0f, 6.0f, 0.0f),
			)

			root.addOrReplaceChild(
				"sash",
				CubeListBuilder.create()
					.texOffs(32, 0).addBox(-3.5f, -1.0f, -2.5f, 7.0f, 5.0f, 5.0f, none)
					.texOffs(56, 0).addBox(-3.5f, -1.0f, -2.5f, 7.0f, 5.0f, 5.0f, mantle),
				PartPose.offset(0.0f, 9.0f, 0.0f),
			)

			return LayerDefinition.create(mesh, 128, 128)
		}
	}
}
