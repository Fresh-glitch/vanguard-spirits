package io.github.freshglitch.vanguardspirits.item

import io.github.freshglitch.vanguardspirits.registry.ModBlocks
import io.github.freshglitch.vanguardspirits.worldgen.RuinSettled
import io.github.freshglitch.vanguardspirits.worldgen.Ruins
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Holds a player's half-made decision to set an Epitaph down.
 *
 * Placing one is the only irreversible act in the mod that costs nothing to
 * trigger -- a stray right-click while walking through a graveyard would end
 * that ruin for good -- so it asks first. The asking is a round trip, because
 * only the server knows whether there is a graveyard under the cursor.
 *
 * ## What is kept, and what is not
 *
 * The **position stays here**. The prompt sent to the client says only what kind
 * of ground it found, and the answer that comes back is nothing at all, so there
 * is no field a modified client could point somewhere else. Everything is
 * re-checked when the answer arrives, because between asking and answering a
 * player can walk away, put the stone in a chest, or watch somebody else build
 * on the spot.
 */
object EpitaphPlacement {

	private class Pending(
		val hit: BlockHitResult,
		val hand: InteractionHand,
		val deadline: Long,
	)

	private val waiting = HashMap<UUID, Pending>()

	fun register() {
		ServerPlayNetworking.registerGlobalReceiver(EpitaphConfirm.TYPE) { _, context ->
			// The receiver runs on the network thread. Placing a block off the
			// server thread is the classic way to corrupt a chunk under load and
			// never in testing.
			val player = context.player()
			val level = player.level() as? ServerLevel ?: return@registerGlobalReceiver
			level.server.execute { place(player) }
		}

		// A player who logs out mid-question is not owed an answer, and the map
		// should not remember them.
		ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
			waiting.remove(handler.player.uuid)
		}
	}

	/**
	 * Puts the question, and holds the spot until it is answered.
	 *
	 * Returns CONSUME rather than SUCCESS: nothing has been placed, and the swing
	 * animation would be a promise the server has not made yet.
	 */
	fun ask(player: ServerPlayer, hit: BlockHitResult, hand: InteractionHand): InteractionResult {
		val stack = player.getItemInHand(hand)
		val context = BlockPlaceContext(player, hand, stack, hit)
		if (!context.canPlace()) return InteractionResult.FAIL

		val level = player.level() as? ServerLevel ?: return InteractionResult.FAIL

		// Asked about where the stone will actually stand, not the block that was
		// clicked -- those are different by one, and the difference is exactly a
		// graveyard's edge.
		val where = context.clickedPos
		val ground = when {
			Ruins.startChunkAt(level, where) == null -> EpitaphPrompt.Ground.ELSEWHERE
			// Asked second, because settling is only meaningful inside a ruin --
			// and [RuinSettled.isSettled] answers no for a position in none, so
			// the two questions in the other order would agree for the wrong
			// reason.
			RuinSettled.isSettled(level, where) -> EpitaphPrompt.Ground.SETTLED
			else -> EpitaphPrompt.Ground.RUIN
		}

		waiting[player.uuid] = Pending(hit, hand, level.gameTime + PATIENCE)
		ServerPlayNetworking.send(player, EpitaphPrompt(ground))
		return InteractionResult.CONSUME
	}

	/**
	 * The answer came back yes. Everything is checked again from scratch.
	 *
	 * A pending placement is a claim about a moment that has already passed, so
	 * none of it is trusted: the deadline catches a client that answered an hour
	 * late, the item check catches a player who put the stone away, and the reach
	 * check catches one who walked off and confirmed from across the valley.
	 */
	private fun place(player: ServerPlayer) {
		val pending = waiting.remove(player.uuid) ?: return
		if (player.level().gameTime > pending.deadline) return

		val stack = player.getItemInHand(pending.hand)
		if (!stack.`is`(ModBlocks.EPITAPH.asItem())) return

		val target = Vec3.atCenterOf(pending.hit.blockPos)
		if (player.distanceToSqr(target) > REACH_SQ) return

		val item = stack.item as? BlockItem ?: return
		item.place(BlockPlaceContext(player, pending.hand, stack, pending.hit))
	}

	/** Ticks a player has to answer. Twenty seconds is a slow reader, not a stall. */
	private const val PATIENCE = 400L

	/**
	 * How far from the clicked block a confirmation is still honoured.
	 *
	 * Generous against vanilla's own reach, because the prompt costs a moment and
	 * a player may drift a step while reading it. Tight enough that it is still a
	 * placement rather than a ranged one.
	 */
	private const val REACH_SQ = 8.0 * 8.0
}
