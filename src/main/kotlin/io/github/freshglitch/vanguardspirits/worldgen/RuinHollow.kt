package io.github.freshglitch.vanguardspirits.worldgen

import com.mojang.serialization.Codec
import io.github.freshglitch.vanguardspirits.VanguardSpirits
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel

/**
 * Remembers which ruins have had their Reliquary emptied.
 *
 * ## Why this exists
 *
 * The fifth passage says what taking the fragments does:
 *
 * > Take one fragment and the rest of a person settles. Take enough and there is
 * > nothing left to settle. They still rise.
 *
 * That shipped as fiction and charged the player nothing. A ruin stripped of its
 * memories was simply finished -- the Sentinel dead, the chest empty, the whole
 * place dead ground the player would never come back to. So the loop's repeated
 * action was travelling to the next ruin rather than playing at any of them.
 *
 * Emptying a Reliquary now hollows the people buried above it. The graveyard
 * starts giving them back after dark, permanently, and what rises is carrying
 * what is left of a memory. The ruin turns from a place you finish into a place
 * you made -- and it is the renewable supply that pays for
 * [io.github.freshglitch.vanguardspirits.charm.Binding], which would otherwise
 * be a sink with nothing feeding it.
 *
 * ## Where it is kept
 *
 * On the ruin's structure-start chunk, resolved through [Ruins] -- the same key
 * [RuinSeal] uses, and for the same reason. The Reliquary is in the vault and
 * the graves are thirty blocks above it on the surface, which in a ruin this
 * size are different chunks; only resolving through the structure gets the
 * question and the answer to the same place.
 *
 * Like a broken vigil, this does not undo. A graveyard that settled back down
 * once the player left would be a consequence they could wait out.
 */
object RuinHollow {

	private val HOLLOWED: AttachmentType<Boolean> =
		AttachmentRegistry.create(VanguardSpirits.id("ruin_hollowed")) { builder ->
			builder.initializer { false }.persistent(Codec.BOOL)
		}

	/**
	 * Whether the ruin containing [pos] has been emptied.
	 *
	 * A position in no ruin at all answers no: a Grave a player placed in their
	 * own garden is not part of anything that can be hollowed, and should stay
	 * the quiet decoration it is.
	 */
	fun isHollowed(level: ServerLevel, pos: BlockPos): Boolean {
		val ruin = Ruins.startChunkAt(level, pos) ?: return false
		return level.getChunk(ruin.x, ruin.z).getAttachedOrElse(HOLLOWED, false)
	}

	/**
	 * Records that the ruin containing [pos] has given up its memories.
	 *
	 * Returns whether this was the moment it changed, so the caller can announce
	 * it once rather than every time somebody closes an empty chest.
	 */
	fun hollow(level: ServerLevel, pos: BlockPos): Boolean {
		val ruin = Ruins.startChunkAt(level, pos) ?: return false

		val chunk = level.getChunk(ruin.x, ruin.z)
		if (chunk.getAttachedOrElse(HOLLOWED, false)) return false

		chunk.setAttached(HOLLOWED, true)
		return true
	}

	/** Touching the object is what registers the attachment type. */
	fun register() = Unit
}
