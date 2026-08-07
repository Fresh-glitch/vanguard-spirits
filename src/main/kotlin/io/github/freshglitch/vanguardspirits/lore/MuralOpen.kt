package io.github.freshglitch.vanguardspirits.lore

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Server to client: open the mural screen on [passage], with [read] as the set
 * of passages this player has already found.
 *
 * A payload rather than a menu. A mural is a read, not a container: there is
 * nothing to hold, nothing to take, and no reason for the server to keep a
 * lifecycle open while somebody stands reading. `AbstractContainerMenu` would
 * have bought only its own ceremony -- a slotless menu, a `stillValid` that
 * always says yes, and a data slot smuggling the passage number across.
 *
 * [read] rides along here rather than arriving through a synced attachment so
 * that the screen is handed one coherent snapshot. It cannot go stale, because
 * reading another passage means closing this screen first.
 */
@JvmRecord
data class MuralOpen(val passage: Int, val read: Int) : CustomPacketPayload {

	override fun type(): CustomPacketPayload.Type<MuralOpen> = TYPE

	companion object {
		/**
		 * Built from the `Type` constructor, **not** `createType`.
		 *
		 * `CustomPacketPayload.createType` runs its string through
		 * `Identifier.withDefaultNamespace`, so the convenient-looking
		 * `createType("mural_open")` would have quietly claimed
		 * `minecraft:mural_open` for this mod.
		 */
		val TYPE: CustomPacketPayload.Type<MuralOpen> =
			CustomPacketPayload.Type(VanguardSpirits.id("mural_open"))

		val CODEC: StreamCodec<RegistryFriendlyByteBuf, MuralOpen> = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, MuralOpen::passage,
			ByteBufCodecs.VAR_INT, MuralOpen::read,
			::MuralOpen,
		)
	}
}
