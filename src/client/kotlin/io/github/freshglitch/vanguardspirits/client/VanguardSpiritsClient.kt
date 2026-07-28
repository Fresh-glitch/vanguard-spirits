package io.github.freshglitch.vanguardspirits.client

import io.github.freshglitch.vanguardspirits.client.render.ChestParts
import io.github.freshglitch.vanguardspirits.client.render.GoldenChestRenderer
import io.github.freshglitch.vanguardspirits.client.screen.GoldenChestScreen
import io.github.freshglitch.vanguardspirits.registry.ModBlockEntities
import io.github.freshglitch.vanguardspirits.registry.ModMenus
import net.fabricmc.api.ClientModInitializer
import net.minecraft.client.gui.screens.MenuScreens
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers

object VanguardSpiritsClient : ClientModInitializer {
	override fun onInitializeClient() {
		CharmTooltip.register()

		ModelLayerRegistry.registerModelLayer(ChestParts.LAYER) { ChestParts.createLayer() }
		BlockEntityRenderers.register(ModBlockEntities.GOLDEN_CHEST, ::GoldenChestRenderer)
		MenuScreens.register(ModMenus.GOLDEN_CHEST, ::GoldenChestScreen)
	}
}
