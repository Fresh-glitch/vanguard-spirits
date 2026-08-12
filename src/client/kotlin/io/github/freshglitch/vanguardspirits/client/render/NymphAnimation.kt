package io.github.freshglitch.vanguardspirits.client.render

import net.minecraft.client.animation.AnimationChannel
import net.minecraft.client.animation.AnimationDefinition
import net.minecraft.client.animation.Keyframe
import net.minecraft.client.animation.KeyframeAnimations

/**
 * The Nymph's walk.
 *
 * **A transcription of the `walk` animation in `blockbench/nymph.bbmodel`**,
 * which is the authority. Edit it there, re-read the keyframes, and regenerate;
 * do not tune the numbers here, because the next export will overwrite them and
 * because Blockbench's viewport is the only place this can be *seen* before it
 * reaches the game.
 *
 * ## The walk itself
 *
 * She is not a person in a hurry, and the cycle is built to say so.
 *
 * - **She rises rather than bobs.** The hips lift over the stance leg twice a
 *   cycle and never dip below their rest height, which reads as being carried
 *   rather than trudging.
 * - **She places each foot instead of dropping it.** The toe comes up before
 *   contact and the ankle flattens as the weight arrives -- a small thing that
 *   suits somebody whose idle line is "mind where you put your feet".
 * - **The knee folds deeply on the swing through** and barely at all under
 *   load, which is what a knee does and what a two-plank leg cannot.
 * - **Her head stays level** while the torso twists under it: the neck turns
 *   against the torso by roughly half its counter-rotation.
 *
 * ## Two conversions between the `.bbmodel` and here
 *
 * - **Rotations negate x and y and leave z**, the same rule the model's own
 *   bone rotations follow. Blockbench and model space differ by a mirror in x
 *   and a mirror in y, which composes to a half turn about z, and a rotation is
 *   an axial vector under it.
 * - **Positions negate x only**, because `KeyframeAnimations.posVec` already
 *   negates y itself -- its whole body is `new Vector3f(x, -y, z)`. Passing a
 *   Blockbench y straight through is therefore correct, and "fixing" that sign
 *   would sink her hips through the floor instead of lifting them.
 *
 * ## Which way a joint bends, settled once
 *
 * Blockbench's front is **-z**, and the model says so itself twice over: her
 * toes run from z -3 to +1, and her eyes are painted on the *north* face. No
 * render needed.
 *
 * From there a scene-graph measurement finishes it. Rotating `forearm_right` by
 * a Blockbench x of +30 and transforming the hand's local point through
 * `mesh.matrixWorld` puts it at z = -3.5, so **positive Blockbench x on a limb
 * swings the far end forward** and the stored keyframes here are positive for a
 * forward elbow. Squinting at a render got this wrong twice in both directions;
 * one arithmetic check settled it.
 *
 * Note what that implies for the Java values below: they come out *negative*,
 * because of the negation above. Both facts are true at once, and mistaking one
 * for the other is the entire history of this file.
 *
 * ## Interpolation
 *
 * Every keyframe is `CATMULLROM`. Minecraft's `AnimationChannel.Interpolations`
 * holds exactly two members -- `LINEAR` and `CATMULLROM` -- so the bezier easing
 * Blockbench will happily author cannot be expressed here at all. An ease
 * applied in the graph editor has to be turned back into a smooth curve before
 * transcription, or it is silently downgraded and the motion quietly stops
 * matching the preview it was approved from.
 */
object NymphAnimation {

	/** The only curve Minecraft can express besides a straight line. */
	private val SMOOTH = AnimationChannel.Interpolations.CATMULLROM

