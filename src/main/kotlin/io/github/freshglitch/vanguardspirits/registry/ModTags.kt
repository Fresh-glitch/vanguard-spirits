package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.level.biome.Biome

object ModTags {
	/**
	 * Biomes a Guarded Ruin may appear in. Data-driven on purpose so packs can
	 * retune where the ruins show up without touching code.
	 */
	val GUARDED_RUINS_BIOMES: TagKey<Biome> =
		TagKey.create(Registries.BIOME, VanguardSpirits.id("has_structure/guarded_ruins"))

	/**
	 * Woodland a Mourner might turn up in of its own accord.
	 *
	 * Nothing to do with the ruins: those birds are placed by the beacon and
	 * belong to a structure. This is the handful of forests where one may simply
	 * exist, and a tag rather than a list so packs can widen it.
	 */
	val MOURNER_BIOMES: TagKey<Biome> =
		TagKey.create(Registries.BIOME, VanguardSpirits.id("spawns_mourner"))
}
