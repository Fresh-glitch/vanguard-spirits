package io.github.freshglitch.vanguardspirits.item

/**
 * The colour the light in a Fractured Memory is showing right now.
 *
 * The item's texture drifts through the four charms' own rune colours, and this
 * is what lets the *name* drift with it, so hovering one shows a word the same
 * colour as the fork burning in the sprite beside it.
 *
 * ## Why the phase is a plain counter
 *
 * Sprite animation is driven by `TextureManager.tick`, which walks the atlas and
 * ticks each `SpriteContents.AnimationState`. That state holds a private `frame`
 * with no accessor and is not reachable from the sprite -- the atlas keeps the
 * states in a private list of its own -- so the live frame cannot simply be read.
 *
 * A counter kept in step with it is the cheaper answer, but *only* if it is
 * driven by the same call. It is: `TextureManagerMixin` advances this from
 * `TextureManager.tick` itself. An earlier version counted client ticks instead
 * and was wrong -- sprites advance once per rendered *frame*, gated on a game
 * tick being due and the level running normally, so the two came apart below
 * twenty frames a second and every time the game was paused.
 *
 * [advance] and [reset] are called from the client; nothing else touches them.
 *
 * ## Why it lives in the common source set
 *
 * Because [FracturedMemoryItem.getName] is common, and that is the one method
 * feeding both the tooltip title and the hotbar name popup. On a dedicated
 * server the phase simply never advances and every name comes out gold, which
 * is the right answer for a death message or an advancement anyway.
 */
object MemoryHue {

	/**
	 * Client ticks since the last resource reload.
	 *
	 * Volatile because the client thread writes it and the render thread reads
	 * it. A torn read would be one frame of the wrong colour, which is not worth
	 * a lock, but the annotation costs nothing and documents the sharing.
	 */
	@Volatile
	private var phase: Int = 0

	/**
	 * Called from `TextureManagerMixin`, on the same call that advances every
	 * sprite animation. `@JvmStatic` because an object's members are instance
	 * methods to Java, the same reason `EchoOfKinshipItem` carries it.
	 */
	@JvmStatic
	fun advance() {
		phase = (phase + 1) % CYCLE
	}

	/** Called when the atlas is re-stitched, which resets the sprite to frame 0. */
	fun reset() {
		phase = 0
	}

	/** The colour the fracture is showing, as a plain RGB int. */
	fun current(): Int = colourAt(phase)

	/**
	 * The colour at a given point in the cycle.
	 *
	 * Deliberately the same shape as the curve baked into the sprite sheet: hold
	 * on a charm's colour for most of a leg, then cross to the next on a
	 * smoothstep. Interpolating straight through instead would put the text on a
	 * washed-out midpoint for a third of its life.
	 */
	fun colourAt(tick: Int): Int {
		val t = (tick % CYCLE).toFloat() / CYCLE
		val leg = (t * STOPS.size).toInt().coerceIn(0, STOPS.size - 1)
		val within = t * STOPS.size - leg

		val from = STOPS[leg]
		val to = STOPS[(leg + 1) % STOPS.size]
		return blend(from, to, crossover(within))
	}

	/** Position through a leg, flattened so each colour is held rather than swept. */
	private fun crossover(t: Float): Float = when {
		t <= HOLD_UNTIL -> 0.0f
		t >= HOLD_FROM -> 1.0f
		else -> {
			val u = (t - HOLD_UNTIL) / (HOLD_FROM - HOLD_UNTIL)
			u * u * (3.0f - 2.0f * u)
		}
	}

	private fun blend(from: Int, to: Int, u: Float): Int {
		var out = 0
		for (shift in intArrayOf(16, 8, 0)) {
			val a = (from shr shift) and 0xFF
			val b = (to shr shift) and 0xFF
			val v = (a + (b - a) * u).toInt().coerceIn(0, 0xFF)
			out = out or (v shl shift)
		}
		return out
	}

	/**
	 * The four charms' rune colours, in the order the sprite visits them.
	 *
	 * The saturated *lip* tone of each rather than its near-white core: the cores
	 * are all within a hair of white and would give four indistinguishable shades
	 * of pale, where these read as gold, blue, green and violet at a glance.
	 *
	 * **Kept in step with `tools/make_fractured_memory.py` by hand.** Four colours
	 * and three numbers duplicated across a Python generator and a Kotlin object
	 * is not worth a build step to unify, but it does mean retuning one side
	 * without the other will put the word and the picture out of agreement.
	 */
	private val STOPS = intArrayOf(
		0xECCD76, // Delver, gold
		0x9FE4F2, // Leaper, blue
		0x5FBB63, // Wanderer, green
		0xC3A3EC, // Returned, violet
	)

	private const val HOLD_UNTIL = 0.30f
	private const val HOLD_FROM = 0.72f

	/** Frames per leg times ticks per frame, times the four legs. 480 ticks, 24s. */
	private const val CYCLE = 4 * 8 * 15
}
