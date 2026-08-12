package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
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

	/**
	 * Where a Nymph might be standing.
	 *
	 * Flower forests and nothing else, because the whole of her is built out of
	 * one: her palette is sampled from that biome's own blocks, and her fiction
	 * is that this particular wood is hers. A Nymph in a swamp would be a mob
	 * wearing the wrong clothes.
	 *
	 * A tag anyway, so a pack that wants her in cherry groves can say so without
	 * touching code.
	 */
	val NYMPH_BIOMES: TagKey<Biome> =
		TagKey.create(Registries.BIOME, VanguardSpirits.id("spawns_nymph"))

	/**
	 * What she will accept from a hand.
	 *
	 * Flowers and saplings: the two things a player can give back that are the
	 * same kind of thing they took. A tag rather than a list in code so a pack
	 * can widen what counts as an apology -- and so the answer to "will she take
	 * this?" lives somewhere a player could in principle look it up.
	 */
	val NYMPH_OFFERINGS: TagKey<Item> =
		TagKey.create(Registries.ITEM, VanguardSpirits.id("nymph_offerings"))
}
