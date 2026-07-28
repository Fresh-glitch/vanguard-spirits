package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.block.GoldenChestBlock
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.MapColor

object ModBlocks {
	private val ordered = mutableListOf<Block>()

	val GOLDEN_CHEST: Block = register("golden_chest", ::GoldenChestBlock) {
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.GOLD)
			.strength(3.0f, 6.0f)
			.requiresCorrectToolForDrops()
			.sound(SoundType.METAL)
			// The lid and gilding do not fill the block, so neighbours must keep
			// rendering their touching faces.
			.noOcclusion()
	}

	/** Every block this mod registers, in display order. */
	val ALL: List<Block> get() = ordered

	/** Touching the object is what actually runs the registrations above. */
	fun register() = Unit

	private fun <T : Block> register(
		path: String,
		factory: (BlockBehaviour.Properties) -> T,
		properties: () -> BlockBehaviour.Properties,
	): T {
		val key = ResourceKey.create(Registries.BLOCK, VanguardSpirits.id(path))
		@Suppress("UNCHECKED_CAST")
		val block = Blocks.register(key, factory as (BlockBehaviour.Properties) -> Block, properties()) as T
		ordered += block
		registerItem(path, block)
		return block
	}

	/** Blocks need a matching BlockItem before they can be held or given. */
	private fun registerItem(path: String, block: Block) {
		val key = ResourceKey.create(Registries.ITEM, VanguardSpirits.id(path))
		val item = BlockItem(
			block,
			Item.Properties()
				.setId(key)
				.rarity(Rarity.RARE)
				.useBlockDescriptionPrefix(),
		)
		Registry.register(BuiltInRegistries.ITEM, key, item)
	}
}
