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
	/**
	 * What the charm grants at each depth, shallowest first. Never empty.
	 *
	 * A charm with one entry is one that does not deepen at all, which is the
	 * Returned: it already costs three of a possible four, so a deeper one would
	 * either eat the whole budget or not fit in it. Symmetry there would make the
	 * choice worse, not richer.
	 */
	val depths: List<CharmAura>,
) : Item(props) {

	init {
		require(depths.isNotEmpty()) { "a charm with no depths grants nothing at any binding" }
	}

	/** How deep this charm can be bound. */
	val maxDepth: Int get() = depths.size

	/**
	 * What this charm grants at [depth], one-based.
	 *
	 * Clamped rather than checked. A stack can carry any integer the component
	 * codec accepts -- an old save, a `/give` with a hand-written depth, a charm
	 * whose depths were shortened in a later version -- and the safe reading of a
	 * depth past the end is the deepest binding that actually exists, not a crash
	 * on a tick that runs for every player every tick.
	 */
	fun auraAt(depth: Int): CharmAura = depths[(depth - 1).coerceIn(0, depths.lastIndex)]

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

	/**
	 * The charm's name, with its depth after it once it has been deepened.
	 *
	 * Built on `super` deliberately. This method is not a hook for naming an item
	 * -- it is the *reader* of `DataComponents.ITEM_NAME`, whose vanilla body is
	 * one line -- so returning a fresh translatable here would throw away every
	 * per-stack rename an anvil, a loot table or a data pack had set. Appending to
	 * what super returns keeps all of that and adds the numeral on the end.
	 *
	 * Nothing is added at depth one, which is every charm a player has not taken
	 * to an altar yet: "Charm of the Wanderer I" would imply a scale to somebody
	 * who has not met the mechanic, and the flat name is the assumption.
	 */
	override fun getName(stack: ItemStack): Component {
		val depth = depthOf(stack)
		if (depth <= 1) return super.getName(stack)

		return Component.empty().append(super.getName(stack)).append(" ").append(numeral(depth))
	}

	companion object {
		const val HUSHED_KEY: String = "message.vanguard-spirits.charm.hushed"
		const val WOKEN_KEY: String = "message.vanguard-spirits.charm.woken"

		/**
		 * How deeply this particular charm has been bound, one-based.
		 *
		 * Absent means one. Every charm that ever existed before deepening was
		 * added therefore reads correctly without migrating a single save.
		 */
		fun depthOf(stack: ItemStack): Int =
			stack.getOrDefault(ModComponents.CHARM_DEPTH, 1).coerceAtLeast(1)

		/**
		 * What [stack] actually radiates, or null if it is not a charm at all.
		 *
		 * The one place depth and item are put together. The scan, the ticker and
		 * the tooltip all come through here for the same reason they all come
		 * through [CharmScan]: three readers, one answer.
		 */
		fun auraOf(stack: ItemStack): CharmAura? {
			val charm = stack.item as? CharmItem ?: return null
			return charm.auraAt(depthOf(stack))
		}

		/**
		 * Depth as a carved numeral.
		 *
		 * Deliberately not `MuralLore.numeral`, which indexes the same strings
		 * zero-based because passages are a list and depths are a count. Sharing
		 * one table between a zero-based and a one-based caller is how an
		 * off-by-one gets written down as a fix.
		 */
		fun numeral(depth: Int): String = NUMERALS.getOrElse(depth - 1) { depth.toString() }

		private val NUMERALS = listOf("I", "II", "III", "IV", "V")

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
