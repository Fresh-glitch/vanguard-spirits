package io.github.freshglitch.vanguardspirits.client

import io.github.freshglitch.vanguardspirits.client.particle.EchoRuneParticle
import io.github.freshglitch.vanguardspirits.client.particle.MemoryMoteParticle
import io.github.freshglitch.vanguardspirits.client.particle.StoneWakeParticle
import io.github.freshglitch.vanguardspirits.client.particle.WardRuneParticle
import io.github.freshglitch.vanguardspirits.client.render.ChestParts
import io.github.freshglitch.vanguardspirits.client.render.GoldenChestRenderer
import io.github.freshglitch.vanguardspirits.client.render.MournerModel
import io.github.freshglitch.vanguardspirits.client.render.MournerRenderer
import io.github.freshglitch.vanguardspirits.client.render.RemnantModel
import io.github.freshglitch.vanguardspirits.client.render.RemnantRenderer
import io.github.freshglitch.vanguardspirits.client.render.StoneSentinelModel
import io.github.freshglitch.vanguardspirits.client.render.StoneSentinelRenderer
import io.github.freshglitch.vanguardspirits.client.screen.GoldenChestScreen
import io.github.freshglitch.vanguardspirits.registry.ModBlockEntities
import io.github.freshglitch.vanguardspirits.registry.ModEntities
import io.github.freshglitch.vanguardspirits.registry.ModMenus
import io.github.freshglitch.vanguardspirits.registry.ModParticles
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.minecraft.client.gui.screens.MenuScreens
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers

object VanguardSpiritsClient : ClientModInitializer {
	override fun onInitializeClient() {
		CharmTooltip.register()

		ModelLayerRegistry.registerModelLayer(ChestParts.LAYER) { ChestParts.createLayer() }
		BlockEntityRenderers.register(ModBlockEntities.GOLDEN_CHEST, ::GoldenChestRenderer)

		ModelLayerRegistry.registerModelLayer(StoneSentinelModel.LAYER) { StoneSentinelModel.createLayer() }
		EntityRendererRegistry.register(ModEntities.STONE_SENTINEL, ::StoneSentinelRenderer)

		ModelLayerRegistry.registerModelLayer(RemnantModel.LAYER) { RemnantModel.createLayer() }
		EntityRendererRegistry.register(ModEntities.REMNANT, ::RemnantRenderer)

		ModelLayerRegistry.registerModelLayer(MournerModel.LAYER) { MournerModel.createLayer() }
		EntityRendererRegistry.register(ModEntities.MOURNER, ::MournerRenderer)
		MenuScreens.register(ModMenus.GOLDEN_CHEST, ::GoldenChestScreen)

		// Registered pending: the sprite set for a particle does not exist until
		// the particle atlas has been stitched, which is long after mod init.
		val particles = ParticleProviderRegistry.getInstance()
		particles.register(ModParticles.MEMORY_MOTE, MemoryMoteParticle::Provider)
		particles.register(ModParticles.ECHO_RUNE, EchoRuneParticle::Provider)
		particles.register(ModParticles.STONE_WAKE, StoneWakeParticle::Provider)
		particles.register(ModParticles.WARD_RUNE, WardRuneParticle::Provider)
	}
}
