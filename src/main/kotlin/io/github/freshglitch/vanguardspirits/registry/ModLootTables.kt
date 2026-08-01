package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.storage.loot.LootTable

/**
 * Loot tables the mod refers to by name from code.
 *
 * The Sentinel's is not here: an entity's table is found by convention from its
 * id, so nothing needs to name it. A chest has to be told which table it rolls,
 * which means this one needs a key.
 */
object ModLootTables {

	/** What the Reliquary in the vault is holding. */
	val GILDED_RELIQUARY: ResourceKey<LootTable> =
		ResourceKey.create(Registries.LOOT_TABLE, VanguardSpirits.id("chests/gilded_reliquary"))
}
