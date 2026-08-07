package io.github.freshglitch.vanguardspirits.lore

import com.mojang.serialization.Codec
import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.registry.ModTriggers
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player

/**
 * Which passages a player has read, as a bitmask.
 *
 * Persistent and carried through death, for the same reason [Attunement][
 * io.github.freshglitch.vanguardspirits.charm.Attunement] is: dying on the way
 * back up from the deeps should cost you the trip, not the reading.
 *
 * Deliberately **not** synced. The screen is the only thing that wants the
 * bitmask and it is handed a snapshot in [MuralOpen] at the moment it opens, so
 * an attachment sync would be a second copy of the same fact travelling by a
 * different route -- and the two could disagree. There is no way to read a new
 * passage while the screen is already up, so the snapshot cannot go stale.
 */
object MuralCodex {

	val TYPE: AttachmentType<Int> = AttachmentRegistry.create(VanguardSpirits.id("murals_read")) { builder ->
		builder.initializer { 0 }
			.persistent(Codec.INT)
			.copyOnDeath()
	}

	/** The raw bitmask. Bit *n* set means passage *n* has been read. */
	fun of(player: Player): Int = player.getAttachedOrCreate(TYPE)

	fun hasRead(player: Player, passage: Int): Boolean =
		MuralLore.exists(passage) && (of(player) and (1 shl passage)) != 0

	/** How many distinct passages this player has found. */
	fun count(player: Player): Int = Integer.bitCount(of(player) and MuralLore.ALL_READ)

	/**
	 * Records [passage] as read, and returns whether that was new.
	 *
	 * The advancement fires from here rather than from [MuralBlock][
	 * io.github.freshglitch.vanguardspirits.block.MuralBlock] so that any future
	 * route to a passage -- a book, a rendered copy, a reward -- is credited
	 * without anyone having to remember to come back and add it. The same reason
	 * `Attunement.raise` owns its trigger rather than the Echo of Kinship.
	 */
	fun markRead(player: Player, passage: Int): Boolean {
		if (!MuralLore.exists(passage)) return false

		val before = of(player)
		val after = before or (1 shl passage)
		if (after == before) return false

		player.setAttached(TYPE, after)
		if (player is ServerPlayer) {
			ModTriggers.MURALS.fire(player, Integer.bitCount(after and MuralLore.ALL_READ))
		}
		return true
	}

	/** Touching the object is what registers the attachment type. */
	fun register() = Unit
}
