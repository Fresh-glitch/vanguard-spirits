package io.github.freshglitch.vanguardspirits

import io.github.freshglitch.vanguardspirits.charm.Attunement
import io.github.freshglitch.vanguardspirits.charm.CharmTicker
import io.github.freshglitch.vanguardspirits.registry.ModBlockEntities
import io.github.freshglitch.vanguardspirits.registry.ModBlocks
import io.github.freshglitch.vanguardspirits.registry.ModEntities
import io.github.freshglitch.vanguardspirits.registry.ModItemGroups
import io.github.freshglitch.vanguardspirits.registry.ModItems
import io.github.freshglitch.vanguardspirits.registry.ModMenus
import io.github.freshglitch.vanguardspirits.registry.ModParticles
import io.github.freshglitch.vanguardspirits.registry.ModSounds
import io.github.freshglitch.vanguardspirits.registry.ModStructures
import io.github.freshglitch.vanguardspirits.worldgen.RuinAmbience
import io.github.freshglitch.vanguardspirits.worldgen.RuinBeacon
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
		ModSounds.register()
		ModParticles.register()
		ModItems.register()
		ModItemGroups.register()
		ModStructures.register()
		ModEntities.register()
		Attunement.register()
		CharmTicker.register()
		RuinAmbience.register()
		RuinBeacon.register()

		LOGGER.info("Vanguard Spirits awakened with {} items.", ModItems.ALL.size)
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
