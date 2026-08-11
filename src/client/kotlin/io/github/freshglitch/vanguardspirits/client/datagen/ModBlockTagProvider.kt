package io.github.freshglitch.vanguardspirits.client.datagen

import io.github.freshglitch.vanguardspirits.registry.ModBlocks
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Block
import java.util.concurrent.CompletableFuture

/**
 * Says which tool mines what.
 *
 * Without this, every `requiresCorrectToolForDrops` block in the mod drops
 * **nothing at all**, to any tool, forever -- and that had been true of the
 * Gilded Reliquary since it was added. Nothing reports it: the loot table is
 * valid, the block breaks normally, and the drop is simply skipped.
 *
 * The gate is in `ServerPlayerGameMode.destroyBlock`, which only calls
 * `Block.playerDestroy` -- the method that rolls the loot table -- when
 * `Player.hasCorrectToolForDrops` says yes. That method is two lines:
 *
 *     !state.requiresCorrectToolForDrops() || heldItem.isCorrectToolForDrops(state)
 *
 * and `isCorrectToolForDrops` resolves through the held item's `TOOL`
 * component, whose rules are all written against **block tags**. A block in no
 * tag matches no rule, so a diamond pickaxe is as wrong as a bare hand.
 *
 * The mural and the altar get no tier tag: vanilla's deepslate bricks need only
 * *a* pickaxe and both are deepslate, so anything stricter would be a rule with
 * nothing to hint at it.
 *
 * **The Reliquary now needs iron**, which was previously argued against here on
 * exactly that discoverability ground. What changed is that the ward holds the
 * chest shut until its Sentinel falls, so nobody meets this rule until they have
 * beaten a ruin -- by which point iron is long behind them. The tier no longer
 * gates access to anything; it says the vault is worth more than the stone
 * around it.
 */
class ModBlockTagProvider(
	output: FabricPackOutput,
	registryLookup: CompletableFuture<HolderLookup.Provider>,
) : FabricTagsProvider<Block>(output, Registries.BLOCK, registryLookup) {

	override fun addTags(registries: HolderLookup.Provider) {
		val pickaxe = builder(BlockTags.MINEABLE_WITH_PICKAXE)

		// Looked up from the registry rather than rebuilt from a path string, so
		// renaming a block cannot quietly leave this pointing at nothing.
		listOf(ModBlocks.MURAL, ModBlocks.GOLDEN_CHEST, ModBlocks.BINDING_ALTAR).forEach { block ->
			pickaxe.add(BuiltInRegistries.BLOCK.getResourceKey(block).orElseThrow())
		}

		builder(BlockTags.NEEDS_IRON_TOOL)
			.add(BuiltInRegistries.BLOCK.getResourceKey(ModBlocks.GOLDEN_CHEST).orElseThrow())
	}
}
