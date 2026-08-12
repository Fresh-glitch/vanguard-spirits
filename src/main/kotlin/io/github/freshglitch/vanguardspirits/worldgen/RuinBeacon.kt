package io.github.freshglitch.vanguardspirits.worldgen

import io.github.freshglitch.vanguardspirits.entity.Mourner
import io.github.freshglitch.vanguardspirits.registry.ModEntities
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.AABB

/**
 * Puts Mourners in the sky over every Guarded Ruin a player comes near.
 *
 * Not worldgen. Birds spawned when the chunk was first written would have
 * wandered off or despawned long before anyone arrived, and a landmark that is
 * only there if you were present at the creation of the world is no landmark.
 * This tops the flock up instead, so the sign is there whenever someone is
 * close enough to read it.
 */
object RuinBeacon {

	fun register() {
		ServerTickEvents.END_LEVEL_TICK.register(::tick)
	}

	private fun tick(level: ServerLevel) {
		if (level.gameTime % SCAN_INTERVAL != 0L) return
		if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return

		for (player in level.players()) {
			if (player.isSpectator) continue
			survey(level, player)
		}
	}

	/**
	 * Finds ruins near a player and makes sure each has its birds.
	 *
	 * Searches by chunk rather than by structure bounding box: the box is thirty
	 * blocks underground and a player on the surface is never inside it, but the
	 * chunks it touches are the same chunks they are standing on.
	 */
	private fun survey(level: ServerLevel, player: ServerPlayer) {
		val here = player.chunkPosition()
		val seen = HashSet<Long>()

		for (dx in -SCAN_CHUNKS..SCAN_CHUNKS) {
			for (dz in -SCAN_CHUNKS..SCAN_CHUNKS) {
				val chunk = ChunkPos(here.x + dx, here.z + dz)
				if (!level.hasChunk(chunk.x, chunk.z)) continue

				val starts = level.structureManager()
					.startsForStructure(chunk) { it is GuardedRuinsStructure }
				if (starts.isEmpty()) continue

				for (start in starts) {
					// One ruin spans several chunks, so the same start comes back
					// from each of them. Key on where it began.
					if (!seen.add(start.chunkPos.pack())) continue
					attend(level, start.boundingBox.center)
				}
			}
		}
	}

	/** Tops the flock up over one ruin. */
	private fun attend(level: ServerLevel, ruinCentre: BlockPos) {
		val surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ruinCentre.x, ruinCentre.z)
		val anchor = BlockPos(ruinCentre.x, surface + FLIGHT_HEIGHT, ruinCentre.z)

		// Once someone has shot them down over this ruin, they stay down.
		if (RuinVigil.isBroken(level, anchor)) return

		val watch = AABB(
			anchor.x - WATCH_RADIUS, anchor.y - WATCH_RADIUS, anchor.z - WATCH_RADIUS,
			anchor.x + WATCH_RADIUS, anchor.y + WATCH_RADIUS, anchor.z + WATCH_RADIUS,
		)
		val already = level.getEntitiesOfClass(Mourner::class.java, watch) { it.anchoredNear(anchor) }
		if (already.size >= FLOCK) return

		for (i in already.size until FLOCK) {
			val bird = ModEntities.MOURNER.create(level, EntitySpawnReason.STRUCTURE) ?: return

			// Spaced around the circle so they arrive as a flock rather than
			// stacked on the same point.
			val angle = Mth.TWO_PI * i / FLOCK
			bird.setAnchor(anchor, angle)
			bird.snapTo(
				anchor.x + 0.5 + Mth.cos(angle.toDouble()) * Mourner.ORBIT_RADIUS,
				anchor.y.toDouble(),
				anchor.z + 0.5 + Mth.sin(angle.toDouble()) * Mourner.ORBIT_RADIUS,
				0.0f,
				0.0f,
			)
			level.addFreshEntity(bird)
		}
	}

	/** Five seconds. Nothing here is urgent and the chunk scan is not free. */
	private const val SCAN_INTERVAL = 100L

	/** Chunks either side of the player to search. Roughly a hundred blocks. */
	private const val SCAN_CHUNKS = 6

	/** Height above the treeline. High enough to see from a distance. */
	private const val FLIGHT_HEIGHT = 16

	private const val FLOCK = 3

	/**
	 * How far from the anchor to count birds that already belong to this ruin.
	 *
	 * Wider than the circle needs, because a Mourner shown a Fractured Memory
	 * leaves it -- a pointing run carries one about fifty six blocks out. At the
	 * old thirty two this box lost sight of it, decided the flock was short, and
	 * spawned a replacement that stayed once the guide came home. Feeding birds
	 * would have grown the flock a little at a time, which is the sort of thing
	 * nobody notices until a ruin has nine of them.
	 *
	 * Safe to widen: the filter also asks [Mourner.anchoredNear], so a
	 * neighbouring ruin's birds are excluded by their own anchor no matter how
	 * far this box reaches.
	 */
	private const val WATCH_RADIUS = 96.0
}
