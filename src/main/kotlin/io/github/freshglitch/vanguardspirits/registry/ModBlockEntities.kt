package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.block.entity.BindingAltarBlockEntity
import io.github.freshglitch.vanguardspirits.block.entity.EpitaphBlockEntity
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

	/**
	 * Holds what is lying on an altar between visits, and is what the renderer
	 * reads to draw it. See [BindingAltarBlockEntity].
	 */
	val BINDING_ALTAR: BlockEntityType<BindingAltarBlockEntity> = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		VanguardSpirits.id("binding_altar"),
		BlockEntityType(::BindingAltarBlockEntity, setOf(ModBlocks.BINDING_ALTAR)),
	)

	/**
	 * Holds the name cut into a placed Epitaph, and is what the renderer draws
	 * from. See [EpitaphBlockEntity].
	 */
	val EPITAPH: BlockEntityType<EpitaphBlockEntity> = Registry.register(
		BuiltInRegistries.BLOCK_ENTITY_TYPE,
		VanguardSpirits.id("unfinished_epitaph"),
		BlockEntityType(::EpitaphBlockEntity, setOf(ModBlocks.EPITAPH)),
	)

	/** Touching the object is what actually runs the registrations above. */
	fun register() = Unit
}
