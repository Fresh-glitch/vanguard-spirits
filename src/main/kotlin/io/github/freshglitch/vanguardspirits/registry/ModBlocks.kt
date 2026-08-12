package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.block.BindingAltarBlock
import io.github.freshglitch.vanguardspirits.block.EpitaphBlock
import io.github.freshglitch.vanguardspirits.block.GoldenChestBlock
import io.github.freshglitch.vanguardspirits.item.EpitaphItem
import io.github.freshglitch.vanguardspirits.block.GraveBlock
import io.github.freshglitch.vanguardspirits.block.MuralBlock
import io.github.freshglitch.vanguardspirits.block.entity.MuralBlockEntity
import net.minecraft.ChatFormatting
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.component.ItemLore
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
			// A hollowed graveyard gives its dead back on its own, after dark.
			// Random ticks are the clock for it -- a block entity on twenty-eight
			// mounds per plot would be twenty-eight tickers to run a check that
			// wants to happen a few times an hour.
			.randomTicks()
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

	/**
	 * Where a charm is bound one depth deeper.
	 *
	 * Deepslate's numbers again, because it is the ruin's own masonry -- and
	 * mineable, so a player can carry one home rather than trekking back to a
	 * sanctum every time they can afford a binding.
	 */
	val BINDING_ALTAR: Block = register("binding_altar", ::BindingAltarBlock) {
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.DEEPSLATE)
			.strength(3.5f, 6.0f)
			.requiresCorrectToolForDrops()
			.sound(SoundType.DEEPSLATE_BRICKS)
			// Waist-high rather than a full cube, so faces touching it must keep
			// rendering.
			.noOcclusion()
	}

	/**
	 * The last keeper's own marker, never finished.
	 *
	 * Deepslate again, and deliberately weak: this is a thing you set down and
	 * commit to, not a thing you defend. It never drops itself once placed --
	 * see its empty loot table -- so breaking one is only ever throwing it away.
	 */
	val EPITAPH: Block = register(
		"unfinished_epitaph",
		::EpitaphBlock,
		// Flavour only. The warning used to live here as a second lore line, and
		// a tooltip is the wrong instrument for it -- it is read once, by players
		// who were already being careful. The prompt in
		// [EpitaphItem][io.github.freshglitch.vanguardspirits.item.EpitaphItem]
		// stops the click instead, which is the one that catches the accident.
		itemProps = { props ->
			props.stacksTo(1).component(
				DataComponents.LORE,
				ItemLore(listOf(Component.translatable(ModItems.loreKey("unfinished_epitaph")))),
			)
		},
		itemFactory = ::EpitaphItem,
	) {
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.DEEPSLATE)
			.strength(1.5f, 6.0f)
			.sound(SoundType.DEEPSLATE_BRICKS)
			// A marker, not a wall: it is three thin cuboids and everything
			// around it has to keep drawing its own faces.
			.noOcclusion()
	}

	/** Every block this mod registers, in display order. */
	val ALL: List<Block> get() = ordered

	/** Touching the object is what actually runs the registrations above. */
	fun register() = Unit

	private fun <T : Block> register(
		path: String,
		factory: (BlockBehaviour.Properties) -> T,
		rarity: Rarity = Rarity.RARE,
		itemProps: (Item.Properties) -> Item.Properties = { it },
		itemFactory: (Block, Item.Properties) -> BlockItem = ::BlockItem,
		properties: () -> BlockBehaviour.Properties,
	): T {
		val key = ResourceKey.create(Registries.BLOCK, VanguardSpirits.id(path))
		@Suppress("UNCHECKED_CAST")
		val block = Blocks.register(key, factory as (BlockBehaviour.Properties) -> Block, properties()) as T
		ordered += block
		registerItem(path, block, rarity, itemProps, itemFactory)
		return block
	}

	/**
	 * Blocks need a matching BlockItem before they can be held or given.
	 *
	 * [itemProps] is the hook for anything the shared shape cannot express --
	 * lore, a stack limit. It runs last so a caller can override what is set
	 * here rather than only add to it.
	 */
	private fun registerItem(
		path: String,
		block: Block,
		rarity: Rarity,
		itemProps: (Item.Properties) -> Item.Properties,
		itemFactory: (Block, Item.Properties) -> BlockItem,
	) {
		val key = ResourceKey.create(Registries.ITEM, VanguardSpirits.id(path))
		val item = itemFactory(
			block,
			itemProps(
				Item.Properties()
					.setId(key)
					.rarity(rarity)
					.useBlockDescriptionPrefix(),
			),
		)
		Registry.register(BuiltInRegistries.ITEM, key, item)
	}
}
