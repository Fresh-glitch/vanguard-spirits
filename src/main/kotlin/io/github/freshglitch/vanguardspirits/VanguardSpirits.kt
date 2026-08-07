package io.github.freshglitch.vanguardspirits

import io.github.freshglitch.vanguardspirits.charm.Attunement
import io.github.freshglitch.vanguardspirits.charm.CharmTicker
import io.github.freshglitch.vanguardspirits.charm.Deflection
import io.github.freshglitch.vanguardspirits.lore.MuralCodex
import io.github.freshglitch.vanguardspirits.registry.ModBlockEntities
import io.github.freshglitch.vanguardspirits.registry.ModBlocks
import io.github.freshglitch.vanguardspirits.registry.ModComponents
import io.github.freshglitch.vanguardspirits.registry.ModEffects
import io.github.freshglitch.vanguardspirits.registry.ModEntities
import io.github.freshglitch.vanguardspirits.registry.ModItemGroups
import io.github.freshglitch.vanguardspirits.registry.ModItems
import io.github.freshglitch.vanguardspirits.registry.ModMenus
import io.github.freshglitch.vanguardspirits.registry.ModNetworking
import io.github.freshglitch.vanguardspirits.registry.ModParticles
import io.github.freshglitch.vanguardspirits.registry.ModSounds
import io.github.freshglitch.vanguardspirits.registry.ModSpawns
import io.github.freshglitch.vanguardspirits.registry.ModStructures
import io.github.freshglitch.vanguardspirits.registry.ModTriggers
import io.github.freshglitch.vanguardspirits.worldgen.RuinAmbience
import io.github.freshglitch.vanguardspirits.worldgen.RuinBeacon
import io.github.freshglitch.vanguardspirits.worldgen.RuinSeal
import io.github.freshglitch.vanguardspirits.worldgen.RuinVigil
import net.fabricmc.api.ModInitializer
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object VanguardSpirits : ModInitializer {
	const val MOD_ID: String = "vanguard-spirits"

	private val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		// Blocks first: the block entity type needs its block to already exist.
		ModBlocks.register()
		ModBlockEntities.register()
		ModMenus.register()
		// Before the items: a charm reads its own hushed component.
		ModComponents.register()
		// Before the items: a charm names the effect it grants.
		ModEffects.register()
		ModSounds.register()
		ModParticles.register()
		// Entities before items: the spawn eggs name the type they hatch, so the
		// types have to exist first. Kotlin would initialise them on first touch
		// anyway, but leaving that to chance hides a real ordering requirement.
		ModEntities.register()
		ModItems.register()
		ModItemGroups.register()
		ModStructures.register()
		ModSpawns.register()
		Attunement.register()
		MuralCodex.register()
		ModTriggers.register()
		ModNetworking.register()
		CharmTicker.register()
		Deflection.register()
		RuinAmbience.register()
		RuinBeacon.register()
		RuinVigil.register()
		RuinSeal.register()

		LOGGER.info("Vanguard Spirits awakened with {} items.", ModItems.ALL.size)
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
