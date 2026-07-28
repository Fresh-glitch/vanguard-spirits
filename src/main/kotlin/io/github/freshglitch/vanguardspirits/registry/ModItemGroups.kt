package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.CreativeModeTabs
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

		addToVanillaTabs()
	}

	/**
	 * Mirrors the items into the vanilla tabs as well, so players who never
	 * scroll to a modded tab still find them where they'd expect.
	 */
	private fun addToVanillaTabs() {
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register { output ->
			output.accept(ModItems.FRACTURED_MEMORY)
		}

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register { output ->
			ModItems.CHARMS.forEach(output::accept)
			output.accept(ModItems.ECHO_OF_KINSHIP)
		}
	}
}
