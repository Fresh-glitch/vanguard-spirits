package io.github.freshglitch.vanguardspirits.lore

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import net.minecraft.network.chat.Component

/**
 * The eight passages carved through a Guarded Ruin, and the single source of
 * truth for how many there are.
 *
 * Both the keys and the English text live here, in the common source set,
 * because three separate places need parts of it and they must not disagree:
 * the block and the codex need [COUNT] to size a bitmask, the screen needs the
 * keys, and the language provider needs the text. Splitting the text off into
 * the datagen provider would put the count and the strings in different files
 * and let one drift past the other -- an untranslated key is a perfectly valid
 * key, so the mismatch could not fail the build and would surface only as a raw
 * `mural.vanguard-spirits.5.body` on a wall thirty blocks underground.
 *
 * ## The arc
 *
 * Read top to bottom they answer the question the ruins pose: who dug this, and
 * why is there a graveyard sitting on the lid. Each passage is placed at the
 * depth it belongs to, so descending *is* the reading order -- the positions
 * live in `GraveyardPiece.setMurals` (I and II, in the mausoleum) and
 * `GuardedRuinsPiece.setMurals` (III to VIII, on the way down).
 *
 * The text is deliberately a first-person account rather than a chronicle. A
 * chronicle would have to explain the mod's systems from outside, which reads
 * like documentation; a survivor writing to whoever comes next can mention the
 * Remnants, the Sentinel and the Mourners as *things they are responsible for*,
 * which is the only way the fiction earns them.
 */
object MuralLore {

	/** How many passages a complete ruin carries. */
	const val COUNT: Int = 8

	/**
	 * Every passage read, as a bitmask.
	 *
	 * The codex compares against this rather than against a hardcoded 255, so
	 * adding a ninth passage cannot leave the "read them all" advancement
	 * quietly unearnable.
	 */
	const val ALL_READ: Int = (1 shl COUNT) - 1

	fun titleKey(passage: Int): String = "mural.${VanguardSpirits.MOD_ID}.$passage.title"

	fun bodyKey(passage: Int): String = "mural.${VanguardSpirits.MOD_ID}.$passage.body"

	fun title(passage: Int): Component = Component.translatable(titleKey(passage))

	fun body(passage: Int): Component = Component.translatable(bodyKey(passage))

	/** Whether [passage] is one this mod actually carved. */
	fun exists(passage: Int): Boolean = passage in 0 until COUNT

	/**
	 * Carved numbering, shown beside the title.
	 *
	 * Roman on purpose: the passages are an ordered set the player is meant to
	 * notice gaps in, and "IV" reads as a thing chiselled by someone rather than
	 * as a page number.
	 */
	fun numeral(passage: Int): String = NUMERALS[passage]

	/**
	 * How many there are, in the same carved numbering.
	 *
	 * The last passage's numeral *is* the total, since the list is 1-based
	 * labels over 0-based indices -- but writing `numeral(COUNT - 1)` at the
	 * call site to mean "eight" reads like an off-by-one waiting to be
	 * "corrected".
	 */
	val TOTAL_NUMERAL: String get() = NUMERALS[COUNT - 1]

	private val NUMERALS = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII")

	/** English titles, in passage order. Consumed by the language provider. */
	val TITLES: List<String> = listOf(
		"The Watch Set Down",
		"Why the Graves Lie Above",
		"What the Stone Keeps",
		"The First Binding",
		"What We Took",
		"The Sealing",
		"The One Who Stayed",
		"The Last Hand",
	)

	/**
	 * English bodies, in passage order.
	 *
	 * Kept to roughly sixty words each. The screen paginates by measuring, so
	 * length here is a reading decision rather than a layout constraint -- but a
	 * passage that runs to three pages stops being something a player finishes
	 * standing in a room full of Remnants.
	 */
	val BODIES: List<String> = listOf(
		// I -- the dedication, in the mausoleum. Establishes that the graveyard
		// is the point of the place and not an ornament on top of it.
		"We were not soldiers. We were the ones who stayed behind when the rest " +
			"went on, to keep what could not be carried.\n\n" +
			"This ground is ours. Walk on it gently. Every name beneath your feet " +
			"chose to be beneath your feet.",

		// II -- the stair. Answers the structural question directly: why dig
		// down through your own cemetery.
		"The stair goes down through our dead on purpose.\n\n" +
			"Anyone who wants what we kept must first cross the whole field of " +
			"those who kept it. We thought that would be warning enough.\n\n" +
			"It was not, or you would not be standing here.",

		// III -- the crypt. The discovery the whole mod rests on.
		"A memory does not end. It breaks.\n\n" +
			"The pieces fall out of a person as they go and settle into whatever " +
			"lies nearest, and deepslate holds them better than anything else we " +
			"tried. Down here the walls are thick with other people's lives.\n\n" +
			"We learned to pick them up. That was the beginning of it.",

		// IV -- the crypt. Puts names to three of the four charms, and opens the
		// thread that passage VII closes.
		"Sera could clear a ravine from standing. Toma walked further in a day " +
			"than a horse. Ilen never once needed a torch.\n\n" +
			"We bound what was left of each of them into stone, and the stone gave " +
			"it back. For a while it felt like keeping them.\n\n" +
			"There was a fourth. I will come to her.",

		// V -- the deeps. The turn. Explains the Remnants as something the
		// builders made rather than something that happened to them.
		"Take one fragment and the rest of a person settles. Take enough and " +
			"there is nothing left to settle.\n\n" +
			"They still rise. They still know the shape of a stair and the shape " +
			"of a stranger. They do not know us.\n\n" +
			"We made every one of them. Remember that when they come up out of the " +
			"ground at you.",

		// VI -- the deeps. Why the ruin is sealed and the graveyard is the lid.
		"We stopped too late, and stopping was the whole of what we could do.\n\n" +
			"The work went down and the door went over it, and we laid our own dead " +
			"across the lid -- not as a lock, but as a ledger.\n\n" +
			"Read the names on your way down. That is the price, written out.",

		// VII -- the sanctum. The Sentinel is a person, and the reason the
		// Reliquary will not open for anyone who has not answered her.
		"Vela was the fourth. She would not let us take a piece of her.\n\n" +
			"She gave the whole of it instead, standing, into the stone at the " +
			"middle of this room, and asked one thing: that it never be allowed to " +
			"gather.\n\n" +
			"So it guards and does not take. It will not tire and it will not want. " +
			"It is the best of us, and it will not know you.",

		// VIII -- the vault. Explains the Mourners, and gives the player
		// permission to loot, which is the only graceful way to end it.
		"I am the last, and I am writing this badly, because my hands are old.\n\n" +
			"The birds came a week ago and have not gone. They know before we do. " +
			"They will circle until the last of us is down, and then they will keep " +
			"circling, because that is what they are for.\n\n" +
			"Take what you need from the reliquary. Leave the rest for whoever comes " +
			"after. There is always someone after.",
	)

	init {
		// The three lists are the one thing here that can silently disagree, and
		// a short TITLES would throw deep inside datagen with nothing pointing
		// back at this file. Fail at class-load instead, where the message names
		// the cause.
		require(TITLES.size == COUNT) { "MuralLore has $COUNT passages but ${TITLES.size} titles" }
		require(BODIES.size == COUNT) { "MuralLore has $COUNT passages but ${BODIES.size} bodies" }
		require(NUMERALS.size == COUNT) { "MuralLore has $COUNT passages but ${NUMERALS.size} numerals" }
	}
}
