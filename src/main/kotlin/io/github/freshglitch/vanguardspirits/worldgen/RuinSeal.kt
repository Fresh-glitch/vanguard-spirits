package io.github.freshglitch.vanguardspirits.worldgen

import com.mojang.serialization.Codec
import io.github.freshglitch.vanguardspirits.VanguardSpirits
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.levelgen.structure.Structure

/**
 * Remembers which ruins have had their Sentinel put down.
 *
 * A player who knows the layout can tunnel straight to the vault and take the
 * Reliquary without ever meeting the thing on the altar, which makes the whole
 * encounter optional and the loot free. The Reliquary asks this before it opens.
 *
 * Kept on the chunk holding the *structure start*, not on the altar's chunk or
 * the vault's. Those are different chunks in a ruin this size, and the two
 * halves of the question -- a Sentinel dying somewhere, a chest being opened
 * somewhere else -- have to arrive at the same key or the seal never lifts.
 * Resolving through the structure is what guarantees that.
 */
object RuinSeal {

	private val FELLED: AttachmentType<Boolean> =
		AttachmentRegistry.create(VanguardSpirits.id("sentinel_felled")) { builder ->
			builder.initializer { false }.persistent(Codec.BOOL)
		}

	/**
	 * Whether the guardian of the ruin containing [pos] is dead.
	 *
	 * A position in no ruin at all answers yes: a Reliquary someone placed in
	 * their own base has nothing guarding it and no reason to refuse them.
	 */
	fun isCleared(level: ServerLevel, pos: BlockPos): Boolean {
		val ruin = ruinAt(level, pos) ?: return true
		return level.getChunk(ruin.x, ruin.z).getAttachedOrElse(FELLED, false)
	}

	/** Records that the Sentinel watching over [pos] has been defeated. */
	fun markFelled(level: ServerLevel, pos: BlockPos) {
		val ruin = ruinAt(level, pos) ?: return
		level.getChunk(ruin.x, ruin.z).setAttached(FELLED, true)
	}

	/**
	 * The start chunk of the Guarded Ruin containing a position, if any.
	 *
	 * Shared with [RuinHollow] through [Ruins] rather than kept here. Both key
	 * their state on this chunk, and two copies that drifted would put the seal
	 * and the hollowing on different ruins.
	 */
	private fun ruinAt(level: ServerLevel, pos: BlockPos): ChunkPos? =
		Ruins.startChunkAt(level, pos)

	/** Touching the object is what registers the attachment type. */
	fun register() = Unit
}
