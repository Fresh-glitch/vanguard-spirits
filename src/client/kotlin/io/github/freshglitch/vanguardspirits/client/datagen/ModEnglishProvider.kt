package io.github.freshglitch.vanguardspirits.client.datagen

import io.github.freshglitch.vanguardspirits.block.entity.GoldenChestBlockEntity
import io.github.freshglitch.vanguardspirits.item.EchoOfKinshipItem
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
		builder.add(ModItems.CHARM_OF_THE_SENTINEL, "Charm of the Sentinel")
		builder.add(ModItems.CHARM_OF_THE_WANDERER, "Charm of the Wanderer")
		builder.add(ModItems.CHARM_OF_THE_DELVER, "Charm of the Delver")
		builder.add(ModItems.ECHO_OF_KINSHIP, "Echo of Kinship")

		builder.add(ModItems.STONE_SENTINEL_SPAWN_EGG, "Stone Sentinel Spawn Egg")
		builder.add(ModItems.REMNANT_SPAWN_EGG, "Remnant Spawn Egg")
		builder.add(ModItems.MOURNER_SPAWN_EGG, "Mourner Spawn Egg")

		builder.add(
			ModItems.loreKey("charm_of_the_sentinel"),
			"The last watch never stood down.",
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
			ModItems.loreKey("echo_of_kinship"),
			"Two spirits who never met, and remember each other anyway.",
		)

		builder.add(ModBlocks.GOLDEN_CHEST, "Gilded Reliquary")
		builder.add(GoldenChestBlockEntity.NAME_KEY, "Gilded Reliquary")
		builder.add("subtitles.vanguard-spirits.golden_chest.open", "Reliquary creaks open")
		builder.add("subtitles.vanguard-spirits.golden_chest.close", "Reliquary seals")

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

		builder.add(ModEntities.STONE_SENTINEL, "Stone Sentinel")
		builder.add(ModEntities.REMNANT, "Remnant")
		builder.add(ModEntities.MOURNER, "Mourner")

		builder.add("tooltip.vanguard-spirits.charm.attuned", "Attuned")
		builder.add("tooltip.vanguard-spirits.charm.dormant", "Dormant — attunement %s in use")
		builder.add("tooltip.vanguard-spirits.charm.generic", "Attunes while carried")

		builder.add(EchoOfKinshipItem.RAISED_KEY, "Attunement deepens — %s of %s")
		builder.add(EchoOfKinshipItem.MAXED_KEY, "You can hold no deeper echo.")
	}
}
