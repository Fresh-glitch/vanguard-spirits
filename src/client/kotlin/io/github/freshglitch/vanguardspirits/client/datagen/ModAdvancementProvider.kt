package io.github.freshglitch.vanguardspirits.client.datagen

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.charm.Attunement
import io.github.freshglitch.vanguardspirits.registry.ModBlocks
import io.github.freshglitch.vanguardspirits.registry.ModEntities
import io.github.freshglitch.vanguardspirits.registry.ModItems
import io.github.freshglitch.vanguardspirits.registry.ModStructures
import io.github.freshglitch.vanguardspirits.registry.ModTriggers
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricAdvancementProvider
import net.minecraft.advancements.Advancement
import net.minecraft.advancements.AdvancementHolder
import net.minecraft.advancements.AdvancementRequirements
import net.minecraft.advancements.AdvancementType
import net.minecraft.advancements.predicates.LocationPredicate
import net.minecraft.advancements.predicates.entity.EntityPredicate
import net.minecraft.advancements.predicates.entity.EntityTypePredicate
import net.minecraft.advancements.triggers.InventoryChangeTrigger
import net.minecraft.advancements.triggers.KilledTrigger
import net.minecraft.advancements.triggers.PlayerTrigger
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.ItemLike
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

/**
 * The mod's progression, written down where a player can read it.
 *
 * Until now none of this was visible anywhere: a Guarded Ruin gives no hint that
 * the Reliquary will not open until the Sentinel is down, nor that the shards
 * inside become charms, nor that attunement caps what you can carry. The tree is
 * as much documentation as reward.
 *
 * Shaped to follow the actual gate rather than the fiction. `RuinSeal` keeps the
 * Reliquary shut until its Sentinel has been felled, so [ModAdvancements.MEMORY]
 * hangs off [ModAdvancements.SENTINEL] -- a player cannot reach one without the
 * other, and the tree saying so is the clearest explanation of that rule the mod
 * has.
 *
 * The two side branches off the root are the things you can do *without* going
 * down: take a feather off a bird, and open a grave. That is deliberate. The
 * surface half of a ruin should look like content in its own right.
 */
