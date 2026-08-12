package io.github.freshglitch.vanguardspirits.client.datagen

import io.github.freshglitch.vanguardspirits.block.BindingAltarBlock
import io.github.freshglitch.vanguardspirits.block.EpitaphBlock
import io.github.freshglitch.vanguardspirits.block.entity.GoldenChestBlockEntity
import io.github.freshglitch.vanguardspirits.client.CharmTooltip
import io.github.freshglitch.vanguardspirits.client.screen.BindingAltarScreen
import io.github.freshglitch.vanguardspirits.entity.NymphSpeech
import io.github.freshglitch.vanguardspirits.item.CharmItem
import io.github.freshglitch.vanguardspirits.item.EchoOfKinshipItem
import io.github.freshglitch.vanguardspirits.item.EpitaphPrompt
import io.github.freshglitch.vanguardspirits.lore.MuralLore
import io.github.freshglitch.vanguardspirits.registry.ModBlocks
import io.github.freshglitch.vanguardspirits.registry.ModEntities
import io.github.freshglitch.vanguardspirits.registry.ModItemGroups
import io.github.freshglitch.vanguardspirits.registry.ModItems
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider
import net.minecraft.core.HolderLookup
import java.util.concurrent.CompletableFuture

class ModEnglishProvider(
	output: FabricPackOutput,
	registryLookup: CompletableFuture<HolderLookup.Provider>,
) : FabricLanguageProvider(output, registryLookup) {

	override fun generateTranslations(
		lookup: HolderLookup.Provider,
		builder: TranslationBuilder,
	) {
		builder.add(ModItemGroups.TITLE_KEY, "Vanguard Spirits")

		builder.add(ModItems.FRACTURED_MEMORY, "Fractured Memory")
		builder.add(ModItems.MOURNER_FEATHER, "Mourner's Feather")
		builder.add(ModItems.CHARM_OF_THE_LEAPER, "Charm of the Leaper")
		builder.add(ModItems.CHARM_OF_THE_WANDERER, "Charm of the Wanderer")
		builder.add(ModItems.CHARM_OF_THE_DELVER, "Charm of the Delver")
		builder.add(ModItems.CHARM_OF_THE_RETURNED, "Charm of the Returned")
		builder.add(ModItems.ECHO_OF_KINSHIP, "Echo of Kinship")

		builder.add(ModItems.STONE_SENTINEL_SPAWN_EGG, "Stone Sentinel Spawn Egg")
		builder.add(ModItems.REMNANT_SPAWN_EGG, "Remnant Spawn Egg")
		builder.add(ModItems.MOURNER_SPAWN_EGG, "Mourner Spawn Egg")
		builder.add(ModItems.NYMPH_SPAWN_EGG, "Nymph Spawn Egg")

		builder.add(
			ModItems.loreKey("charm_of_the_leaper"),
			"They cleared the gap. Nobody saw them land.",
		)
		builder.add(
			ModItems.loreKey("charm_of_the_wanderer"),
			"Some roads are still being walked.",
		)
		builder.add(
			ModItems.loreKey("charm_of_the_delver"),
			"They dug deep, and something remembered them.",
		)
		builder.add(
			ModItems.loreKey("charm_of_the_returned"),
			"Everything sent at them came back.",
		)
		builder.add(
			ModItems.loreKey("echo_of_kinship"),
			"Two spirits who never met, and remember each other anyway.",
		)
		builder.add(
			ModItems.loreKey("mourner_feather"),
			"It let this go rather than let you closer.",
		)
		// The eighth passage's author never got a stone of their own, because
		// they were the one who dug all the others.
		builder.add(
			ModItems.loreKey("unfinished_epitaph"),
			"Whoever cut this had nobody left to finish it.",
		)
		builder.add(EpitaphPrompt.TITLE_KEY, "Set the Epitaph down?")
		// Both say the same last thing, because that is the part being confirmed.
		// The difference above it is whether anything is being ended.
		builder.add(
			EpitaphPrompt.IN_RUIN_KEY,
			"The graves here will go quiet for good — nothing will rise again, " +
				"and there will be no more memories to take. You cannot pick the stone back up.",
		)
		builder.add(
			EpitaphPrompt.SETTLED_KEY,
			"This ground is already quiet — a stone stands here and the graves are done rising. " +
				"Another will change nothing. You cannot pick it back up.",
		)
		builder.add(
			EpitaphPrompt.ELSEWHERE_KEY,
			"There is no graveyard here, so nothing will settle and nothing will change. " +
				"It will stand as a marker only. You cannot pick the stone back up.",
		)

		builder.add(ModBlocks.GOLDEN_CHEST, "Gilded Reliquary")
		builder.add(GoldenChestBlockEntity.NAME_KEY, "Gilded Reliquary")
		builder.add(ModBlocks.GRAVE, "Grave")
		builder.add(ModBlocks.MURAL, "Deepslate Mural")
		builder.add(ModBlocks.BINDING_ALTAR, "Binding Altar")
		builder.add(ModBlocks.EPITAPH, "Unfinished Epitaph")
		builder.add(BindingAltarBlock.TITLE_KEY, "Binding Altar")
		builder.add(BindingAltarScreen.PRICE_KEY, "%s more Fractured Memories")
		builder.add(BindingAltarScreen.READY_KEY, "Bind to %s")
		builder.add(
			GoldenChestBlockEntity.HOLLOWED_KEY,
			"Something settles in the ground above you.",
		)
		builder.add("subtitles.vanguard-spirits.grave.disturb", "A grave comes open")
		builder.add("subtitles.vanguard-spirits.mural.read", "The carving answers")
		builder.add("subtitles.vanguard-spirits.mural.turn", "Stone ticks")
		builder.add("subtitles.vanguard-spirits.golden_chest.open", "Reliquary creaks open")
		builder.add("subtitles.vanguard-spirits.golden_chest.close", "Reliquary seals")
		builder.add("subtitles.vanguard-spirits.binding_altar.open", "The altar stirs")
		builder.add("subtitles.vanguard-spirits.binding_altar.bind", "A binding takes hold")
		builder.add("subtitles.vanguard-spirits.charm.wake", "Charm answers")
		builder.add("subtitles.vanguard-spirits.charm.hush", "Charm falls quiet")
		builder.add("subtitles.vanguard-spirits.charm.deflect", "Ward turns a shot")
		builder.add("subtitles.vanguard-spirits.echo_of_kinship.release", "An echo comes loose")
		builder.add("subtitles.vanguard-spirits.echo_of_kinship.claim", "An echo is claimed")

		builder.add(CharmItem.WOKEN_KEY, "The charm answers you.")
		builder.add(CharmItem.HUSHED_KEY, "The charm falls quiet.")
		builder.add(CharmTooltip.HUSHED_KEY, "Hushed — sneak and use to wake")
		builder.add("subtitles.vanguard-spirits.golden_chest.reject", "Runes gather")
		builder.add("subtitles.vanguard-spirits.golden_chest.ward", "The ward answers")

		builder.add(GoldenChestBlockEntity.REJECT_KEY, "The Runes reject your claim.")

		builder.add("subtitles.vanguard-spirits.mourner.call", "Mourner calls")
		builder.add("subtitles.vanguard-spirits.mourner.hurt", "Mourner cries out")
		builder.add("subtitles.vanguard-spirits.remnant.rasp", "Something breathes")
		builder.add("subtitles.vanguard-spirits.remnant.notice", "Remnant cries out")
		builder.add("subtitles.vanguard-spirits.remnant.hurt", "Remnant breaks")
		builder.add("subtitles.vanguard-spirits.remnant.death", "Remnant comes apart")
		builder.add("subtitles.vanguard-spirits.remnant.step", "Remnant scurries")

		builder.add("subtitles.vanguard-spirits.stone_sentinel.stir", "Stone grinds")
		builder.add("subtitles.vanguard-spirits.stone_sentinel.rumble", "The ground shudders")
		builder.add("subtitles.vanguard-spirits.stone_sentinel.roar", "Stone Sentinel wakes")
		builder.add("subtitles.vanguard-spirits.stone_sentinel.step", "Stone Sentinel steps")
		builder.add("subtitles.vanguard-spirits.stone_sentinel.slam", "Stone Sentinel slams")
		builder.add("subtitles.vanguard-spirits.stone_sentinel.sweep", "Stone Sentinel sweeps")
		builder.add("subtitles.vanguard-spirits.stone_sentinel.reach", "Stone Sentinel reaches out")
		builder.add("subtitles.vanguard-spirits.stone_sentinel.reckoning", "Stone Sentinel hauls you down")
		builder.add("subtitles.vanguard-spirits.stone_sentinel.brace", "Stone Sentinel draws back")
		builder.add("subtitles.vanguard-spirits.stone_sentinel.sunder", "Stone Sentinel breaks through")
		builder.add("subtitles.vanguard-spirits.stone_sentinel.wind", "Stone Sentinel winds up")
		builder.add("subtitles.vanguard-spirits.stone_sentinel.shell", "Stone Sentinel shuts itself away")
		builder.add("subtitles.vanguard-spirits.stone_sentinel.mend", "Stone knits together")
		builder.add("subtitles.vanguard-spirits.stone_sentinel.hurt", "Stone Sentinel cracks")
		builder.add("subtitles.vanguard-spirits.stone_sentinel.death", "Stone Sentinel crumbles")

		builder.add("subtitles.vanguard-spirits.nymph.ambient", "Leaves stir")
		builder.add("subtitles.vanguard-spirits.nymph.speak", "The Nymph speaks")
		builder.add("subtitles.vanguard-spirits.nymph.warn", "The Nymph warns you")
		builder.add("subtitles.vanguard-spirits.nymph.wrath", "The grove turns")
		builder.add("subtitles.vanguard-spirits.nymph.gift", "The Nymph relents")
		builder.add("subtitles.vanguard-spirits.nymph.hurt", "The Nymph recoils")
		builder.add("subtitles.vanguard-spirits.nymph.death", "The Nymph withers")

		builder.add(ModEntities.STONE_SENTINEL, "Stone Sentinel")
		builder.add(ModEntities.REMNANT, "Remnant")
		builder.add(ModEntities.MOURNER, "Mourner")
		builder.add(ModEntities.NYMPH, "Nymph")

		nymphSpeech(builder)

		builder.add("tooltip.vanguard-spirits.charm.attuned", "Attuned")
		builder.add("tooltip.vanguard-spirits.charm.dormant", "Dormant — attunement %s in use")
		builder.add("tooltip.vanguard-spirits.charm.generic", "Attunes while carried")
		builder.add(CharmTooltip.COST_KEY, "Costs %s attunement")
		builder.add(CharmTooltip.DEPTH_KEY, "Bound %s of %s")

		builder.add("effect.vanguard-spirits.deflection", "Deflection")

		builder.add(EchoOfKinshipItem.RAISED_KEY, "Attunement deepens — %s of %s")
		builder.add(EchoOfKinshipItem.SPENT_KEY, "The echo lets go — %s experience")
		builder.add(EpitaphBlock.READS_KEY, "It reads: %s")
		builder.add(EpitaphBlock.SETTLED_KEY, "The ground goes quiet.")

		// The mural screen's footer. Two forms so a single-page passage does not
		// carry a pointless "1/1".
		builder.add("gui.vanguard-spirits.mural.marker", "%s of %s")
		builder.add("gui.vanguard-spirits.mural.marker.paged", "%s of %s  ·  %s/%s")

		// Every passage, from the same list the block and the screen read. The
		// count is asserted in MuralLore, so a missing body cannot slip through
		// as a raw key on a wall thirty blocks underground.
		for (passage in 0 until MuralLore.COUNT) {
			builder.add(MuralLore.titleKey(passage), MuralLore.TITLES[passage])
			builder.add(MuralLore.bodyKey(passage), MuralLore.BODIES[passage])
		}

		// Driven off the same list the tree is built from, so an advancement can
		// never generate without its text. An untranslated key is a valid key, so
		// nothing downstream would have caught the mismatch.
		ModAdvancements.ALL.forEach { entry ->
			builder.add(ModAdvancements.titleKey(entry), entry.title)
			builder.add(ModAdvancements.descriptionKey(entry), entry.description)
		}
	}

	/**
	 * Everything the Nymph says.
	 *
	 * Her voice is the reason she is in the mod, so it is worth naming what it
	 * is: old, dry, unhurried, and entirely without sentiment. She is not a
	 * guardian spirit delivering a lesson about nature -- she is a very long
	 * lived neighbour who has watched one civilisation fail already and is
	 * unsurprised by yours. She never moralises, she never says "you must", and
	 * when she threatens you she does it in the flattest available words.
	 *
	 * The lore thread is the mod's own story told from outside it. Everything in
	 * it is corroborated by a mural or a block the player can go and find -- the
	 * eight passages, the Sentinel still following its order, the last keeper's
	 * unfinished stone, the Mourners circling. She adds no facts. What she adds
	 * is that somebody is saying them aloud, and that she was there.
	 *
	 * Drawn from the same lists [NymphSpeech] selects from, so a line cannot be
	 * added on one side and missed on the other -- an untranslated key is a
	 * perfectly valid key, and would surface only as raw
	 * `message.vanguard-spirits.nymph.lore4` on somebody's action bar.
	 */
	private fun nymphSpeech(builder: TranslationBuilder) {
		builder.add(NymphSpeech.GREETING_KEY, "You walk quietly. Not many do.")

		builder.add(NymphSpeech.NIGHT, "The dark is when this place talks. Try listening instead of asking.")
		builder.add(NymphSpeech.RAIN, "Rain. Good. The roots have been complaining for a week.")
		builder.add(NymphSpeech.HURT, "You are bleeding into my moss. Sit down before you fall down.")
		builder.add(NymphSpeech.MEMORY, "You are carrying a piece of somebody. I knew the hands that made it.")
		builder.add(NymphSpeech.CHARM, "You have tied a dead thing to you. It is listening. They always are.")
		builder.add(NymphSpeech.AXE, "You are holding an axe. I would rather you held it somewhere else.")
		builder.add(NymphSpeech.SORE, "You have taken from the wood today. Only a little. I am still counting.")

		val lore = listOf(
			"There were people here. Not here — under there, where the stone goes down.",
			"They dug because they were frightened. Frightened things always dig.",
			"They set something at the bottom to keep watch. Then they died, and nobody told it to stop.",
			"The last of them buried everyone else and began a stone for themselves. " +
				"They did not finish it. There was no one left to.",
			"The birds still turn over the place. I do not think they remember why. I do.",
			"You will go down and take what is left of them. Everyone does. I am not going to stop you.",
			"I was here before they came and I will be here after you. " +
				"That is not a threat. It is only the arithmetic.",
		)
		require(lore.size == NymphSpeech.LORE.size) {
			"the Nymph has ${NymphSpeech.LORE.size} lore keys and ${lore.size} lines written for them"
		}
		NymphSpeech.LORE.forEachIndexed { i, key -> builder.add(key, lore[i]) }

		val idle = listOf(
			"The flowers open for anybody. That is not the same as being welcome.",
			"Something is growing where you are standing. Mind your feet.",
			"Ask the bees. They keep better records than I do.",
			"I have nothing else for you today. Come back when the light has moved.",
		)
		require(idle.size == NymphSpeech.IDLE.size) {
			"the Nymph has ${NymphSpeech.IDLE.size} idle keys and ${idle.size} lines written for them"
		}
		NymphSpeech.IDLE.forEachIndexed { i, key -> builder.add(key, idle[i]) }

		val warnings = listOf(
			"Stop.",
			"That was alive, and now it is not.",
			"I am counting. You should know that I am counting.",
		)
		require(warnings.size == NymphSpeech.WARNINGS.size) {
			"the Nymph has ${NymphSpeech.WARNINGS.size} warning keys and ${warnings.size} lines written for them"
		}
		NymphSpeech.WARNINGS.forEachIndexed { i, key -> builder.add(key, warnings[i]) }

		builder.add(NymphSpeech.WRATH_KEY, "Enough.")
		builder.add(NymphSpeech.REFUSAL_KEY, "We are past talking.")
		builder.add(NymphSpeech.SETTLED_KEY, "Very well. Go carefully, and mind where you put your feet.")
		builder.add(NymphSpeech.AMENDS_KEY, "It does not undo it. Keep going.")
		builder.add(
			NymphSpeech.GIFT_KEY,
			"For me? Put it in the ground instead. It will do better there than in your pocket.",
		)
	}
}
