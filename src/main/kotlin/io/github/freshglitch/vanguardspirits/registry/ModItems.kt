package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.charm.CharmAura
import io.github.freshglitch.vanguardspirits.charm.CharmGrant
import io.github.freshglitch.vanguardspirits.item.CharmItem
import io.github.freshglitch.vanguardspirits.item.EchoOfKinshipItem
import io.github.freshglitch.vanguardspirits.item.FracturedMemoryItem
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.effect.MobEffect
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
		FracturedMemoryItem(props.stacksTo(16).rarity(Rarity.UNCOMMON))
	}

	/**
	 * Shed by a startled Mourner, and the reason not to shoot one.
	 *
	 * Killing a Mourner ends its ruin's vigil for good, so the mod must never
	 * make the kill the profitable move. This is the way round that: the bird
	 * leaves one behind when it is put up off a perch, so a player who works out
	 * they can flush the same bird every few minutes does better than one who
	 * shoots it once. An anchored Mourner drops nothing at all when it dies --
	 * see `Mourner.shouldDropLoot`.
	 */
	val MOURNER_FEATHER: Item = register("mourner_feather") { props ->
		Item(props.component(DataComponents.LORE, flavour("mourner_feather")))
	}

	val CHARM_OF_THE_LEAPER: CharmItem = registerCharm(
		path = "charm_of_the_leaper",
		depths = stronger(MobEffects.JUMP_BOOST),
	)

	val CHARM_OF_THE_WANDERER: CharmItem = registerCharm(
		path = "charm_of_the_wanderer",
		depths = stronger(MobEffects.SPEED),
	)

	/**
	 * Ilen, who never once needed a torch.
	 *
	 * The one charm that cannot deepen on an amplifier: night vision has exactly
	 * one strength, because `GameRenderer.nightVisionScale` reads only the
	 * instance's duration and never its amplifier. A Delver II built the way the
	 * Leaper and the Wanderer are would have been indistinguishable from a Delver
	 * I at twice the price.
	 *
	 * So it deepens by widening instead. Ilen's line is about being underground
	 * rather than about brightness, and haste is what the rest of being a delver
	 * feels like.
	 */
	val CHARM_OF_THE_DELVER: CharmItem = registerCharm(
		path = "charm_of_the_delver",
		depths = listOf(
			CharmAura(MobEffects.NIGHT_VISION),
			CharmAura(
				listOf(CharmGrant(MobEffects.NIGHT_VISION), CharmGrant(MobEffects.HASTE)),
				cost = 2,
			),
			CharmAura(
				listOf(CharmGrant(MobEffects.NIGHT_VISION), CharmGrant(MobEffects.HASTE, 1)),
				cost = 3,
			),
		),
	)

	/**
	 * Sends back what is thrown at you, and eats three quarters of a full
	 * attunement doing it.
	 *
	 * The cost is the design. At the cap of four it leaves room for exactly one
	 * other charm, so taking this is a decision about what to give up rather
	 * than another thing to carry.
	 *
	 * It is also the one charm that does not deepen, and for the same reason: a
	 * second depth at cost four would be the entire budget and a third would not
	 * fit in it at all. There is no room above three to sell, so the Returned
	 * stays what it already was -- the thing you build a whole attunement around.
	 */
	val CHARM_OF_THE_RETURNED: CharmItem = registerCharm(
		path = "charm_of_the_returned",
		depths = listOf(CharmAura(ModEffects.DEFLECTION, cost = 3)),
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

	private fun registerCharm(path: String, depths: List<CharmAura>): CharmItem =
		register(path) { props ->
			CharmItem(
				props.stacksTo(1)
					// Judged on what the charm costs unbound, since that is what
					// every one of them is when it first reaches a player. A
					// deepened Wanderer is the same object, more deeply bound.
					.rarity(if (depths.first().cost > 1) Rarity.EPIC else Rarity.RARE)
					.component(DataComponents.LORE, flavour(path)),
				depths,
			)
		}

	/**
	 * Three depths of one effect, each a level stronger and an attunement dearer.
	 *
	 * The shape for a charm whose effect vanilla already scales. Costs run 1, 2, 3
	 * so the deepest binding of anything takes three quarters of a full
	 * attunement -- the same price the Returned pays, and for the same reason.
	 */
	private fun stronger(effect: Holder<MobEffect>, depths: Int = 3): List<CharmAura> =
		(0 until depths).map { step -> CharmAura(effect, amplifier = step, cost = step + 1) }

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
