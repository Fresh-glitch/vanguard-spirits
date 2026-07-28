package io.github.freshglitch.vanguardspirits.block.entity

import net.minecraft.util.Mth

/**
 * Drives the Reliquary's lid angle.
 *
 * Vanilla's `ChestLidController` moves at a fixed 0.1 per tick, which suits a
 * vanilla chest's sound and nothing else, so the Reliquary keeps its own.
 *
 * Both directions take five ticks. An earlier version stretched the opening to
 * 41 ticks to span `block/vault/open_shutter`, whose file runs 2.041 s -- but
 * most of that length is decay tail rather than moving shutter, so the lid
 * crawled long after anything sounded like it was still travelling. Matching
 * the audible movement beats matching the file length.
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
		/**
		 * Ticks the lid takes to travel. Kept as two constants rather than one
		 * so the directions can be tuned apart again if a future sound wants it.
		 */
		private const val OPEN_TICKS = 5.0f
		private const val CLOSE_TICKS = 5.0f

		private const val OPEN_STEP = 1.0f / OPEN_TICKS
		private const val CLOSE_STEP = 1.0f / CLOSE_TICKS
	}
}
