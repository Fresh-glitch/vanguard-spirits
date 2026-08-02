package io.github.freshglitch.vanguardspirits.charm

import io.github.freshglitch.vanguardspirits.item.CharmItem
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectInstance

/**
 * Holds every attuned charm's aura open, and shuts it the moment the charm goes.
 *
 * The first version re-applied a sixty tick effect once a second and relied on
 * the overlap to hide the seam. That works for most effects and is wrong for
 * night vision, which vanilla deliberately makes flicker under ten seconds
 * remaining as a warning that it is about to lapse -- so a charm meant to be
 * permanent strobed continuously in the dark.
 *
 * The aura is granted with [MobEffectInstance.INFINITE_DURATION] instead, which
 * turns this sweep from something that tops auras up into something that takes
 * them back. That is also why it now runs every tick rather than once a second:
 * putting a charm down should stop the effect at once, not up to a second later.
 */
object CharmTicker {

	/**
	 * Every effect any charm can grant.
	 *
	 * Read off the item registry rather than a hand-kept list, so a charm added
	 * later is withdrawn properly without anyone remembering to come here. Lazy
	 * because the registry is not populated when this object is first touched.
	 */
	private val grantable: List<Holder<MobEffect>> by lazy {
		BuiltInRegistries.ITEM.filterIsInstance<CharmItem>().map { it.aura.effect }.distinct()
	}

	fun register() {
		ServerTickEvents.END_SERVER_TICK.register { server ->
			server.playerList.players.forEach(::refresh)
		}
	}

	private fun refresh(player: ServerPlayer) {
		val inventory = player.inventory

		// What the attuned charms are asking for this tick. Two granting the
		// same effect resolve to the stronger rather than to whichever happened
		// to sit in the lower slot.
		val wanted = HashMap<Holder<MobEffect>, Int>()
		for (slot in CharmScan.activeSlots(player)) {
			val charm = inventory.getItem(slot).item as? CharmItem ?: continue
			wanted.merge(charm.aura.effect, charm.aura.amplifier, ::maxOf)
		}

		for ((effect, amplifier) in wanted) {
			// Only touched when it is actually missing or wrong. Re-applying an
			// identical instance every tick would restart the fade the client
			// runs when an effect arrives.
			if (alreadyHeld(player, effect, amplifier)) continue

			player.addEffect(
				MobEffectInstance(
					effect,
					MobEffectInstance.INFINITE_DURATION,
					amplifier,
					/* ambient = */ true,
					/* visible = */ false,
					/* showIcon = */ true,
				),
			)
		}

		for (effect in grantable) {
			if (effect in wanted) continue

			// Only ever take back something that looks like ours. Infinite and
			// ambient together is a signature nothing a player can drink or
			// stand in a beacon for produces: potions are finite, and a beacon
			// is ambient but keeps refreshing a finite duration.
			val held = player.getEffect(effect) ?: continue
			if (held.isInfiniteDuration && held.isAmbient) player.removeEffect(effect)
		}
	}

	private fun alreadyHeld(player: ServerPlayer, effect: Holder<MobEffect>, amplifier: Int): Boolean {
		val held = player.getEffect(effect) ?: return false
		return held.isInfiniteDuration && held.isAmbient && held.amplifier == amplifier
	}
}
