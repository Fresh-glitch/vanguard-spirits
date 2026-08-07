package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.block.entity.GoldenChestBlockEntity
import io.github.freshglitch.vanguardspirits.block.entity.MuralBlockEntity
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.entity.BlockEntityType

object ModBlockEntities {
	val GOLDEN_CHEST: BlockEntityType<GoldenChestBlockEntity> = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		VanguardSpirits.id("golden_chest"),
		BlockEntityType(::GoldenChestBlockEntity, setOf(ModBlocks.GOLDEN_CHEST)),
	)

	/**
	 * Carries no data -- it exists so a mural gets a tick, and everything it
	 * decides goes into the blockstate. See [MuralBlockEntity].
	 */
	val MURAL: BlockEntityType<MuralBlockEntity> = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		VanguardSpirits.id("mural"),
		BlockEntityType(::MuralBlockEntity, setOf(ModBlocks.MURAL)),
	)

	/** Touching the object is what actually runs the registrations above. */
	fun register() = Unit
}
