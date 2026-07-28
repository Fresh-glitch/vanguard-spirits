package io.github.freshglitch.vanguardspirits

import io.github.freshglitch.vanguardspirits.charm.Attunement
import io.github.freshglitch.vanguardspirits.charm.CharmTicker
import io.github.freshglitch.vanguardspirits.registry.ModItemGroups
import io.github.freshglitch.vanguardspirits.registry.ModItems
import net.fabricmc.api.ModInitializer
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object VanguardSpirits : ModInitializer {
	const val MOD_ID: String = "vanguard-spirits"

	private val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		ModItems.register()
		ModItemGroups.register()
		Attunement.register()
		CharmTicker.register()

		LOGGER.info("Vanguard Spirits awakened with {} items.", ModItems.ALL.size)
	}

	fun id(path: String): Identifier
		= Identifier.fromNamespaceAndPath(MOD_ID, path)
}
