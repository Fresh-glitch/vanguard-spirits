package io.github.freshglitch.vanguardspirits.worldgen

/**
 * Positional hashing, for geometry that has to agree with itself across a chunk
 * border.
 *
 * `StructurePiece.postProcess` runs once per chunk the piece touches and hands
 * each call its own `RandomSource`, so anything shaped by a random draw comes
 * out differently in each chunk. A block of masonry wider than one column then
 * generates in two halves that disagree -- a 2x2 pillar straddling a boundary
 * ends up stepped, and no log says anything about it.
 *
 * The answer is to make such decisions a pure function of position instead.
 * These live at package scope because both pieces need them and there must only
 * be one definition: two copies that drifted apart would be the same bug again,
 * one file further out.
 *
 * The arguments are deliberately unnamed as coordinates. Callers feed whatever
 * two numbers identify the thing being decided -- [GraveyardPiece] passes plot
 * coordinates, [GuardedRuinsPiece] passes world coordinates so that pillars
 * still vary from one ruin to the next, and both sometimes fold a third axis
 * into one of them.
 */
internal fun hash(a: Int, b: Int, salt: Int): Int {
	var h = (a * 73_856_093) xor (b * 19_349_663) xor (salt * 83_492_791)
	h = h xor (h ushr 13)
	h *= 1_274_126_177
	return h xor (h ushr 16)
}

/** True for a [p] share of positions, chosen by [hash]. */
internal fun chance(a: Int, b: Int, salt: Int, p: Float): Boolean =
	window(a, b, salt) < (p * 0xFFFF).toInt()

/** A value in `0 until bound`, chosen by [hash]. The positional `nextInt`. */
internal fun range(a: Int, b: Int, salt: Int, bound: Int): Int =
	window(a, b, salt) % bound

/**
 * Sixteen bits out of the middle of the hash.
 *
 * The same window for both consumers, so a salt that reads as unbiased through
 * [chance] reads the same way through [range].
 */
private fun window(a: Int, b: Int, salt: Int): Int =
	hash(a, b, salt) ushr 8 and 0xFFFF
