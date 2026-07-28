package io.github.freshglitch.vanguardspirits.client.datagen

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.registry.ModStructures
import io.github.freshglitch.vanguardspirits.registry.ModTags
import io.github.freshglitch.vanguardspirits.worldgen.GuardedRuinsStructure
import net.minecraft.core.registries.Registries
import net.minecraft.data.worldgen.BootstrapContext
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.levelgen.GenerationStep
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureSet
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType

/**
 * Builds the dynamic-registry entries for Guarded Ruins.
 *
 * The `Structure` and its `StructureSet` are data, not code -- these bootstraps
 * run at datagen time and the resulting JSON ships in the mod.
 */
object ModWorldgenBootstrap {

	val GUARDED_RUINS_SET: ResourceKey<StructureSet> =
		ResourceKey.create(Registries.STRUCTURE_SET, VanguardSpirits.id("guarded_ruins"))

	fun structures(context: BootstrapContext<Structure>) {
		val biomes = context.lookup(Registries.BIOME)

		context.register(
			ModStructures.GUARDED_RUINS_KEY,
			GuardedRuinsStructure(
				Structure.StructureSettings(
					biomes.getOrThrow(ModTags.GUARDED_RUINS_BIOMES),
					emptyMap(),
					GenerationStep.Decoration.SURFACE_STRUCTURES,
					// Blend the surrounding terrain into the footprint instead of
					// leaving the ruin floating on a hillside.
					TerrainAdjustment.BEARD_THIN,
				),
			),
		)
	}

	fun structureSets(context: BootstrapContext<StructureSet>) {
		val structures = context.lookup(Registries.STRUCTURE)

		context.register(
			GUARDED_RUINS_SET,
			StructureSet(
				structures.getOrThrow(ModStructures.GUARDED_RUINS_KEY),
				// One candidate per 24-chunk cell with a 12-chunk buffer: uncommon
				// enough to be worth finding, common enough to test.
				RandomSpreadStructurePlacement(24, 12, RandomSpreadType.LINEAR, SALT),
			),
		)
	}

	/** Keeps our spread from correlating with vanilla structure sets. */
	private const val SALT = 851_274_913
}
