package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.worldgen.GraveyardPiece
import io.github.freshglitch.vanguardspirits.worldgen.GuardedRuinsPiece
import io.github.freshglitch.vanguardspirits.worldgen.GuardedRuinsStructure
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureType
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType

object ModStructures {
	/**
	 * Data-driven key for the structure itself. The `Structure` instance lives in
	 * a dynamic registry and is produced by datagen, so only the key is a constant.
	 */
	val GUARDED_RUINS_KEY: ResourceKey<Structure> =
		ResourceKey.create(Registries.STRUCTURE, VanguardSpirits.id("guarded_ruins"))

	val GUARDED_RUINS_PIECE: StructurePieceType = Registry.register(
		BuiltInRegistries.STRUCTURE_PIECE,
		VanguardSpirits.id("guarded_ruins"),
		StructurePieceType.ContextlessType(::GuardedRuinsPiece),
	)

	/**
	 * The surface half of a Guarded Ruin.
	 *
	 * A second piece of the same structure rather than a structure of its own, so
	 * a graveyard cannot generate without the ruin under it and everything that
	 * already keys off the start -- [io.github.freshglitch.vanguardspirits.worldgen.RuinSeal],
	 * [io.github.freshglitch.vanguardspirits.worldgen.RuinBeacon] -- keeps working
	 * untouched.
	 */
	val GRAVEYARD_PIECE: StructurePieceType = Registry.register(
		BuiltInRegistries.STRUCTURE_PIECE,
		VanguardSpirits.id("graveyard"),
		StructurePieceType.ContextlessType(::GraveyardPiece),
	)

	val GUARDED_RUINS: StructureType<GuardedRuinsStructure> = Registry.register(
		BuiltInRegistries.STRUCTURE_TYPE,
		VanguardSpirits.id("guarded_ruins"),
		StructureType { GuardedRuinsStructure.CODEC },
	)

	/** Touching the object is what actually runs the registrations above. */
	fun register() = Unit
}
