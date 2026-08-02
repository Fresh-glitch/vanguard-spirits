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
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity
import net.minecraft.world.item.SpawnEggItem
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

	/**
	 * The three spawn eggs.
	 *
	 * 26.2 no longer tints a shared template, so each of these is its own
	 * painting under `textures/item/`; the only thing code decides is which type
	 * comes out. Registered last so they sort to the end of the mod's own tab,
	 * the way vanilla keeps its eggs out of the way of real items.
	 */
	val STONE_SENTINEL_SPAWN_EGG: Item = registerEgg("stone_sentinel", ModEntities.STONE_SENTINEL)
	val REMNANT_SPAWN_EGG: Item = registerEgg("remnant", ModEntities.REMNANT)
	val MOURNER_SPAWN_EGG: Item = registerEgg("mourner", ModEntities.MOURNER)

	/** Every item this mod registers, in display order. */
	val ALL: List<Item> get() = ordered

	/** Just the eggs, for the vanilla spawn egg tab. */
	val SPAWN_EGGS: List<Item> get() =
		listOf(STONE_SENTINEL_SPAWN_EGG, REMNANT_SPAWN_EGG, MOURNER_SPAWN_EGG)

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

	private fun registerEgg(mob: String, type: EntityType<out Mob>): Item =
		register("${mob}_spawn_egg") { props -> SpawnEggItem(props.spawnEgg(type)) }

	private fun <T : Item> register(path: String, factory: (Item.Properties) -> T): T {
		val key = ResourceKey.create(Registries.ITEM, VanguardSpirits.id(path))
		val item = Registry.register(BuiltInRegistries.ITEM, key, factory(Item.Properties().setId(key)))
		ordered += item
		return item
	}
}
