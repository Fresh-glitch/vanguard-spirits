package io.github.freshglitch.vanguardspirits.item

import io.github.freshglitch.vanguardspirits.charm.CharmAura
import io.github.freshglitch.vanguardspirits.registry.ModComponents
import io.github.freshglitch.vanguardspirits.registry.ModSounds
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * A charm forged from Fractured Memories. Carrying one anywhere in the
 * inventory keeps its [aura] refreshed on the holder.
 *
 * Flavour text is attached as a default LORE component at registration rather
 * than by overriding `appendHoverText`, which is deprecated.
 */
class CharmItem(
	props: Item.Properties,
	val aura: CharmAura,
) : Item(props) {

	/**
	 * Sneak and use to switch the charm off, and again to switch it back on.
	 *
	 * Attunement is scarce and slot order is a clumsy way to spend it: silencing
	 * a charm you are carrying but do not want live meant shuffling it out of
	 * the hotbar and hoping nothing else shifted with it. This says so outright.
	 *
	 * Sneak-gated because an ordinary right click is what happens by accident
	 * while doing something else, and a charm that quietly switched itself off
	 * would read as the aura being broken rather than as a setting.
	 */
	override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
		if (!player.isSecondaryUseActive) return InteractionResult.PASS

		// Let the client predict the swing; the server owns the state.
		if (level.isClientSide) return InteractionResult.SUCCESS

		val held = player.getItemInHand(hand)
		val hushed = !isHushed(held)
		held.set(ModComponents.CHARM_HUSHED, hushed)

		level.playSound(
			null,
			player.x, player.y, player.z,
			if (hushed) ModSounds.CHARM_HUSH else ModSounds.CHARM_WAKE,
			SoundSource.PLAYERS,
			0.7f,
			1.0f,
		)

		player.sendOverlayMessage(
			if (hushed) {
				Component.translatable(HUSHED_KEY).withStyle(ChatFormatting.GRAY)
			} else {
				Component.translatable(WOKEN_KEY).withStyle(ChatFormatting.AQUA)
			},
		)

		return InteractionResult.SUCCESS
	}

	companion object {
		const val HUSHED_KEY: String = "message.vanguard-spirits.charm.hushed"
		const val WOKEN_KEY: String = "message.vanguard-spirits.charm.woken"

		/**
		 * Whether the holder has switched this particular charm off.
		 *
		 * Read through here by the scan, the ticker and the tooltip rather than
		 * each poking at the component, for the same reason the scan itself is
		 * shared: three readers, one answer.
		 */
		fun isHushed(stack: ItemStack): Boolean =
			stack.getOrDefault(ModComponents.CHARM_HUSHED, false)
	}
}
