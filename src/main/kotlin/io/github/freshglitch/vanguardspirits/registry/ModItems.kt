package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.charm.CharmAura
import io.github.freshglitch.vanguardspirits.item.CharmItem
import io.github.freshglitch.vanguardspirits.item.EchoOfKinshipItem
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.component.ItemLore

object ModItems {
	/** Registration order, reused as the creative tab display order. */
	private val ordered = mutableListOf<Item>()

	/** Dropped by Guarded Ruins. The raw material every charm is forged from. */
	val FRACTURED_MEMORY: Item = register("fractured_memory") { props ->
		Item(props.stacksTo(16).rarity(Rarity.UNCOMMON))
	}

	val CHARM_OF_THE_SENTINEL: CharmItem = registerCharm(
		path = "charm_of_the_sentinel",
		aura = CharmAura(MobEffects.RESISTANCE),
	)

	val CHARM_OF_THE_WANDERER: CharmItem = registerCharm(
		path = "charm_of_the_wanderer",
		aura = CharmAura(MobEffects.SPEED),
	)

	val CHARM_OF_THE_DELVER: CharmItem = registerCharm(
		path = "charm_of_the_delver",
		aura = CharmAura(MobEffects.NIGHT_VISION),
	)

	/** Deep-ruin loot. Consumed to raise the holder's attunement cap. */
	val ECHO_OF_KINSHIP: Item = register("echo_of_kinship") { props ->
		EchoOfKinshipItem(
			props.stacksTo(1)
				.rarity(Rarity.EPIC)
				.component(DataComponents.LORE, flavour("echo_of_kinship")),
		)
	}

	/** Every item this mod registers, in display order. */
	val ALL: List<Item> get() = ordered

	/** Only the charms, for systems that iterate auras. */
	val CHARMS: List<CharmItem> get() = ordered.filterIsInstance<CharmItem>()

	/** Touching the object is what actually runs the registrations above. */
	fun register() = Unit

	private fun registerCharm(path: String, aura: CharmAura): CharmItem =
		register(path) { props ->
			CharmItem(
				props.stacksTo(1)
					.rarity(Rarity.RARE)
					.component(DataComponents.LORE, flavour(path)),
				aura,
			)
		}

	/** Translation key holding a charm's flavour line. */
	fun loreKey(path: String): String = "lore.vanguard-spirits.$path"

	// Flavour only. Whether a charm is actually live depends on the holder, so
	// that line is added client-side by CharmTooltip instead of baked in here.
	private fun flavour(path: String) = ItemLore(listOf(Component.translatable(loreKey(path))))

	private fun <T : Item> register(path: String, factory: (Item.Properties) -> T): T {
		val key = ResourceKey.create(Registries.ITEM, VanguardSpirits.id(path))
		val item = Registry.register(BuiltInRegistries.ITEM, key, factory(Item.Properties().setId(key)))
		ordered += item
		return item
	}
}
