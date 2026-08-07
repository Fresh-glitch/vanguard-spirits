package io.github.freshglitch.vanguardspirits.client.datagen

import net.minecraft.network.chat.Component

/**
 * Every advancement's id and its English text, in one place.
 *
 * Two providers need these: [ModAdvancementProvider] builds the tree and refers
 * to the translation keys, and [ModEnglishProvider] supplies the strings behind
 * them. Split across both files, a renamed advancement would keep generating and
 * simply show `advancements.vanguard-spirits.foo.title` in game -- a failure that
 * datagen cannot catch, because an untranslated key is a perfectly valid key.
 * Deriving both sides from one list means there is nothing to keep in step.
 *
 * ## Where the flavour goes
 *
 * In the **title**, and nowhere else. Measured off vanilla's own 127
 * descriptions rather than guessed at: median seven words, thirty-four
 * characters, and not one of them adds a second sentence. "Enter a Bastion
 * Remnant." "Kill any hostile monster." "Upgrade your Pickaxe." The wit lives
 * entirely in names like *Isn't It Iron Pick* and *Those Were the Days*, while
 * the description below stays a plain imperative saying what to do.
 *
 * The first draft here had it both ways -- every description carried an extra
 * editorial clause ("...The birds were the sign.") and ran half again as long as
 * vanilla's. Titles are still ours to be evocative with; descriptions are
 * instructions.
 */
object ModAdvancements {

	data class Entry(val path: String, val title: String, val description: String)

	/**
	 * The tab's own name, so it reads as the mod rather than as one achievement
	 * in it -- vanilla titles `story.root` "Minecraft" for the same reason.
	 *
	 * Roots are also the one place a description is *not* an instruction. Every
	 * vanilla root carries a tagline instead -- "Bring summer clothes", "Or the
	 * beginning?", "The heart and story of the game" -- so the line that used to
	 * be this advancement's title lands here rather than being lost.
	 */
	val ROOT = Entry(
		"root",
		"Vanguard Spirits",
		"Something circles above",
	)

	val FEATHER = Entry(
		"shed_not_taken",
		"Shed, Not Taken",
		"Startle a Mourner into shedding a feather",
	)

	val REMNANT = Entry(
		"they_are_still_here",
		"They Are Still Here",
		"Open a grave and kill what comes out",
	)

	val SENTINEL = Entry(
		"stone_remembers",
		"Stone Remembers",
		"Fell the Stone Sentinel",
	)

	val MEMORY = Entry(
		"the_ward_lifts",
		"The Ward Lifts",
		"Take a Fractured Memory from a Gilded Reliquary",
	)

	val CHARM = Entry(
		"bound_to_a_spirit",
		"Bound to a Spirit",
		"Craft a charm from Fractured Memories",
	)

	val ALL_CHARMS = Entry(
		"all_four_answer",
		"All Four Answer",
		"Hold all four charms at once",
	)

	val KINSHIP = Entry(
		"two_who_never_met",
		"Two Who Never Met",
		"Obtain an Echo of Kinship",
	)

	val ATTUNED = Entry(
		"as_deep_as_it_goes",
		"As Deep As It Goes",
		"Raise your attunement to four",
	)

	val MURAL = Entry(
		"in_their_own_hand",
		"In Their Own Hand",
		"Read a mural in a Guarded Ruin",
	)

	val ALL_MURALS = Entry(
		"the_whole_account",
		"The Whole Account",
		"Read all eight murals",
	)

	val ALL: List<Entry> = listOf(
		ROOT, FEATHER, REMNANT, SENTINEL, MEMORY, CHARM, ALL_CHARMS, KINSHIP, ATTUNED,
		MURAL, ALL_MURALS,
	)

	fun titleKey(entry: Entry): String = "advancements.vanguard-spirits.${entry.path}.title"

	fun descriptionKey(entry: Entry): String = "advancements.vanguard-spirits.${entry.path}.description"

	fun title(entry: Entry): Component = Component.translatable(titleKey(entry))

	fun description(entry: Entry): Component = Component.translatable(descriptionKey(entry))
}
