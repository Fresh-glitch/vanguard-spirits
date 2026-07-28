package io.github.freshglitch.vanguardspirits.item

import io.github.freshglitch.vanguardspirits.charm.Attunement
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level

/**
 * Consumed to permanently raise the holder's attunement by one, up to
 * [Attunement.MAX]. Intended as deep Guarded Ruins loot rather than a craft.
 */
class EchoOfKinshipItem(props: Item.Properties) : Item(props) {

	override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
		// Let the client predict the swing; the server owns the actual change.
		if (level.isClientSide) return InteractionResult.SUCCESS

		if (!Attunement.raise(player)) {
			player.sendOverlayMessage(
				Component.translatable(MAXED_KEY).withStyle(ChatFormatting.GRAY),
			)
			return InteractionResult.FAIL
		}

		player.getItemInHand(hand).consume(1, player)
		level.playSound(
			null,
			player.x,
			player.y,
			player.z,
			SoundEvents.BEACON_ACTIVATE,
			SoundSource.PLAYERS,
			0.7f,
			1.4f,
		)
		player.sendOverlayMessage(
			Component.translatable(RAISED_KEY, Attunement.of(player), Attunement.MAX)
				.withStyle(ChatFormatting.AQUA),
		)
		return InteractionResult.SUCCESS
	}

	companion object {
		const val RAISED_KEY: String = "message.vanguard-spirits.attunement.raised"
		const val MAXED_KEY: String = "message.vanguard-spirits.attunement.maxed"
	}
}
