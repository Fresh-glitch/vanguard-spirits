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
		val centreX = chunkPos.middleBlockX
		val centreZ = chunkPos.middleBlockZ
		val half = GuardedRuinsPiece.WIDTH / 2

		// Probe the footprint corners rather than just the centre: one sample
		// cannot tell a flat clearing from a cliff edge or a lake shore.
		var lowest = Int.MAX_VALUE
		var highest = Int.MIN_VALUE

		for (dx in intArrayOf(-half, half)) {
			for (dz in intArrayOf(-half, half)) {
				val x = centreX + dx
				val z = centreZ + dz

				val floor = heightAt(context, x, z, Heightmap.Types.OCEAN_FLOOR_WG)
				val surface = heightAt(context, x, z, Heightmap.Types.WORLD_SURFACE_WG)

				// WORLD_SURFACE counts fluids, OCEAN_FLOOR does not. A gap between
				// them means standing water -- ocean, lake or river -- above the
				// spot we would build on.
				if (surface > floor) return Optional.empty()

				if (floor < lowest) lowest = floor
				if (floor > highest) highest = floor
			}
		}

		// Refuse hillsides, which would leave the ruin half buried and half stilted.
		if (highest - lowest > MAX_RELIEF) return Optional.empty()

		// Anchor to the lowest corner so the floor settles into the ground rather
		// than perching on top of it.
		val origin = BlockPos(centreX, lowest - 1, centreZ)

		return Optional.of(
			GenerationStub(origin) { builder -> builder.addPiece(GuardedRuinsPiece(origin)) },
		)
	}

	private fun heightAt(
		context: GenerationContext,
		x: Int,
		z: Int,
		type: Heightmap.Types,
	): Int = context.chunkGenerator().getFirstOccupiedHeight(
		x,
		z,
		type,
		context.heightAccessor(),
		context.randomState(),
	)

	override fun type(): StructureType<*> = ModStructures.GUARDED_RUINS

	companion object {
		/** Largest corner-to-corner height difference we will still build on. */
		private const val MAX_RELIEF = 4

		val CODEC: MapCodec<GuardedRuinsStructure> = simpleCodec(::GuardedRuinsStructure)
	}
}
