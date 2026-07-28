package io.github.freshglitch.vanguardspirits.worldgen

import com.mojang.serialization.MapCodec
import io.github.freshglitch.vanguardspirits.registry.ModStructures
import net.minecraft.core.BlockPos
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.levelgen.structure.Structure
import net.minecraft.world.level.levelgen.structure.StructureType
import java.util.Optional

/**
 * A Guarded Ruin: a single procedurally built piece anchored to the surface at
 * the centre of its chunk.
 *
 * There is no NBT template and no jigsaw pool -- [GuardedRuinsPiece] lays the
 * blocks itself, so the shape is varied by the world seed rather than authored.
 */
class GuardedRuinsStructure(settings: StructureSettings) : Structure(settings) {

	override fun findGenerationPoint(context: GenerationContext): Optional<GenerationStub> {
		val chunkPos = context.chunkPos()
		val x = chunkPos.middleBlockX
		val z = chunkPos.middleBlockZ

		val surface = context.chunkGenerator().getFirstOccupiedHeight(
			x,
			z,
			Heightmap.Types.WORLD_SURFACE_WG,
			context.heightAccessor(),
			context.randomState(),
		)

		// Drop it a block so the floor sits flush with the ground rather than on top.
		val origin = BlockPos(x, surface - 1, z)

		return Optional.of(
			GenerationStub(origin) { builder -> builder.addPiece(GuardedRuinsPiece(origin)) },
		)
	}

	override fun type(): StructureType<*> = ModStructures.GUARDED_RUINS

	companion object {
		val CODEC: MapCodec<GuardedRuinsStructure> = simpleCodec(::GuardedRuinsStructure)
	}
}
