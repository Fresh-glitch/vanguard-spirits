package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.block.entity.GoldenChestBlockEntity
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.entity.BlockEntityType

object ModBlockEntities {
	val GOLDEN_CHEST: BlockEntityType<GoldenChestBlockEntity> = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		VanguardSpirits.id("golden_chest"),
		BlockEntityType(::GoldenChestBlockEntity, setOf(ModBlocks.GOLDEN_CHEST)),
	)

	/** Touching the object is what actually runs the registrations above. */
	fun register() = Unit
}