class ModAdvancementProvider(
	output: FabricPackOutput,
	registryLookup: CompletableFuture<HolderLookup.Provider>,
) : FabricAdvancementProvider(output, registryLookup) {

	override fun generateAdvancement(
		registries: HolderLookup.Provider,
		consumer: Consumer<AdvancementHolder>,
	) {
		val entityTypes = registries.lookupOrThrow(Registries.ENTITY_TYPE)

		// The structure only exists in a dynamic registry, built by
		// ModWorldgenBootstrap in this same datagen run -- so it has to be looked
		// up from the provider rather than named as a constant.
		val ruin = registries.lookupOrThrow(Registries.STRUCTURE)
			.getOrThrow(ModStructures.GUARDED_RUINS_KEY)

		val root = Advancement.Builder.advancement()
			.display(
				ModBlocks.GRAVE,
				ModAdvancements.title(ModAdvancements.ROOT),
				ModAdvancements.description(ModAdvancements.ROOT),
				// The block the ruins are built from. Vanilla's own backdrops are
				// nothing more than block textures -- `backgrounds/stone.png` is
				// byte-for-byte the stone block, and the panel darkens it at draw
				// time -- so pointing straight at deepslate bricks gets the right
				// look without shipping a copy of a Mojang texture in a jam entry.
				Identifier.withDefaultNamespace("block/deepslate_bricks"),
				AdvancementType.TASK,
				// Vanilla suppresses both of these on roots because its roots are
				// trivial -- you always end up with a crafting table. This one
				// fires on finding a Guarded Ruin, which is the single most
				// notable moment in the mod, and unlocking it in silence made the
				// discovery look like nothing had happened.
				/* showToast = */ true,
				/* announceChat = */ true,
				/* hidden = */ false,
			)
			// Fires anywhere inside the structure's bounding box, which includes
			// the graveyard -- so arriving on the surface is enough and a player
			// does not have to find the stair first to be told they found it.
			.addCriterion(
				"entered_ruin",
				PlayerTrigger.TriggerInstance.located(LocationPredicate.Builder.inStructure(ruin)),
			)
			.save(consumer, id(ModAdvancements.ROOT.path))

		child(consumer, root, ModAdvancements.FEATHER, ModItems.MOURNER_FEATHER) { builder ->
			builder.addCriterion(
				"has_feather",
				InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.MOURNER_FEATHER),
			)
		}

		child(
			consumer, root, ModAdvancements.REMNANT,
			net.minecraft.world.item.Items.ROTTEN_FLESH,
		) { builder ->
			builder.addCriterion(
				"killed_remnant",
				KilledTrigger.TriggerInstance.playerKilledEntity(
					EntityPredicate.Builder.entity()
						.entityType(EntityTypePredicate.of(entityTypes, ModEntities.REMNANT)),
				),
			)
		}

		val sentinel = child(
			consumer, root, ModAdvancements.SENTINEL,
			ModItems.STONE_SENTINEL_SPAWN_EGG, AdvancementType.GOAL,
		) { builder ->
			builder.addCriterion(
				"killed_sentinel",
				KilledTrigger.TriggerInstance.playerKilledEntity(
					EntityPredicate.Builder.entity()
						.entityType(EntityTypePredicate.of(entityTypes, ModEntities.STONE_SENTINEL)),
				),
			)
		}

		val memory = child(
			consumer, sentinel, ModAdvancements.MEMORY, ModItems.FRACTURED_MEMORY,
		) { builder ->
			builder.addCriterion(
				"has_memory",
				InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.FRACTURED_MEMORY),
			)
		}

		val charm = child(
			consumer, memory, ModAdvancements.CHARM, ModItems.CHARM_OF_THE_WANDERER,
		) { builder ->
			// One criterion per charm, satisfied by any -- so this reads "forge a
			// charm", not "forge the Wanderer". Adding a fifth charm later picks
			// itself up here, because the list comes off the registry.
			ModItems.CHARMS.forEach { item ->
				builder.addCriterion(
					"has_${key(item)}",
					InventoryChangeTrigger.TriggerInstance.hasItems(item),
				)
			}
			builder.requirements(AdvancementRequirements.Strategy.OR)
		}

		child(
			consumer, charm, ModAdvancements.ALL_CHARMS,
			ModItems.CHARM_OF_THE_DELVER, AdvancementType.CHALLENGE,
		) { builder ->
			// A single criterion listing every charm, so all of them are required
			// at once rather than one after another.
			builder.addCriterion(
				"has_every_charm",
				InventoryChangeTrigger.TriggerInstance.hasItems(*ModItems.CHARMS.toTypedArray()),
			)
		}

		// Hangs off the Sentinel, not the Reliquary. Its loot table drops an Echo
		// at fifty per cent, so felling one is the actual prerequisite and a
		// Fractured Memory is not needed at all. Parented under the Reliquary at
		// first, and a playthrough earned this six seconds *before* its parent --
		// advancement parents are display-only, so nothing broke, but the tab
		// showed a finished child under an unfinished one.
		val kinship = child(
			consumer, sentinel, ModAdvancements.KINSHIP,
			ModItems.ECHO_OF_KINSHIP, AdvancementType.GOAL,
		) { builder ->
			builder.addCriterion(
				"has_kinship",
				InventoryChangeTrigger.TriggerInstance.hasItems(ModItems.ECHO_OF_KINSHIP),
			)
		}

		// Hangs off the Echo because Echoes are the only way to raise the cap.
		// Iconed with the Returned, which costs three of the four on its own --
		// so the charm is the reason the cap is worth reaching.
		child(
			consumer, kinship, ModAdvancements.ATTUNED,
			ModItems.CHARM_OF_THE_RETURNED, AdvancementType.CHALLENGE,
		) { builder ->
			builder.addCriterion("attunement_maxed", ModTriggers.attunementReached(Attunement.MAX))
		}
	}

	private fun child(
		consumer: Consumer<AdvancementHolder>,
		parent: AdvancementHolder,
		entry: ModAdvancements.Entry,
		icon: ItemLike,
		type: AdvancementType = AdvancementType.TASK,
		criteria: (Advancement.Builder) -> Unit,
	): AdvancementHolder {
		val builder = Advancement.Builder.advancement()
			.parent(parent)
			.display(
				icon,
				ModAdvancements.title(entry),
				ModAdvancements.description(entry),
				/* background = */ null,
				type,
				/* showToast = */ true,
				/* announceChat = */ true,
				/* hidden = */ false,
			)
		criteria(builder)
		return builder.save(consumer, id(entry.path))
	}

	private fun id(path: String): String = VanguardSpirits.id(path).toString()

	private fun key(item: net.minecraft.world.item.Item): String =
		net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).path

	override fun getName(): String = "Vanguard Spirits Advancements"
}
