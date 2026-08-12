package io.github.freshglitch.vanguardspirits.entity

import io.github.freshglitch.vanguardspirits.item.CharmItem
import io.github.freshglitch.vanguardspirits.registry.ModItems
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.player.Player

/**
 * What the Nymph has to say, and which of it she says now.
 *
 * The brief was an *intelligent* mob, and the cheap reading of that is a long
 * list of lines picked at random. That is not intelligence, it is a fortune
 * cookie -- the tell is that the third line contradicts the first and nothing
 * she says has anything to do with the person standing in front of her.
 *
 * So this is built out of two halves that interleave:
 *
 * - **What she can see.** [observe] returns only the remarks that are *true at
 *   this moment* -- that it is dark, that it is raining, that the player is
 *   bleeding, that they are holding an axe in her wood. Every one of them is a
 *   real read of the world, so she never remarks on rain in the sun.
 * - **What she knows.** [LORE] is a thread with an order, and it is the mod's
 *   own story told by the only witness left alive to tell it. It advances one
 *   step per conversation and does not repeat.
 *
 * Odd turns take an observation, even turns take the thread. That keeps her
 * grounded in the present without the story ever stalling, and it needs no state
 * beyond the cursor the Nymph is already keeping -- which matters, because the
 * alternative is remembering which lines were used per player per Nymph and
 * saving it.
 *
 * The lore is deliberately *load-bearing on nothing*. A player who never finds
 * her still learns all of it from the murals. What she adds is that somebody is
 * saying it out loud, and that she was there.
 */
object NymphSpeech {

	private const val ROOT = "message.vanguard-spirits.nymph."

	const val GREETING_KEY: String = ROOT + "greeting"
	const val WRATH_KEY: String = ROOT + "wrath"
	const val REFUSAL_KEY: String = ROOT + "refusal"
	const val SETTLED_KEY: String = ROOT + "settled"
	const val AMENDS_KEY: String = ROOT + "amends"
	const val GIFT_KEY: String = ROOT + "gift"

	/** Things she remarks on only when they are true. */
	val NIGHT: String = ROOT + "night"
	val RAIN: String = ROOT + "rain"
	val HURT: String = ROOT + "hurt"
	val MEMORY: String = ROOT + "memory"
	val CHARM: String = ROOT + "charm"
	val AXE: String = ROOT + "axe"
	val SORE: String = ROOT + "sore"

	/**
	 * The thread, in order.
	 *
	 * Told from the outside, because she was outside it. The people who built the
	 * ruin are "they" to her throughout, and she never once says what they should
	 * have done -- she is not a moral, she is a neighbour who watched.
	 */
	val LORE: List<String> = (1..7).map { "${ROOT}lore$it" }

	/** For when the thread has run out and she is still being asked. */
	val IDLE: List<String> = (1..4).map { "${ROOT}idle$it" }

	/** Said once per crossing into [Nymph.Temper.WARY], so it varies. */
	val WARNINGS: List<String> = (1..3).map { "${ROOT}warn$it" }

	/**
	 * How this particular Nymph tells somebody to stop.
	 *
	 * Keyed on her entity id, so it is *hers* and does not change -- one Nymph
	 * has one way of saying it. Keyed on `tickCount` instead, as the first draft
	 * did, the line changed once a second, and a player pressing use against a
	 * wary Nymph got all three warnings in three seconds. Variety across a
	 * lifetime is worth having; variety within one exchange reads as babble.
	 */
	fun warning(nymph: Nymph): String = WARNINGS[Math.floorMod(nymph.id, WARNINGS.size)]

	/** A chosen line, and whether taking it moved the thread along. */
	class Spoken(val key: String, val advancedLore: Boolean)

	/**
	 * The next thing she says.
	 *
	 * `cursor` is how many turns this listener has taken and `lorePos` is how far
	 * through [LORE] they have got. Two counters rather than one, and the second
	 * was bought with a playtest.
	 *
	 * The first version derived everything from `cursor` alone: odd turns took an
	 * observation, even turns took the thread, and an odd turn with nothing to
	 * remark on fell through to [IDLE]. It reads fine and it is wrong, because in
	 * plain daylight with an empty hand *nothing* is observable -- no rain, no
	 * dark, no axe, full health -- so half of every first conversation was
	 * filler. Watched in game it was worse than that sounds: turn seven produced
	 * "I have nothing else for you today" while three lore lines were still
	 * unsaid, and turn nine repeated turn one.
	 *
	 * So the thread now advances on its own count. Idle lines cannot appear until
	 * [LORE] is genuinely exhausted, which is what they were written for, and the
	 * line that claims she is finished is only ever said by a Nymph who is.
	 *
	 * The parity rule survives, and it is worth saying why: without it, a player
	 * who happens to be standing in the rain would hear seven observations and no
	 * story. Alternating means the thread always moves at least every other turn.
	 */
	fun pick(nymph: Nymph, player: Player, cursor: Int, lorePos: Int): Spoken {
		if (cursor == 0) return Spoken(GREETING_KEY, false)

		if (cursor % 2 == 1) {
			val seen = observe(nymph, player)
			// Cycles rather than repeating the first applicable remark forever,
			// so a player who keeps talking under a night sky hears the other
			// things she has noticed too.
			if (seen.isNotEmpty()) return Spoken(seen[(cursor / 2) % seen.size], false)
		}

		if (lorePos < LORE.size) return Spoken(LORE[lorePos], true)

		// Indexed on the turn itself, not on half of it. Once the thread is done
		// this branch is reached every turn rather than every other one, so the
		// halved index that is right for the observations above would hand out
		// each idle line twice in a row.
		return Spoken(IDLE[cursor % IDLE.size], false)
	}

	/**
	 * Everything about right now that she would actually mention.
	 *
	 * Ordered from the most particular to the most general, so when several are
	 * true the specific observation comes first -- being handed a piece of the
	 * dead is more worth a word than the weather.
	 *
	 * Night is read off `getSkyDarken` rather than off the time of day. There is
	 * no `getDayTime` on `Level` in 26.2, and the darkening value is better for
	 * this anyway: it is what the sky actually looks like, so it is already right
	 * under a thunderhead without a second check.
	 */
	private fun observe(nymph: Nymph, player: Player): List<String> {
		val out = ArrayList<String>(4)
		val level = nymph.level()

		val main = player.mainHandItem
		val off = player.offhandItem

		if (main.`is`(ModItems.FRACTURED_MEMORY) || off.`is`(ModItems.FRACTURED_MEMORY)) out += MEMORY
		if (main.item is CharmItem || off.item is CharmItem) out += CHARM
		if (main.`is`(ItemTags.AXES)) out += AXE
		if (player.health <= player.maxHealth * HURT_AT) out += HURT
		if (nymph.sore) out += SORE
		if (level.isRaining) out += RAIN
		if (level.skyDarken >= NIGHT_AT) out += NIGHT

		return out
	}

	/** Below two fifths of their hearts, she notices. */
	private const val HURT_AT = 0.4f

	/** `getSkyDarken` runs 0 in open daylight to 11 at midnight. */
	private const val NIGHT_AT = 5
}
