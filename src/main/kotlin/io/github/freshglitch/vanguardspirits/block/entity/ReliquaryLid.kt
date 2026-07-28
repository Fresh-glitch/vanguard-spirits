package io.github.freshglitch.vanguardspirits.block.entity

import net.minecraft.util.Mth

/**
 * Drives the Reliquary's lid angle, opening and closing at different speeds.
 *
 * Vanilla's `ChestLidController` moves at a fixed 0.1 per tick -- ten ticks in
 * either direction -- which suits the sound a vanilla chest makes and nothing
 * else. The Reliquary's two sounds are wildly different lengths, so no single
 * rate can follow both:
 *
 *  - `block/vault/open_shutter` runs 2.041 s, about 41 ticks
 *  - `block/trial_spawner/close_shutter` runs 0.250 s, about 5 ticks
 *
 * Durations measured from the .ogg files rather than guessed. The result reads
 * the way the audio already sounded: a heavy shutter grinding open, then
 * dropping shut.
 */
class ReliquaryLid {

	private var wantsOpen = false
	private var openness = 0.0f
	private var previous = 0.0f

	fun shouldBeOpen(open: Boolean) {
		wantsOpen = open
	}

	fun tick() {
		previous = openness
		openness = if (wantsOpen) {
			(openness + OPEN_STEP).coerceAtMost(1.0f)
		} else {
			(openness - CLOSE_STEP).coerceAtLeast(0.0f)
		}
	}

	/** Interpolated for the frame, so the swing is smooth between ticks. */
	fun openness(partialTick: Float): Float = Mth.lerp(partialTick, previous, openness)

	companion object {
		/** Ticks the lid takes to travel, matched to each sound's length. */
		private const val OPEN_TICKS = 41.0f
		private const val CLOSE_TICKS = 5.0f

		private const val OPEN_STEP = 1.0f / OPEN_TICKS
		private const val CLOSE_STEP = 1.0f / CLOSE_TICKS
	}
}
