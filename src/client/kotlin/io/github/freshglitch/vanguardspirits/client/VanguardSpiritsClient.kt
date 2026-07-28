package io.github.freshglitch.vanguardspirits.client

import net.fabricmc.api.ClientModInitializer

object VanguardSpiritsClient : ClientModInitializer {
	override fun onInitializeClient() {
		CharmTooltip.register()
	}
}
