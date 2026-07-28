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
}
