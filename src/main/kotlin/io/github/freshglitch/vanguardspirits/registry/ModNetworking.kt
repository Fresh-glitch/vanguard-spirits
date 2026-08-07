package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.lore.MuralOpen
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry

/**
 * Payload types the mod sends.
 *
 * Registration is common, not client: it is the *codec* being registered, and
 * both ends need it -- the server to encode, the client to decode. Only the
 * receiver is client-side, and that lives in `VanguardSpiritsClient`.
 */
object ModNetworking {

	fun register() {
		PayloadTypeRegistry.clientboundPlay().register(MuralOpen.TYPE, MuralOpen.CODEC)
	}
}
