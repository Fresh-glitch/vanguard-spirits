package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.block.GoldenChestBlock
import io.github.freshglitch.vanguardspirits.block.GraveBlock
import io.github.freshglitch.vanguardspirits.block.MuralBlock
import io.github.freshglitch.vanguardspirits.block.entity.MuralBlockEntity
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

	/**
	 * A grave mound. Soft on purpose.
	 *
	 * Dirt's own hardness, no tool needed: springing this should be something a
	 * player does by accident in the second before they think better of it, not
	 * a decision they have time to reconsider halfway through.
	 */
	val GRAVE: Block = register("grave", ::GraveBlock, rarity = Rarity.COMMON) {
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.PODZOL)
			.strength(0.6f)
			.sound(SoundType.ROOTED_DIRT)
	}

	/**
	 * A carved passage from the ruin's own account of itself.
	 *
	 * Deepslate's own numbers, because that is what it is -- a wall block that
	 * happens to be worth stopping at. Nothing here hints at the reading; a
	 * player finds that by clicking it, which is the right order.
	 */
	val MURAL: Block = register("mural", ::MuralBlock) {
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.DEEPSLATE)
			.strength(3.5f, 6.0f)
			.requiresCorrectToolForDrops()
			.sound(SoundType.DEEPSLATE_BRICKS)
			// Dark until somebody comes near. The block entity walks GLOW up and
			// down from player distance; this is the only place that value can be
			// read from, because the light engine is handed a BlockState and
			// nothing else.
			.lightLevel { state ->
				state.getValue(MuralBlock.GLOW) * MuralBlockEntity.LIGHT_PER_STEP
			}
	}

	/** Every block this mod registers, in display order. */
	val ALL: List<Block> get() = ordered

	/** Touching the object is what actually runs the registrations above. */
	fun register() = Unit

	private fun <T : Block> register(
		path: String,
		factory: (BlockBehaviour.Properties) -> T,
		rarity: Rarity = Rarity.RARE,
		properties: () -> BlockBehaviour.Properties,
	): T {
		val key = ResourceKey.create(Registries.BLOCK, VanguardSpirits.id(path))
		@Suppress("UNCHECKED_CAST")
		val block = Blocks.register(key, factory as (BlockBehaviour.Properties) -> Block, properties()) as T
		ordered += block
		registerItem(path, block, rarity)
		return block
	}

	/** Blocks need a matching BlockItem before they can be held or given. */
	private fun registerItem(path: String, block: Block, rarity: Rarity) {
		val key = ResourceKey.create(Registries.ITEM, VanguardSpirits.id(path))
		val item = BlockItem(
			block,
			Item.Properties()
				.setId(key)
				.rarity(rarity)
				.useBlockDescriptionPrefix(),
		)
		Registry.register(BuiltInRegistries.ITEM, key, item)
	}
}
