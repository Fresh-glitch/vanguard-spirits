package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack

object ModItemGroups {
	val VANGUARD_SPIRITS: ResourceKey<CreativeModeTab> =
		ResourceKey.create(Registries.CREATIVE_MODE_TAB, VanguardSpirits.id("vanguard_spirits"))

	const val TITLE_KEY: String = "itemGroup.vanguard-spirits.vanguard_spirits"

	fun register() {
		val tab = FabricCreativeModeTab.builder()
			.title(Component.translatable(TITLE_KEY))
			.icon { ItemStack(ModItems.FRACTURED_MEMORY) }
			.displayItems { _, output -> ModItems.ALL.forEach(output::accept) }
			.build()

		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, VANGUARD_SPIRITS, tab)
	}
}
