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

	val CHARM_OF_THE_LEAPER: CharmItem = registerCharm(
		path = "charm_of_the_leaper",
		aura = CharmAura(MobEffects.JUMP_BOOST),
	)

	val CHARM_OF_THE_WANDERER: CharmItem = registerCharm(
		path = "charm_of_the_wanderer",
		aura = CharmAura(MobEffects.SPEED),
	)

	val CHARM_OF_THE_DELVER: CharmItem = registerCharm(
		path = "charm_of_the_delver",
		aura = CharmAura(MobEffects.NIGHT_VISION),
	)

	/**
	 * Sends back what is thrown at you, and eats three quarters of a full
	 * attunement doing it.
	 *
	 * The cost is the design. At the cap of four it leaves room for exactly one
	 * other charm, so taking this is a decision about what to give up rather
	 * than another thing to carry.
	 */
	val CHARM_OF_THE_RETURNED: CharmItem = registerCharm(
		path = "charm_of_the_returned",
		aura = CharmAura(ModEffects.DEFLECTION),
		cost = 3,
	)

	/** Deep-ruin loot. Consumed to raise the holder's attunement cap. */
	val ECHO_OF_KINSHIP: Item = register("echo_of_kinship") { props ->
		EchoOfKinshipItem(
			props.stacksTo(1)
				.rarity(Rarity.EPIC)
				// Worn permanently, the way the Nether Star wears it. The shimmer
				// is vanilla's glint, so it costs nothing and matches what a
				// player already reads as "this is not an ordinary object".
				.component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
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

	private fun registerCharm(path: String, aura: CharmAura, cost: Int = 1): CharmItem =
		register(path) { props ->
			CharmItem(
				props.stacksTo(1)
					.rarity(if (cost > 1) Rarity.EPIC else Rarity.RARE)
					.component(DataComponents.LORE, flavour(path)),
				aura,
				cost,
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
