package io.github.freshglitch.vanguardspirits.charm

import com.mojang.serialization.Codec
import io.github.freshglitch.vanguardspirits.VanguardSpirits
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.world.entity.player.Player

/**
 * How many charms a player can keep attuned at once.
 *
 * Persisted across sessions, carried through death, and synced to the owning
 * client only -- the client needs it to tell the player which carried charms are
 * live, but nobody else's attunement is their business.
 */
object Attunement {
	/** What every player starts with. */
	const val BASE: Int = 1

	/** Ceiling, so Echoes of Kinship stop being useful at some point. */
	const val MAX: Int = 4

	val TYPE: AttachmentType<Int> = AttachmentRegistry.create(VanguardSpirits.id("attunement")) { builder ->
		builder.initializer { BASE }
			.persistent(Codec.INT)
			.copyOnDeath()
			.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
	}

	fun of(player: Player): Int = player.getAttachedOrCreate(TYPE)

	/** Raises the cap by one. Returns false if already at [MAX]. */
	fun raise(player: Player): Boolean {
		val current = of(player)
		if (current >= MAX) return false
		player.setAttached(TYPE, current + 1)
		return true
	}

	/** Touching the object is what registers the attachment type. */
	fun register() = Unit
}
