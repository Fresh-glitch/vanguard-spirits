package io.github.freshglitch.vanguardspirits.item

import io.github.freshglitch.vanguardspirits.charm.Attunement
import io.github.freshglitch.vanguardspirits.registry.ModComponents
import io.github.freshglitch.vanguardspirits.registry.ModSounds
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/**
 * Consumed to permanently raise the holder's attunement by one, up to
 * [Attunement.MAX]. Intended as deep Guarded Ruins loot rather than a craft.
 */
class EchoOfKinshipItem(props: Item.Properties) : Item(props) {

	/**
	 * Takes the Sentinel's mark off the moment somebody is carrying it.
	 *
	 * Hanging in the air is what an Echo does while it is still the ruin's, not
	 * a property of the object. Once it is in a pocket it is baggage like
	 * anything else, and throwing it back down should drop it, not hang it.
	 */
	override fun inventoryTick(
		stack: ItemStack,
		level: ServerLevel,
		holder: Entity,
		slot: EquipmentSlot?,
	) {
		super.inventoryTick(stack, level, holder, slot)
		if (stack.has(ModComponents.KINSHIP_FREED)) stack.remove(ModComponents.KINSHIP_FREED)
	}

	override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
		// Let the client predict the swing; the server owns the actual change.
		if (level.isClientSide) return InteractionResult.SUCCESS

		if (!Attunement.raise(player)) {
			player.sendOverlayMessage(
				Component.translatable(MAXED_KEY).withStyle(ChatFormatting.GRAY),
			)
			return InteractionResult.FAIL
		}