	val WALK: AnimationDefinition =
		AnimationDefinition.Builder.withLength(1.0f)
			.looping()
			.addAnimation(
				"hips",
				AnimationChannel(
					AnimationChannel.Targets.POSITION,
					Keyframe(0f, KeyframeAnimations.posVec(0f, 0f, 0f), SMOOTH),
					Keyframe(0.25f, KeyframeAnimations.posVec(0f, 0.6f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.posVec(0f, 0f, 0f), SMOOTH),
					Keyframe(0.75f, KeyframeAnimations.posVec(0f, 0.6f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.posVec(0f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"torso",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(0f, -7f, 1.5f), SMOOTH),
					Keyframe(0.25f, KeyframeAnimations.degreeVec(0f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(0f, 7f, -1.5f), SMOOTH),
					Keyframe(0.75f, KeyframeAnimations.degreeVec(0f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(0f, -7f, 1.5f), SMOOTH),
				),
			)
			.addAnimation(
				"neck",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(0f, 3f, 0f), SMOOTH),
					Keyframe(0.25f, KeyframeAnimations.degreeVec(0f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(0f, -3f, 0f), SMOOTH),
					Keyframe(0.75f, KeyframeAnimations.degreeVec(0f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(0f, 3f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"hair",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(1f, 0f, 0f), SMOOTH),
					Keyframe(0.25f, KeyframeAnimations.degreeVec(3.5f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(1f, 0f, 0f), SMOOTH),
					Keyframe(0.75f, KeyframeAnimations.degreeVec(3.5f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(1f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"arm_right",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(-12f, 0f, 0f), SMOOTH),
					Keyframe(0.25f, KeyframeAnimations.degreeVec(0f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(14f, 0f, 0f), SMOOTH),
					Keyframe(0.75f, KeyframeAnimations.degreeVec(0f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(-12f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"forearm_right",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(-18f, 0f, 0f), SMOOTH),
					Keyframe(0.25f, KeyframeAnimations.degreeVec(-10f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(-22f, 0f, 0f), SMOOTH),
					Keyframe(0.75f, KeyframeAnimations.degreeVec(-14f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(-18f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"arm_left",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(14f, 0f, 0f), SMOOTH),
					Keyframe(0.25f, KeyframeAnimations.degreeVec(0f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(-12f, 0f, 0f), SMOOTH),
					Keyframe(0.75f, KeyframeAnimations.degreeVec(0f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(14f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"forearm_left",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(-22f, 0f, 0f), SMOOTH),
					Keyframe(0.25f, KeyframeAnimations.degreeVec(-14f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(-18f, 0f, 0f), SMOOTH),
					Keyframe(0.75f, KeyframeAnimations.degreeVec(-10f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(-22f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"leg_right",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(22f, 0f, 0f), SMOOTH),
					Keyframe(0.25f, KeyframeAnimations.degreeVec(0f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(-18f, 0f, 0f), SMOOTH),
					Keyframe(0.75f, KeyframeAnimations.degreeVec(0f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(22f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"shin_right",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(6f, 0f, 0f), SMOOTH),
					Keyframe(0.25f, KeyframeAnimations.degreeVec(48f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(8f, 0f, 0f), SMOOTH),
					Keyframe(0.75f, KeyframeAnimations.degreeVec(2f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(6f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"foot_right",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(20f, 0f, 0f), SMOOTH),
					Keyframe(0.25f, KeyframeAnimations.degreeVec(-12f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(-12f, 0f, 0f), SMOOTH),
					Keyframe(0.75f, KeyframeAnimations.degreeVec(0f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(20f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"leg_left",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(-18f, 0f, 0f), SMOOTH),
					Keyframe(0.25f, KeyframeAnimations.degreeVec(0f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(22f, 0f, 0f), SMOOTH),
					Keyframe(0.75f, KeyframeAnimations.degreeVec(0f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(-18f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"shin_left",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(8f, 0f, 0f), SMOOTH),
					Keyframe(0.25f, KeyframeAnimations.degreeVec(2f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(6f, 0f, 0f), SMOOTH),
					Keyframe(0.75f, KeyframeAnimations.degreeVec(48f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(8f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"foot_left",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(-12f, 0f, 0f), SMOOTH),
					Keyframe(0.25f, KeyframeAnimations.degreeVec(0f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(20f, 0f, 0f), SMOOTH),
					Keyframe(0.75f, KeyframeAnimations.degreeVec(-12f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(-12f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"sash",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(0f, 0f, 3f), SMOOTH),
					Keyframe(0.25f, KeyframeAnimations.degreeVec(0f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(0f, 0f, -3f), SMOOTH),
					Keyframe(0.75f, KeyframeAnimations.degreeVec(0f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(0f, 0f, 3f), SMOOTH),
				),
			)
			.build()

	/**
	 * The Nymph's run, and it is a hunt rather than a jog.
	 *
	 * **A transcription of the `run` animation in `blockbench/nymph.bbmodel`**,
	 * on the same terms as [WALK]: edit it there, re-read the keyframes,
	 * regenerate. Eleven keys on a tenth-second grid rather than the walk's five
	 * quarters, because a run has four distinct poses per stride and the walk's
	 * grid cannot hold them.
	 *
	 * ## Phase-locked to the walk, deliberately
	 *
	 * [NymphModel] crossfades the two by weight, so they have to agree about
	 * where in the stride they are or the blend cancels the very limbs it is
	 * mixing. Both therefore put the **right thigh at its rearmost and the right
	 * arm at its most forward at t=0**, and over the whole cycle the two gaits
	 * disagree in sign for at most 3% of it -- only at the zero crossings, where
	 * the amplitude is nil and it cannot show.
	 *
	 * ## What makes it read as a run
	 *
	 * Measured against the walk with the same instrument, through the leg chain:
	 *
	 * | | walk | run |
	 * | --- | --- | --- |
	 * | foot lift | 1.27 | **6.45** |
	 * | stride angle | 40 deg | **87 deg** |
	 * | deepest knee | -48 deg | **-105 deg** |
	 * | arm swing | 26 deg | **82 deg** |
	 *
	 * On top of that the torso pitches 13 degrees forward and counter-twists
	 * nearly twice as far, the elbows lock into a runner's right angle and pump
	 * rather than swing, and the hips drop *below* their rest height at each
	 * absorption instead of only rising as the walk's do.
	 *
	 * The stance is 40% of the cycle rather than the walk's 50%, which is what
	 * compresses the swing and gives the leg somewhere to go. Note what that
	 * does **not** buy: a Minecraft model's root never translates, so there is
	 * no airborne moment to measure and no point claiming one. The first version
	 * of the check that verified this animation tried to, and failed its own
	 * control by declaring the shipped walk 42% airborne.
	 *
	 * ## One artefact, understood rather than fixed
	 *
	 * The interpolation clamps its keyframe indices at the ends instead of
	 * wrapping, so the loop seam carries a velocity kink -- 328 deg/s here
	 * against the walk's 105. It is on `shin_right` at exactly the toe-off, and
	 * clamping is the *better* behaviour there: it holds the knee still through
	 * late stance and then snaps it, where a wrapped tangent would start the
	 * heel flicking up while the foot was still pushing. The same clamping
	 * leaves the two legs 4.4 degrees short of exact mirrors at the seam, which
	 * sounds like a limp and is not -- the walk is 3.1 degrees out in the same
	 * place on the same bone, over a stride less than half as long.
	 */
	val RUN: AnimationDefinition =
		AnimationDefinition.Builder.withLength(1.0f)
			.looping()
			.addAnimation(
				"hips",
				AnimationChannel(
					AnimationChannel.Targets.POSITION,
					Keyframe(0f, KeyframeAnimations.posVec(0f, 1f, 0f), SMOOTH),
					Keyframe(0.1f, KeyframeAnimations.posVec(0f, 0.2f, 0f), SMOOTH),
					Keyframe(0.2f, KeyframeAnimations.posVec(0f, -0.5f, 0f), SMOOTH),
					Keyframe(0.3f, KeyframeAnimations.posVec(0f, -0.1f, 0f), SMOOTH),
					Keyframe(0.4f, KeyframeAnimations.posVec(0f, 0.55f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.posVec(0f, 1f, 0f), SMOOTH),
					Keyframe(0.6f, KeyframeAnimations.posVec(0f, 0.2f, 0f), SMOOTH),
					Keyframe(0.7f, KeyframeAnimations.posVec(0f, -0.5f, 0f), SMOOTH),
					Keyframe(0.8f, KeyframeAnimations.posVec(0f, -0.1f, 0f), SMOOTH),
					Keyframe(0.9f, KeyframeAnimations.posVec(0f, 0.55f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.posVec(0f, 1f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"torso",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(12.5f, -12f, 3.5f), SMOOTH),
					Keyframe(0.1f, KeyframeAnimations.degreeVec(14.5f, -10f, 2.8f), SMOOTH),
					Keyframe(0.2f, KeyframeAnimations.degreeVec(16f, -4f, 1.1f), SMOOTH),
					Keyframe(0.3f, KeyframeAnimations.degreeVec(14.5f, 4f, -1.1f), SMOOTH),
					Keyframe(0.4f, KeyframeAnimations.degreeVec(12.5f, 10f, -2.8f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(12.5f, 12f, -3.5f), SMOOTH),
					Keyframe(0.6f, KeyframeAnimations.degreeVec(14.5f, 10f, -2.8f), SMOOTH),
					Keyframe(0.7f, KeyframeAnimations.degreeVec(16f, 4f, -1.1f), SMOOTH),
					Keyframe(0.8f, KeyframeAnimations.degreeVec(14.5f, -4f, 1.1f), SMOOTH),
					Keyframe(0.9f, KeyframeAnimations.degreeVec(12.5f, -10f, 2.8f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(12.5f, -12f, 3.5f), SMOOTH),
				),
			)
			.addAnimation(
				"neck",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(-9f, 5f, 0f), SMOOTH),
					Keyframe(0.1f, KeyframeAnimations.degreeVec(-10f, 4f, 0f), SMOOTH),
					Keyframe(0.2f, KeyframeAnimations.degreeVec(-11f, 1.5f, 0f), SMOOTH),
					Keyframe(0.3f, KeyframeAnimations.degreeVec(-10f, -1.5f, 0f), SMOOTH),
					Keyframe(0.4f, KeyframeAnimations.degreeVec(-9f, -4f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(-9f, -5f, 0f), SMOOTH),
					Keyframe(0.6f, KeyframeAnimations.degreeVec(-10f, -4f, 0f), SMOOTH),
					Keyframe(0.7f, KeyframeAnimations.degreeVec(-11f, -1.5f, 0f), SMOOTH),
					Keyframe(0.8f, KeyframeAnimations.degreeVec(-10f, 1.5f, 0f), SMOOTH),
					Keyframe(0.9f, KeyframeAnimations.degreeVec(-9f, 4f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(-9f, 5f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"hair",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(6f, 0f, 0f), SMOOTH),
					Keyframe(0.1f, KeyframeAnimations.degreeVec(7.7f, 0f, 0f), SMOOTH),
					Keyframe(0.2f, KeyframeAnimations.degreeVec(10.5f, 0f, 0f), SMOOTH),
					Keyframe(0.3f, KeyframeAnimations.degreeVec(10.5f, 0f, 0f), SMOOTH),
					Keyframe(0.4f, KeyframeAnimations.degreeVec(7.7f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(6f, 0f, 0f), SMOOTH),
					Keyframe(0.6f, KeyframeAnimations.degreeVec(7.7f, 0f, 0f), SMOOTH),
					Keyframe(0.7f, KeyframeAnimations.degreeVec(10.5f, 0f, 0f), SMOOTH),
					Keyframe(0.8f, KeyframeAnimations.degreeVec(10.5f, 0f, 0f), SMOOTH),
					Keyframe(0.9f, KeyframeAnimations.degreeVec(7.7f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(6f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"arm_right",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(-44f, 0f, -5f), SMOOTH),
					Keyframe(0.1f, KeyframeAnimations.degreeVec(-36f, 0f, -5f), SMOOTH),
					Keyframe(0.2f, KeyframeAnimations.degreeVec(-16f, 0f, -5f), SMOOTH),
					Keyframe(0.3f, KeyframeAnimations.degreeVec(10f, 0f, -5f), SMOOTH),
					Keyframe(0.4f, KeyframeAnimations.degreeVec(30f, 0f, -5f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(38f, 0f, -5f), SMOOTH),
					Keyframe(0.6f, KeyframeAnimations.degreeVec(30f, 0f, -5f), SMOOTH),
					Keyframe(0.7f, KeyframeAnimations.degreeVec(10f, 0f, -5f), SMOOTH),
					Keyframe(0.8f, KeyframeAnimations.degreeVec(-16f, 0f, -5f), SMOOTH),
					Keyframe(0.9f, KeyframeAnimations.degreeVec(-36f, 0f, -5f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(-44f, 0f, -5f), SMOOTH),
				),
			)
			.addAnimation(
				"forearm_right",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(-88f, 0f, 0f), SMOOTH),
					Keyframe(0.1f, KeyframeAnimations.degreeVec(-85f, 0f, 0f), SMOOTH),
					Keyframe(0.2f, KeyframeAnimations.degreeVec(-78f, 0f, 0f), SMOOTH),
					Keyframe(0.3f, KeyframeAnimations.degreeVec(-68f, 0f, 0f), SMOOTH),
					Keyframe(0.4f, KeyframeAnimations.degreeVec(-61f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(-58f, 0f, 0f), SMOOTH),
					Keyframe(0.6f, KeyframeAnimations.degreeVec(-61f, 0f, 0f), SMOOTH),
					Keyframe(0.7f, KeyframeAnimations.degreeVec(-68f, 0f, 0f), SMOOTH),
					Keyframe(0.8f, KeyframeAnimations.degreeVec(-78f, 0f, 0f), SMOOTH),
					Keyframe(0.9f, KeyframeAnimations.degreeVec(-85f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(-88f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"arm_left",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(38f, 0f, 5f), SMOOTH),
					Keyframe(0.1f, KeyframeAnimations.degreeVec(30f, 0f, 5f), SMOOTH),
					Keyframe(0.2f, KeyframeAnimations.degreeVec(10f, 0f, 5f), SMOOTH),
					Keyframe(0.3f, KeyframeAnimations.degreeVec(-16f, 0f, 5f), SMOOTH),
					Keyframe(0.4f, KeyframeAnimations.degreeVec(-36f, 0f, 5f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(-44f, 0f, 5f), SMOOTH),
					Keyframe(0.6f, KeyframeAnimations.degreeVec(-36f, 0f, 5f), SMOOTH),
					Keyframe(0.7f, KeyframeAnimations.degreeVec(-16f, 0f, 5f), SMOOTH),
					Keyframe(0.8f, KeyframeAnimations.degreeVec(10f, 0f, 5f), SMOOTH),
					Keyframe(0.9f, KeyframeAnimations.degreeVec(30f, 0f, 5f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(38f, 0f, 5f), SMOOTH),
				),
			)
			.addAnimation(
				"forearm_left",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(-58f, 0f, 0f), SMOOTH),
					Keyframe(0.1f, KeyframeAnimations.degreeVec(-61f, 0f, 0f), SMOOTH),
					Keyframe(0.2f, KeyframeAnimations.degreeVec(-68f, 0f, 0f), SMOOTH),
					Keyframe(0.3f, KeyframeAnimations.degreeVec(-78f, 0f, 0f), SMOOTH),
					Keyframe(0.4f, KeyframeAnimations.degreeVec(-85f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(-88f, 0f, 0f), SMOOTH),
					Keyframe(0.6f, KeyframeAnimations.degreeVec(-85f, 0f, 0f), SMOOTH),
					Keyframe(0.7f, KeyframeAnimations.degreeVec(-78f, 0f, 0f), SMOOTH),
					Keyframe(0.8f, KeyframeAnimations.degreeVec(-68f, 0f, 0f), SMOOTH),
					Keyframe(0.9f, KeyframeAnimations.degreeVec(-61f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(-58f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"leg_right",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(40f, 0f, 0f), SMOOTH),
					Keyframe(0.1f, KeyframeAnimations.degreeVec(16f, 0f, 0f), SMOOTH),
					Keyframe(0.2f, KeyframeAnimations.degreeVec(-8f, 0f, 0f), SMOOTH),
					Keyframe(0.3f, KeyframeAnimations.degreeVec(-32f, 0f, 0f), SMOOTH),
					Keyframe(0.4f, KeyframeAnimations.degreeVec(-46f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(-44f, 0f, 0f), SMOOTH),
					Keyframe(0.6f, KeyframeAnimations.degreeVec(-32f, 0f, 0f), SMOOTH),
					Keyframe(0.7f, KeyframeAnimations.degreeVec(-16f, 0f, 0f), SMOOTH),
					Keyframe(0.8f, KeyframeAnimations.degreeVec(4f, 0f, 0f), SMOOTH),
					Keyframe(0.9f, KeyframeAnimations.degreeVec(24f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(40f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"shin_right",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(18f, 0f, 0f), SMOOTH),
					Keyframe(0.1f, KeyframeAnimations.degreeVec(78f, 0f, 0f), SMOOTH),
					Keyframe(0.2f, KeyframeAnimations.degreeVec(104f, 0f, 0f), SMOOTH),
					Keyframe(0.3f, KeyframeAnimations.degreeVec(96f, 0f, 0f), SMOOTH),
					Keyframe(0.4f, KeyframeAnimations.degreeVec(64f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(34f, 0f, 0f), SMOOTH),
					Keyframe(0.6f, KeyframeAnimations.degreeVec(22f, 0f, 0f), SMOOTH),
					Keyframe(0.7f, KeyframeAnimations.degreeVec(44f, 0f, 0f), SMOOTH),
					Keyframe(0.8f, KeyframeAnimations.degreeVec(34f, 0f, 0f), SMOOTH),
					Keyframe(0.9f, KeyframeAnimations.degreeVec(22f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(18f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"foot_right",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(38f, 0f, 0f), SMOOTH),
					Keyframe(0.1f, KeyframeAnimations.degreeVec(22f, 0f, 0f), SMOOTH),
					Keyframe(0.2f, KeyframeAnimations.degreeVec(4f, 0f, 0f), SMOOTH),
					Keyframe(0.3f, KeyframeAnimations.degreeVec(-6f, 0f, 0f), SMOOTH),
					Keyframe(0.4f, KeyframeAnimations.degreeVec(-12f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(-14f, 0f, 0f), SMOOTH),
					Keyframe(0.6f, KeyframeAnimations.degreeVec(-6f, 0f, 0f), SMOOTH),
					Keyframe(0.7f, KeyframeAnimations.degreeVec(8f, 0f, 0f), SMOOTH),
					Keyframe(0.8f, KeyframeAnimations.degreeVec(18f, 0f, 0f), SMOOTH),
					Keyframe(0.9f, KeyframeAnimations.degreeVec(28f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(38f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"leg_left",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(-44f, 0f, 0f), SMOOTH),
					Keyframe(0.1f, KeyframeAnimations.degreeVec(-32f, 0f, 0f), SMOOTH),
					Keyframe(0.2f, KeyframeAnimations.degreeVec(-16f, 0f, 0f), SMOOTH),
					Keyframe(0.3f, KeyframeAnimations.degreeVec(4f, 0f, 0f), SMOOTH),
					Keyframe(0.4f, KeyframeAnimations.degreeVec(24f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(40f, 0f, 0f), SMOOTH),
					Keyframe(0.6f, KeyframeAnimations.degreeVec(16f, 0f, 0f), SMOOTH),
					Keyframe(0.7f, KeyframeAnimations.degreeVec(-8f, 0f, 0f), SMOOTH),
					Keyframe(0.8f, KeyframeAnimations.degreeVec(-32f, 0f, 0f), SMOOTH),
					Keyframe(0.9f, KeyframeAnimations.degreeVec(-46f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(-44f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"shin_left",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(34f, 0f, 0f), SMOOTH),
					Keyframe(0.1f, KeyframeAnimations.degreeVec(22f, 0f, 0f), SMOOTH),
					Keyframe(0.2f, KeyframeAnimations.degreeVec(44f, 0f, 0f), SMOOTH),
					Keyframe(0.3f, KeyframeAnimations.degreeVec(34f, 0f, 0f), SMOOTH),
					Keyframe(0.4f, KeyframeAnimations.degreeVec(22f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(18f, 0f, 0f), SMOOTH),
					Keyframe(0.6f, KeyframeAnimations.degreeVec(78f, 0f, 0f), SMOOTH),
					Keyframe(0.7f, KeyframeAnimations.degreeVec(104f, 0f, 0f), SMOOTH),
					Keyframe(0.8f, KeyframeAnimations.degreeVec(96f, 0f, 0f), SMOOTH),
					Keyframe(0.9f, KeyframeAnimations.degreeVec(64f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(34f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"foot_left",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(-14f, 0f, 0f), SMOOTH),
					Keyframe(0.1f, KeyframeAnimations.degreeVec(-6f, 0f, 0f), SMOOTH),
					Keyframe(0.2f, KeyframeAnimations.degreeVec(8f, 0f, 0f), SMOOTH),
					Keyframe(0.3f, KeyframeAnimations.degreeVec(18f, 0f, 0f), SMOOTH),
					Keyframe(0.4f, KeyframeAnimations.degreeVec(28f, 0f, 0f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(38f, 0f, 0f), SMOOTH),
					Keyframe(0.6f, KeyframeAnimations.degreeVec(22f, 0f, 0f), SMOOTH),
					Keyframe(0.7f, KeyframeAnimations.degreeVec(4f, 0f, 0f), SMOOTH),
					Keyframe(0.8f, KeyframeAnimations.degreeVec(-6f, 0f, 0f), SMOOTH),
					Keyframe(0.9f, KeyframeAnimations.degreeVec(-12f, 0f, 0f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(-14f, 0f, 0f), SMOOTH),
				),
			)
			.addAnimation(
				"sash",
				AnimationChannel(
					AnimationChannel.Targets.ROTATION,
					Keyframe(0f, KeyframeAnimations.degreeVec(8f, 0f, 6f), SMOOTH),
					Keyframe(0.1f, KeyframeAnimations.degreeVec(9.5f, 0f, 4.9f), SMOOTH),
					Keyframe(0.2f, KeyframeAnimations.degreeVec(11.5f, 0f, 1.9f), SMOOTH),
					Keyframe(0.3f, KeyframeAnimations.degreeVec(11.5f, 0f, -1.9f), SMOOTH),
					Keyframe(0.4f, KeyframeAnimations.degreeVec(9.5f, 0f, -4.9f), SMOOTH),
					Keyframe(0.5f, KeyframeAnimations.degreeVec(8f, 0f, -6f), SMOOTH),
					Keyframe(0.6f, KeyframeAnimations.degreeVec(9.5f, 0f, -4.9f), SMOOTH),
					Keyframe(0.7f, KeyframeAnimations.degreeVec(11.5f, 0f, -1.9f), SMOOTH),
					Keyframe(0.8f, KeyframeAnimations.degreeVec(11.5f, 0f, 1.9f), SMOOTH),
					Keyframe(0.9f, KeyframeAnimations.degreeVec(9.5f, 0f, 4.9f), SMOOTH),
					Keyframe(1f, KeyframeAnimations.degreeVec(8f, 0f, 6f), SMOOTH),
				),
			)
			.build()
}
