package io.github.freshglitch.vanguardspirits.item

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.BlockHitResult

/**
 * An Epitaph in the hand. Refuses to place itself until it has been asked twice.
 *
 * Setting one down cannot be undone -- the stone does not come back, and if it
 * lands in a ruin it ends that graveyard for good. That is a lot to hang on a
 * right-click made while walking, so this puts the question through
 * [EpitaphPlacement] and places nothing until the answer arrives.
 *
 * ## Why the client is stopped as well as the server
 *
 * `useOn` runs on both sides -- the client calls it to *predict* the result so a
 * block appears the instant you click. If only the server were held back, the
 * client would place a marker, get corrected a tick later, and the stone would
 * flash into existence and vanish while the prompt was still on screen. That is
 * the same flicker the Gilded Reliquary's ward had before it moved into the
 * blockstate, and it reads as a bug rather than a question.
 *
 * So the client returns early without placing anything, and the block only ever
 * appears once, when the server puts it there.
 */
class EpitaphItem(block: Block, properties: Properties) : BlockItem(block, properties) {

	override fun useOn(context: UseOnContext): InteractionResult {
		val player = context.player ?: return InteractionResult.FAIL

		// No prediction. See above.
		if (context.level.isClientSide) return InteractionResult.CONSUME

		if (player !is ServerPlayer) return InteractionResult.FAIL

		val hit = BlockHitResult(
			context.clickLocation,
			context.clickedFace,
			context.clickedPos,
			context.isInside,
		)
		return EpitaphPlacement.ask(player, hit, context.hand)
	}
}