		player.getItemInHand(hand).consume(1, player)
		level.playSound(
			null,
			player.x,
			player.y,
			player.z,
			SoundEvents.BEACON_ACTIVATE,
			SoundSource.PLAYERS,
			0.7f,
			1.4f,
		)
		player.sendOverlayMessage(
			Component.translatable(RAISED_KEY, Attunement.of(player), Attunement.MAX)
				.withStyle(ChatFormatting.AQUA),
		)
		return InteractionResult.SUCCESS
	}

	companion object {
		const val RAISED_KEY: String = "message.vanguard-spirits.attunement.raised"
		const val MAXED_KEY: String = "message.vanguard-spirits.attunement.maxed"

		/** Whether this stack still carries the mark a Sentinel put on it. */
		@JvmStatic
		fun isFreed(stack: ItemStack): Boolean =
			stack.getOrDefault(ModComponents.KINSHIP_FREED, false)

		/**
		 * Taken. Cuts the call off and answers it.
		 *
		 * The bloom runs about two and a half seconds and is fired off at the
		 * item's position, so without stopping it outright a player who picked
		 * one up would walk away with it still singing behind them from a spot
		 * where there is now nothing at all.
		 *
		 * Stopping a sound is a packet per listener rather than anything the
		 * level can do, so everyone who could have heard it gets told. The reach
		 * is the call's own, plus a little for anyone who has moved since.
		 */
		@JvmStatic
		fun claimed(level: Level, entity: ItemEntity) {
			if (level !is ServerLevel) return

			val hush = ClientboundStopSoundPacket(ModSounds.KINSHIP_RELEASE.location(), SoundSource.BLOCKS)
			for (listener in level.players()) {
				if (listener.distanceToSqr(entity) <= EARSHOT_SQ) listener.connection.send(hush)
			}

			level.playSound(
				null,
				entity.x, entity.y, entity.z,
				ModSounds.KINSHIP_CLAIM,
				SoundSource.BLOCKS,
				0.9f,
				1.0f,
			)
		}

		/**
		 * What a dropped Echo does instead of lying in the dirt.
		 *
		 * Called every tick from `ItemEntityMixin`, which is the only way to get
		 * at a dropped item's tick -- the behaviour belongs to the entity, not
		 * the item, and vanilla offers a mod no hook on it.
		 *
		 * It hangs at a fixed height over whatever is beneath it and calls until
		 * somebody comes. Everything the Sentinel drops besides this is deepslate
		 * and diamonds, so the one thing worth crossing a ruin for should not be
		 * sitting in the same heap looking like the rest of it.
		 */
		@JvmStatic
		fun drift(level: Level, entity: ItemEntity) {
			// Only one a Sentinel gave up. A player who throws theirs on the
			// floor gets an ordinary dropped item, because the moment they
			// picked it up the mark came off -- see inventoryTick.
			if (!isFreed(entity.item)) return

			// Both sides need the gravity flag off or the client interpolates the
			// item downward between position updates and it visibly judders.
			entity.isNoGravity = true

			if (level !is ServerLevel) return

			if (entity.age == 0) {
				call(level, entity)

				// Waits as long as it takes. Everything else the Sentinel drops
				// can rot on the floor; losing the one thing worth the fight to
				// a five minute timer while the player is still finding their way
				// back down would be a poor joke.
				//
				// Note this freezes `age` at -32768 outright -- vanilla's tick
				// stops incrementing it once it holds that value -- which is why
				// nothing below is timed off it.
				entity.setUnlimitedLifetime()
			} else if (level.gameTime % CALL_EVERY == 0L) {
				call(level, entity)
			}

			hover(level, entity)
		}

		/**
		 * The bloom it gives off, over and over, until somebody comes.
		 *
		 * Carried well past the usual sixteen blocks on purpose: an Echo that
		 * rolled into a corner of the vault is meant to be findable by ear, and
		 * a player who never saw it drop has nothing else to go on.
		 */
		private fun call(level: ServerLevel, entity: ItemEntity) {
			level.playSound(
				null,
				entity.x, entity.y, entity.z,
				ModSounds.KINSHIP_RELEASE,
				// A thing in the world rather than something the player did, so
				// it belongs on the same slider as every other world object.
				SoundSource.BLOCKS,
				CALL_VOLUME,
				1.0f,
			)
		}

		/**
		 * Holds it a fixed height above the ground and lets it breathe there.
		 *
		 * Eased rather than snapped, so an Echo dropped in mid air sinks to its
		 * station instead of appearing at it, and one that had the floor mined
		 * out from under it follows the floor down.
		 */
		private fun hover(level: ServerLevel, entity: ItemEntity) {
			entity.deltaMovement = entity.deltaMovement.multiply(DRAG, 0.0, DRAG)

			val floor = groundUnder(level, entity)
			// Off the world clock, not the entity's own age, which stops moving
			// the moment the lifetime is made unlimited.
			val bob = Mth.sin(level.gameTime * BOB_RATE.toDouble()) * BOB
			val want = floor + HOVER + bob
			val step = Mth.clamp((want - entity.y) * SETTLE, -MAX_FALL, MAX_FALL)

			entity.setPos(entity.x, entity.y + step, entity.z)
		}

		/** First solid surface below, or a little under it if there is none near. */
		private fun groundUnder(level: ServerLevel, entity: ItemEntity): Double {
			val cursor = BlockPos.MutableBlockPos()
			val from = Mth.floor(entity.y)
			for (y in from downTo from - PROBE_DEPTH) {
				cursor.set(entity.blockX, y, entity.blockZ)
				val shape = level.getBlockState(cursor).getCollisionShape(level, cursor)
				if (!shape.isEmpty) return y + shape.max(Direction.Axis.Y)
			}
			return (from - PROBE_DEPTH).toDouble()
		}

		/** Blocks above the surface it settles at. */
		private const val HOVER = 0.55

		/** How far it drifts up and down about that, and how fast. */
		private const val BOB = 0.08
		private const val BOB_RATE = 0.09f

		/** Fraction of the remaining gap closed each tick, and a speed limit on it. */
		private const val SETTLE = 0.14
		private const val MAX_FALL = 0.35

		/** Horizontal coasting is damped out so it comes to rest where it landed. */
		private const val DRAG = 0.82

		/** How far down to look for something to hang over. */
		private const val PROBE_DEPTH = 8

		/** Three seconds, against a bloom of about two and a half. */
		private const val CALL_EVERY = 60L

		/**
		 * Past one, this is also the reach: Minecraft ranges a variable-range
		 * sound at sixteen blocks times its volume, so this carries about thirty
		 * two. Loud stood over it, which is the price of being findable at all.
		 */
		private const val CALL_VOLUME = 2.0f

		/** The call's own reach, with slack for anyone who has moved since. */
		private const val EARSHOT_SQ = 40.0 * 40.0

	}
}
