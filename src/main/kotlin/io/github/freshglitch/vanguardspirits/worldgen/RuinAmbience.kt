package io.github.freshglitch.vanguardspirits.worldgen

import io.github.freshglitch.vanguardspirits.registry.ModParticles
import io.github.freshglitch.vanguardspirits.registry.ModStructures
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.util.RandomSource
import net.minecraft.world.level.levelgen.structure.BoundingBox
import net.minecraft.world.phys.Vec3

/**
 * Seeds the air inside a Guarded Ruin with the mod's own particles.
 *
 * Server side by necessity: only the server knows where the structures are, so
 * the client cannot decide this for itself. Positions are chosen here and the
 * particles pushed out to whoever is close enough to see them.
 *
 * Sparse on purpose. The ruins already have soul campfires throwing a constant
 * column of flame -- ambience that competed with those would just be noise, so
 * a mote arrives every few seconds and a rune once in a while.
 */
object RuinAmbience {

	fun register() {
		ServerTickEvents.END_LEVEL_TICK.register(::tick)
	}

	private fun tick(level: ServerLevel) {
		if (level.gameTime % SCAN_INTERVAL != 0L) return

		for (player in level.players()) {
			val start = level.structureManager()
				.getStructureWithPieceAt(player.blockPosition()) { it.`is`(ModStructures.GUARDED_RUINS_KEY) }

			// INVALID_START rather than null when the player is not in a ruin.
			if (start.isValid) emit(level, player, start.boundingBox)
		}
	}

	/**
	 * One scan's worth of particles for one player.
	 *
	 * The bounding box is the piece's, which spans the whole hollowed cavern, so
	 * clamping to it keeps every particle inside the chamber instead of bleeding
	 * out into the surrounding deepslate.
	 */
	private fun emit(level: ServerLevel, player: ServerPlayer, box: BoundingBox) {
		val random = level.random

		repeat(MOTE_ATTEMPTS) {
			if (random.nextFloat() < MOTE_CHANCE) {
				spawn(level, player, box, random, ModParticles.MEMORY_MOTE, MOTE_RISE, MOTE_DROP)
			}
		}

		if (random.nextFloat() < RUNE_CHANCE) {
			spawn(level, player, box, random, ModParticles.ECHO_RUNE, RUNE_RISE, RUNE_DROP)
		}
	}

	private fun spawn(
		level: ServerLevel,
		player: ServerPlayer,
		box: BoundingBox,
		random: RandomSource,
		type: SimpleParticleType,
		rise: Int,
		drop: Int,
	) {
		val at = findAir(level, player, box, random, rise, drop) ?: return
		level.sendParticles(type, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0)
	}

	/**
	 * An open spot near the player, inside the cavern.
	 *
	 * Rejects rather than clamps: a clamped position would pile particles against
	 * whichever wall the player is nearest, and the ruin is full of pillars and
	 * rubble a mote should not be born inside.
	 */
	private fun findAir(
		level: ServerLevel,
		player: ServerPlayer,
		box: BoundingBox,
		random: RandomSource,
		rise: Int,
		drop: Int,
	): Vec3? {
		val eye = player.eyePosition
		val cursor = BlockPos.MutableBlockPos()

		repeat(PLACEMENT_TRIES) {
			// Polar, so candidates spread evenly around the player instead of
			// bunching along the diagonals of a square.
			// Mth.cos/sin take a double and hand back a float, so the angle has to
			// be widened here rather than at the call.
			val angle = random.nextFloat().toDouble() * Mth.TWO_PI
			val distance = Mth.lerp(random.nextFloat(), NEAR, FAR)

			val x = eye.x + Mth.cos(angle) * distance
			val z = eye.z + Mth.sin(angle) * distance
			val y = eye.y + Mth.nextInt(random, -drop, rise)

			cursor.set(Mth.floor(x), Mth.floor(y), Mth.floor(z))
			if (!box.isInside(cursor)) return@repeat
			if (!level.getBlockState(cursor).isAir) return@repeat

			return Vec3(x, y, z)
		}
		return null
	}

	/** Ticks between sweeps. One second is fine -- nothing here is time-critical. */
	private const val SCAN_INTERVAL = 20L

	/** Chances are per sweep, so these read directly as "per second". */
	private const val MOTE_ATTEMPTS = 2
	private const val MOTE_CHANCE = 0.18f

	/** Roughly one every forty seconds spent inside a ruin. */
	private const val RUNE_CHANCE = 0.025f

	/** How many spots to try before giving up on this particle for this sweep. */
	private const val PLACEMENT_TRIES = 6

	/** Near enough to notice, far enough not to spawn on the player's face. */
	private const val NEAR = 3.0f
	private const val FAR = 14.0f

	/** Vertical band around eye level, in blocks. Motes work the whole chamber. */
	private const val MOTE_RISE = 9
	private const val MOTE_DROP = 4

	/** Runes stay near eye level, where they can actually be read. */
	private const val RUNE_RISE = 3
	private const val RUNE_DROP = 2
}
