package io.github.freshglitch.vanguardspirits.worldgen

import com.mojang.serialization.Codec
import io.github.freshglitch.vanguardspirits.VanguardSpirits
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel

/**
 * Remembers which ruins have had somebody put their name in the ledger.
 *
 * ## What settling is
 *
 * [RuinHollow] is the wound: emptying a Reliquary takes the fragments holding
 * these people together, and the fifth passage's promise comes due -- the
 * graveyard starts giving them back, permanently, and the ruin becomes a place
 * you farm.
 *
 * This is the answer to it. An Unfinished Epitaph engraved with a player's name
 * finishes the job the last keeper could not, and the ground goes quiet: the
 * graves stop rising and open into plain turned earth.
 *
 * It is deliberately the *end* of a ruin rather than a repair of one. Nothing
 * comes back — the memories the player took are still gone, the Sentinel is
 * still dead. What changes is that the place stops producing consequences, which
 * is worth exactly one thing: somewhere to live.
 *
 * ## Why it does not undo
 *
 * The same reason [RuinVigil] and [RuinHollow] do not. A consequence a player can
 * wait out is not a consequence, and a decision they can take back is not a
 * decision. Settling costs a ruin's renewable supply of memories and it should
 * keep costing it.
 *
 * Kept on the ruin's structure-start chunk through [Ruins], like the other two,
 * so an Epitaph in the graveyard and a grave thirty blocks along it agree about
 * which ruin they belong to.
 */
object RuinSettled {

	private val SETTLED: AttachmentType<Boolean> =
		AttachmentRegistry.create(VanguardSpirits.id("ruin_settled")) { builder ->
			builder.initializer { false }.persistent(Codec.BOOL)
		}

	/**
	 * Whether the ruin containing [pos] has been given a name.
	 *
	 * A position in no ruin answers no, which is the useful default: a Grave a
	 * player laid in their own garden was never part of anything that could be
	 * settled, and should keep behaving like the decoration it is.
	 */
	fun isSettled(level: ServerLevel, pos: BlockPos): Boolean {
		val ruin = Ruins.startChunkAt(level, pos) ?: return false
		return level.getChunk(ruin.x, ruin.z).getAttachedOrElse(SETTLED, false)
	}

	/**
	 * Records that the ruin containing [pos] has been settled.
	 *
	 * Returns whether this was the moment it changed, so the caller announces it
	 * once rather than every time somebody re-reads the stone. Returns false for
	 * a position in no ruin at all, which is how an Epitaph planted in a meadow
	 * quietly becomes a headstone and nothing more.
	 */
	fun settle(level: ServerLevel, pos: BlockPos): Boolean {
		val ruin = Ruins.startChunkAt(level, pos) ?: return false

		val chunk = level.getChunk(ruin.x, ruin.z)
		if (chunk.getAttachedOrElse(SETTLED, false)) return false

		chunk.setAttached(SETTLED, true)
		return true
	}

	/** Touching the object is what registers the attachment type. */
	fun register() = Unit
}
