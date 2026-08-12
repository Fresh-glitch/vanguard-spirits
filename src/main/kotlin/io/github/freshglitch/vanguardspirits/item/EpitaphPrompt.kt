package io.github.freshglitch.vanguardspirits.item

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/**
 * Server to client: ask whether to set an Epitaph down here.
 *
 * ## Why this has to be a round trip
 *
 * The question the prompt has to answer is *"is there a graveyard here"*, and
 * only the server can answer it -- resolving a position to a structure needs the
 * structure manager, which does not exist on a client. So the client cannot
 * decide on its own whether to warn, and the placement has to pause mid-flight
 * while it asks.
 *
 * [ground] is the entire payload. The **position never leaves the server**,
 * which is deliberate: the client is being asked a yes or no question, not
 * trusted with where the block goes. See [EpitaphPlacement], which remembers the
 * spot itself and re-validates everything when the answer comes back.
 */
@JvmRecord
data class EpitaphPrompt(val ground: Ground) : CustomPacketPayload {

	override fun type(): CustomPacketPayload.Type<EpitaphPrompt> = TYPE

	/**
	 * What the server found under the cursor.
	 *
	 * Three answers rather than a boolean, because "there is a graveyard here"
	 * and "there is a graveyard here **and somebody has already finished it**"
	 * want opposite advice: the first is a warning about what you are about to
	 * end, the second is a warning that you are about to spend a rare stone on
	 * nothing.
	 */
	enum class Ground {
		/** A ruin, still giving up its dead. Placing here ends that. */
		RUIN,

		/** A ruin somebody has already settled. Another stone changes nothing. */
		SETTLED,

		/** No ruin at all. It stands as a headstone and does nothing. */
		ELSEWHERE,
	}

	companion object {
		/**
		 * The `Type` constructor, not `createType` -- that one runs its string
		 * through `Identifier.withDefaultNamespace` and would quietly register
		 * this under `minecraft:`.
		 */
		val TYPE: CustomPacketPayload.Type<EpitaphPrompt> =
			CustomPacketPayload.Type(VanguardSpirits.id("epitaph_prompt"))

		/**
		 * Ordinal on the wire, clamped on the way back in.
		 *
		 * The clamp is not paranoia about this mod's own server -- it is that a
		 * decode which throws takes the whole connection down, and an index out
		 * of a three-entry array is the cheapest way to do that.
		 */
		val CODEC: StreamCodec<RegistryFriendlyByteBuf, EpitaphPrompt> = StreamCodec.composite(
			ByteBufCodecs.VAR_INT.map(
				{ ordinal -> Ground.entries.getOrElse(ordinal) { Ground.ELSEWHERE } },
				Ground::ordinal,
			),
			EpitaphPrompt::ground,
			::EpitaphPrompt,
		)

		/**
		 * The prompt's wording. Declared here, in common, so the language
		 * provider and the screen cannot drift apart -- an untranslated key is a
		 * perfectly valid key and would surface only as raw text on a dialog.
		 */
		const val TITLE_KEY: String = "screen.vanguard-spirits.epitaph.title"

		/** Shown when the stone would land in a ruin, and end it. */
		const val IN_RUIN_KEY: String = "screen.vanguard-spirits.epitaph.in_ruin"

		/** Shown when somebody has already settled this ground. */
		const val SETTLED_KEY: String = "screen.vanguard-spirits.epitaph.settled"

		/** Shown anywhere else, where it is a headstone and nothing more. */
		const val ELSEWHERE_KEY: String = "screen.vanguard-spirits.epitaph.elsewhere"

		/** The line for a given answer, so the screen holds no mapping of its own. */
		fun messageKey(ground: Ground): String = when (ground) {
			Ground.RUIN -> IN_RUIN_KEY
			Ground.SETTLED -> SETTLED_KEY
			Ground.ELSEWHERE -> ELSEWHERE_KEY
		}
	}
}

/**
 * Client to server: yes, set it down.
 *
 * Carries nothing at all. Everything about the placement -- where, which hand,
 * which way round -- is already held server side against this player, so the
 * only thing missing was consent, and consent is one bit. A payload that named
 * the position would be a payload a modified client could aim anywhere.
 */
object EpitaphConfirm : CustomPacketPayload {

	override fun type(): CustomPacketPayload.Type<EpitaphConfirm> = TYPE

	val TYPE: CustomPacketPayload.Type<EpitaphConfirm> =
		CustomPacketPayload.Type(VanguardSpirits.id("epitaph_confirm"))

	val CODEC: StreamCodec<RegistryFriendlyByteBuf, EpitaphConfirm> = StreamCodec.unit(this)
}
