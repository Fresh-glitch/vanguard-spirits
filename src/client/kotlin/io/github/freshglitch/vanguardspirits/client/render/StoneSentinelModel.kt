package io.github.freshglitch.vanguardspirits.client.render

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.entity.StoneSentinel
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
		rest()

		when {
			state.dormant -> return
			state.wakeTick > 0 -> waking(state)
			else -> {
				hunting(state)
				if (state.attackKind != StoneSentinel.NO_ATTACK) attacking(state)
			}
		}
	}

	/** The statue pose, and the base every other pose is written over. */
	private fun rest() {
		head.xRot = 0.0f; head.yRot = 0.0f; head.zRot = 0.0f
		body.xRot = 0.0f; body.yRot = 0.0f
		armRight.xRot = 0.0f; armRight.yRot = 0.0f; armRight.zRot = 0.0f
		armLeft.xRot = 0.0f; armLeft.yRot = 0.0f; armLeft.zRot = 0.0f
		legRight.xRot = 0.0f
		legLeft.xRot = 0.0f
	}

	/**
	 * The waking sequence, keyed off the same tick counter the server uses for
	 * its sound cues, so movement and audio cannot drift apart.
	 *
	 * Three beats: a twitch of the head that says something is alive in there, a
	 * long grinding straighten as it takes its own weight, then the head coming
	 * up to find whoever woke it.
	 */
	private fun waking(state: StoneSentinelRenderState) {
		val t = state.wakeTick.toFloat()

		if (state.wakeTick < StoneSentinel.STIR_AT) {
			// One sharp jerk that rings out. Nothing else moves yet -- the whole
			// point is that the body is still stone at this moment.
			val since = (t - StoneSentinel.TWITCH_AT).coerceAtLeast(0.0f)
			val decay = (1.0f - since / (StoneSentinel.STIR_AT - StoneSentinel.TWITCH_AT)).coerceAtLeast(0.0f)
			head.yRot = Mth.cos((since * TWITCH_RATE).toDouble()) * TWITCH * decay * decay
			head.xRot = TWITCH_DIP * decay
			return
		}

		if (state.wakeTick < StoneSentinel.ROAR_AT) {
			// Taking its weight: shoulders roll out, knees give a little, and the
			// head hangs before it lifts.
			val p = ease(
				(t - StoneSentinel.STIR_AT) / (StoneSentinel.ROAR_AT - StoneSentinel.STIR_AT).toFloat(),
			)
			val grind = Mth.cos((t * GRIND_RATE).toDouble()) * GRIND * (1.0f - p)

			body.xRot = Mth.lerp(p, 0.0f, HUNCH) + grind
			head.xRot = Mth.lerp(p, 0.0f, HEAD_HANG) - grind
			armRight.zRot = Mth.lerp(p, 0.0f, -SHOULDER_ROLL) + grind
			armLeft.zRot = Mth.lerp(p, 0.0f, SHOULDER_ROLL) - grind
			armRight.xRot = Mth.lerp(p, 0.0f, -ARM_FLEX)
			armLeft.xRot = Mth.lerp(p, 0.0f, -ARM_FLEX)
			legRight.xRot = Mth.lerp(p, 0.0f, KNEE)
			legLeft.xRot = Mth.lerp(p, 0.0f, -KNEE)
			return
		}

		// The roar: head snaps up and back, arms thrown wide, then it settles.
		val p = ease((t - StoneSentinel.ROAR_AT) / (StoneSentinel.WAKE_TICKS - StoneSentinel.ROAR_AT).toFloat())
		val flare = Mth.sin((p * Mth.PI).toDouble())

		body.xRot = Mth.lerp(p, HUNCH, 0.0f) - flare * ROAR_ARCH
		head.xRot = Mth.lerp(p, HEAD_HANG, 0.0f) - flare * ROAR_LOOK
		armRight.zRot = Mth.lerp(p, -SHOULDER_ROLL, 0.0f) - flare * ROAR_SPREAD
		armLeft.zRot = Mth.lerp(p, SHOULDER_ROLL, 0.0f) + flare * ROAR_SPREAD
		armRight.xRot = Mth.lerp(p, -ARM_FLEX, 0.0f)
		armLeft.xRot = Mth.lerp(p, -ARM_FLEX, 0.0f)
		legRight.xRot = Mth.lerp(p, KNEE, 0.0f)
		legLeft.xRot = Mth.lerp(p, -KNEE, 0.0f)
	}

	/** Walking and looking around, once it is up. */
	private fun hunting(state: StoneSentinelRenderState) {
		head.xRot = state.xRot * Mth.DEG_TO_RAD
		head.yRot = state.yRot * Mth.DEG_TO_RAD

		val swing = state.walkAnimationPos * STRIDE_RATE
		val amount = state.walkAnimationSpeed

		legRight.xRot = Mth.cos(swing.toDouble()) * LEG_SWING * amount
		legLeft.xRot = Mth.cos((swing + Mth.PI).toDouble()) * LEG_SWING * amount
		armRight.xRot = Mth.cos((swing + Mth.PI).toDouble()) * ARM_SWING * amount
		armLeft.xRot = Mth.cos(swing.toDouble()) * ARM_SWING * amount

		// A slow list from side to side, so it never stands perfectly straight
		// once it is awake.
		val sway = Mth.cos((state.ageInTicks * SWAY_RATE).toDouble()) * SWAY
		armRight.zRot = -sway
		armLeft.zRot = sway
		body.xRot = LEAN
	}

	/**
	 * The telegraphs.
	 *
	 * Both attacks spend most of their length winding up and only a few ticks
	 * landing, which is what gives a player time to read them and move. Written
	 * over the walk pose, so the legs keep whatever they were doing.
	 */
	private fun attacking(state: StoneSentinelRenderState) {
		val t = state.attackTick.toFloat()

		if (state.attackKind == StoneSentinel.SLAM) {
			val windup = StoneSentinel.SLAM_WINDUP.toFloat()
			if (t < windup) {
				// Both fists hauled overhead. Slow, so it is unmistakable.
				val p = ease(t / windup)
				armRight.xRot = Mth.lerp(p, 0.0f, -SLAM_RAISE)
				armLeft.xRot = Mth.lerp(p, 0.0f, -SLAM_RAISE)
				armRight.zRot = Mth.lerp(p, 0.0f, -SLAM_GATHER)
				armLeft.zRot = Mth.lerp(p, 0.0f, SLAM_GATHER)
				body.xRot = Mth.lerp(p, 0.0f, -SLAM_ARCH)
				head.xRot = Mth.lerp(p, 0.0f, -SLAM_ARCH)
			} else {
				// Down, and stay down through the recovery.
				val p = ((t - windup) / SLAM_STRIKE).coerceIn(0.0f, 1.0f)
				armRight.xRot = Mth.lerp(p, -SLAM_RAISE, SLAM_FOLLOW)
				armLeft.xRot = Mth.lerp(p, -SLAM_RAISE, SLAM_FOLLOW)
				armRight.zRot = Mth.lerp(p, -SLAM_GATHER, 0.0f)
				armLeft.zRot = Mth.lerp(p, SLAM_GATHER, 0.0f)
				body.xRot = Mth.lerp(p, -SLAM_ARCH, SLAM_STOOP)
				head.xRot = Mth.lerp(p, -SLAM_ARCH, SLAM_STOOP)
			}
			return
		}

		if (state.attackKind == StoneSentinel.RECKONING) {
			val windup = StoneSentinel.RECKONING_WINDUP.toFloat()
			if (t < windup) {
				// Arms opening wide with the head thrown back -- reaching for
				// something out of range rather than hammering what is in front.
				// Nothing like the slam's overhead gather, so the two read apart
				// at a glance.
				val p = ease(t / windup)
				armRight.zRot = Mth.lerp(p, 0.0f, -REACH_SPREAD)
				armLeft.zRot = Mth.lerp(p, 0.0f, REACH_SPREAD)
				armRight.xRot = Mth.lerp(p, 0.0f, -REACH_LIFT)
				armLeft.xRot = Mth.lerp(p, 0.0f, -REACH_LIFT)
				head.xRot = Mth.lerp(p, 0.0f, -REACH_LOOK)
				body.xRot = Mth.lerp(p, 0.0f, -REACH_ARCH)
			} else {
				// The wrench: arms clamp in and down, and it hunches over the
				// pull as though hauling on a chain.
				val p = ((t - windup) / RECKON_STRIKE).coerceIn(0.0f, 1.0f)
				armRight.zRot = Mth.lerp(p, -REACH_SPREAD, RECKON_CLAMP)
				armLeft.zRot = Mth.lerp(p, REACH_SPREAD, -RECKON_CLAMP)
				armRight.xRot = Mth.lerp(p, -REACH_LIFT, RECKON_HAUL)
				armLeft.xRot = Mth.lerp(p, -REACH_LIFT, RECKON_HAUL)
				head.xRot = Mth.lerp(p, -REACH_LOOK, RECKON_STOOP)
				body.xRot = Mth.lerp(p, -REACH_ARCH, RECKON_STOOP)
			}
			return
		}

		if (state.attackKind == StoneSentinel.SUNDER) {
			val windup = StoneSentinel.SUNDER_WINDUP.toFloat()
			if (t < windup) {
				// The right arm hauled back behind the shoulder while the body
				// coils after it, shaking harder the further it is wound. A long
				// draw against a very short release is what makes the punch snap
				// rather than swing.
				val p = ease(t / windup)
				val tremor = Mth.cos((t * BRACE_RATE).toDouble()) * BRACE_SHAKE * p
				armRight.xRot = Mth.lerp(p, 0.0f, BRACE_DRAW) + tremor
				armRight.zRot = Mth.lerp(p, 0.0f, -BRACE_FLARE)
				armLeft.xRot = Mth.lerp(p, 0.0f, -BRACE_COUNTER)
				body.yRot = Mth.lerp(p, 0.0f, BRACE_COIL)
				head.yRot = Mth.lerp(p, 0.0f, -BRACE_COIL)
				body.xRot = Mth.lerp(p, 0.0f, BRACE_CROUCH)
			} else {
				// Straight out, and it stays out through the recovery -- the arm
				// left buried in the hole it just made says what happened better
				// than any amount of follow-through.
				val p = ((t - windup) / SUNDER_STRIKE).coerceIn(0.0f, 1.0f)
				armRight.xRot = Mth.lerp(p, BRACE_DRAW, -THRUST)
				armRight.zRot = Mth.lerp(p, -BRACE_FLARE, 0.0f)
				armLeft.xRot = Mth.lerp(p, -BRACE_COUNTER, THRUST * 0.25f)
				body.yRot = Mth.lerp(p, BRACE_COIL, -BRACE_COIL * 0.6f)
				head.yRot = Mth.lerp(p, -BRACE_COIL, 0.0f)
				body.xRot = Mth.lerp(p, BRACE_CROUCH, -THRUST_LEAN)
			}
			return
		}

		// Sweep: one arm cocked across the chest, then thrown outward.
		val windup = StoneSentinel.SWEEP_WINDUP.toFloat()
		if (t < windup) {
			val p = ease(t / windup)
			armRight.yRot = Mth.lerp(p, 0.0f, SWEEP_COCK)
			armRight.xRot = Mth.lerp(p, 0.0f, -SWEEP_LIFT)
			body.yRot = Mth.lerp(p, 0.0f, SWEEP_TWIST)
		} else {
			val p = ((t - windup) / SWEEP_STRIKE).coerceIn(0.0f, 1.0f)
			armRight.yRot = Mth.lerp(p, SWEEP_COCK, -SWEEP_COCK)
			armRight.xRot = Mth.lerp(p, -SWEEP_LIFT, -SWEEP_LIFT * 0.4f)
			body.yRot = Mth.lerp(p, SWEEP_TWIST, -SWEEP_TWIST)
		}
	}

	/** Smoothstep. Movement that starts and stops abruptly reads as a glitch. */
	private fun ease(t: Float): Float {
		val c = t.coerceIn(0.0f, 1.0f)
		return c * c * (3.0f - 2.0f * c)
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

		// ---- waking ----

		/** Radians per tick of the twitch's ring-out, and how far it throws the head. */
		private const val TWITCH_RATE = 1.5f
		private const val TWITCH = 0.42f
		private const val TWITCH_DIP = 0.10f

		/** A grinding tremor through the straighten, fading as it takes its weight. */
		private const val GRIND_RATE = 1.1f
		private const val GRIND = 0.035f

		private const val HUNCH = 0.30f
		private const val HEAD_HANG = 0.42f
		private const val SHOULDER_ROLL = 0.30f
		private const val ARM_FLEX = 0.25f
		private const val KNEE = 0.22f

		private const val ROAR_ARCH = 0.24f
		private const val ROAR_LOOK = 0.55f
		private const val ROAR_SPREAD = 0.50f

		// ---- attacks ----

		/** Ticks the fists take to come down. Short, against a 20-tick raise. */
		private const val SLAM_STRIKE = 3.0f
		private const val SLAM_RAISE = 2.5f
		private const val SLAM_GATHER = 0.35f
		private const val SLAM_ARCH = 0.22f
		private const val SLAM_FOLLOW = 0.55f
		private const val SLAM_STOOP = 0.30f

		/** Reckoning: opening wide, then hauling in. */
		private const val RECKON_STRIKE = 4.0f
		private const val REACH_SPREAD = 1.35f
		private const val REACH_LIFT = 1.10f
		private const val REACH_LOOK = 0.60f
		private const val REACH_ARCH = 0.22f
		private const val RECKON_CLAMP = 0.30f
		private const val RECKON_HAUL = 0.70f
		private const val RECKON_STOOP = 0.35f

		/** Sunder: winding the arm back, then putting it through the wall. */
		private const val SUNDER_STRIKE = 2.5f
		private const val BRACE_DRAW = 1.25f
		private const val BRACE_FLARE = 0.28f
		private const val BRACE_COUNTER = 0.45f
		private const val BRACE_COIL = 0.35f
		private const val BRACE_CROUCH = 0.18f
		private const val BRACE_RATE = 0.9f
		private const val BRACE_SHAKE = 0.05f
		private const val THRUST = 1.55f
		private const val THRUST_LEAN = 0.20f

		private const val SWEEP_STRIKE = 3.0f
		private const val SWEEP_COCK = 1.5f
		private const val SWEEP_LIFT = 0.9f
		private const val SWEEP_TWIST = 0.30f

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
