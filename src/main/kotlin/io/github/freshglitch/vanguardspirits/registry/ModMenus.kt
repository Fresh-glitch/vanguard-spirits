package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.menu.BindingAltarMenu
import io.github.freshglitch.vanguardspirits.menu.GoldenChestMenu
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.inventory.MenuType

object ModMenus {
	val GOLDEN_CHEST: MenuType<GoldenChestMenu> = Registry.register(
		BuiltInRegistries.MENU,
		VanguardSpirits.id("golden_chest"),
		MenuType(::GoldenChestMenu, FeatureFlags.VANILLA_SET),
	)

	val BINDING_ALTAR: MenuType<BindingAltarMenu> = Registry.register(
		BuiltInRegistries.MENU,
		VanguardSpirits.id("binding_altar"),
		MenuType(::BindingAltarMenu, FeatureFlags.VANILLA_SET),
	)

	/** Touching the object is what actually runs the registrations above. */
	fun register() = Unit
}
