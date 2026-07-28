package io.github.freshglitch.vanguardspirits.charm

import io.github.freshglitch.vanguardspirits.item.CharmItem
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance

/**
 * Keeps every attuned charm's aura topped up.
 *
 * Sweeps online players once a second and re-applies each aura with a duration
 * comfortably longer than the sweep interval -- that overlap is what stops the
 * effect icon flickering between refreshes.
 */
object CharmTicker {
	private const val REFRESH_INTERVAL_TICKS = 20
	private const val AURA_DURATION_TICKS = 60

	fun register() {
		ServerTickEvents.END_SERVER_TICK.register { server ->
			if (server.tickCount % REFRESH_INTERVAL_TICKS == 0) {
				server.playerList.players.forEach(::refresh)
			}
		}
	}

	private fun refresh(player: ServerPlayer) {
		val inventory = player.inventory
		for (slot in CharmScan.activeSlots(player)) {
			val charm = inventory.getItem(slot).item as? CharmItem ?: continue
			player.addEffect(
				MobEffectInstance(
					charm.aura.effect,
					AURA_DURATION_TICKS,
					charm.aura.amplifier,
					/* ambient = */ true,
					/* visible = */ false,
					/* showIcon = */ true,
				),
			)
		}
	}
}
