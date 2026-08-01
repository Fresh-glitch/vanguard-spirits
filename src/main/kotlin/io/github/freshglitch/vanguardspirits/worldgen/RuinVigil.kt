package io.github.freshglitch.vanguardspirits.worldgen

import com.mojang.serialization.Codec
import io.github.freshglitch.vanguardspirits.VanguardSpirits
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

/**
 * Remembers which ruins have had their Mourners killed.
 *
 * Stored on the chunk the birds circle over rather than in any registry of our
 * own: chunk attachments save and load with the chunk, so the memory is scoped
 * to exactly the place it describes and survives without a world-level index to
 * maintain.
 *
 * The flock does not come back. A landmark that respawns the moment you clear
 * it is not something a player can act on, and killing the birds over a ruin
 * you have already emptied should stay done.
 */
object RuinVigil {

	private val BROKEN: AttachmentType<Boolean> =
		AttachmentRegistry.create(VanguardSpirits.id("vigil_broken")) { builder ->
			builder.initializer { false }.persistent(Codec.BOOL)
		}

	fun isBroken(level: Level, anchor: BlockPos): Boolean =
		level.getChunk(anchor).getAttachedOrElse(BROKEN, false)

	fun breakVigil(level: Level, anchor: BlockPos) {
		level.getChunk(anchor).setAttached(BROKEN, true)
	}

	/** Touching the object is what registers the attachment type. */
	fun register() = Unit
}
