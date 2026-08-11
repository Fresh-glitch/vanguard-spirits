package io.github.freshglitch.vanguardspirits.block.entity

import io.github.freshglitch.vanguardspirits.menu.GoldenChestMenu
import io.github.freshglitch.vanguardspirits.registry.ModBlockEntities
import io.github.freshglitch.vanguardspirits.registry.ModParticles
import io.github.freshglitch.vanguardspirits.registry.ModSounds
import io.github.freshglitch.vanguardspirits.worldgen.RuinHollow
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.core.NonNullList
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import net.minecraft.world.ContainerHelper
import net.minecraft.world.entity.ContainerUser
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.ContainerOpenersCounter
import net.minecraft.world.level.block.entity.LidBlockEntity
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

/**
 * A nine-slot treasure chest.
 *
 * Extends [RandomizableContainerBlockEntity] rather than a plain container so
 * structures can drop one with a loot table attached and have it roll on first
 * open, which is the whole point of putting these in ruins.
 */
class GoldenChestBlockEntity(pos: BlockPos, state: BlockState) :
	RandomizableContainerBlockEntity(ModBlockEntities.GOLDEN_CHEST, pos, state), LidBlockEntity {

	private var items: NonNullList<ItemStack> = NonNullList.withSize(SIZE, ItemStack.EMPTY)

	/**
	 * Drives the lid angle on the client. It only ever eases toward a target,
	 * so the server just has to say open or shut.
	 */
	private val lid = ReliquaryLid()

	private val openers = object : ContainerOpenersCounter() {
		override fun onOpen(level: Level, pos: BlockPos, state: BlockState) =
			play(level, pos, ModSounds.GOLDEN_CHEST_OPEN)

		override fun onClose(level: Level, pos: BlockPos, state: BlockState) {
			play(level, pos, ModSounds.GOLDEN_CHEST_CLOSE)
			hollowIfStripped(level, pos)
		}

		/**
		 * Announces the lid state to everyone tracking the chunk.
		 *
		 * The opener count only exists on the server, so the animation cannot be
		 * derived client-side -- it has to be pushed as a block event.
		 */
		override fun openerCountChanged(
			level: Level,
			pos: BlockPos,
			state: BlockState,
			previous: Int,
			current: Int,
		) {
			level.blockEvent(pos, state.block, EVENT_LID, if (current > 0) 1 else 0)
		}

		/** Exact: the menu is ours, so it can be asked which chest it is showing. */
		override fun isOwnContainer(player: Player): Boolean {
			val menu = player.containerMenu
			return menu is GoldenChestMenu && menu.isFor(this@GoldenChestBlockEntity)
		}
	}

	override fun getContainerSize(): Int = SIZE

	override fun getItems(): NonNullList<ItemStack> = items

	override fun setItems(list: NonNullList<ItemStack>) {
		items = list
	}

	override fun getDefaultName(): Component = Component.translatable(NAME_KEY)

	override fun createMenu(id: Int, inventory: Inventory): AbstractContainerMenu =
		GoldenChestMenu(id, inventory, this)

	override fun startOpen(user: ContainerUser) {
		val level = this.level ?: return
		if (remove) return
		openers.incrementOpeners(user.livingEntity, level, blockPos, blockState, user.containerInteractionRange)
	}

	override fun stopOpen(user: ContainerUser) {
		val level = this.level ?: return
		if (remove) return
		openers.decrementOpeners(user.livingEntity, level, blockPos, blockState)
	}

	fun recheckOpen() {
		val level = this.level ?: return
		if (!remove) openers.recheckOpeners(level, blockPos, blockState)
	}

	override fun loadAdditional(input: ValueInput) {
		super.loadAdditional(input)
		items = NonNullList.withSize(SIZE, ItemStack.EMPTY)
		if (!tryLoadLootTable(input)) ContainerHelper.loadAllItems(input, items)
	}

	override fun saveAdditional(output: ValueOutput) {
		super.saveAdditional(output)
		if (!trySaveLootTable(output)) ContainerHelper.saveAllItems(output, items)
	}

	/**
	 * Hollows the ruin the moment its Reliquary is left empty.
	 *
	 * Hung off the last opener leaving rather than off each item being taken:
	 * "the visit is over and there is nothing left" is one event, and asking on
	 * every slot change would fire on a chest a player was briefly using as
	 * storage. By this point the loot table has certainly been unpacked -- the
	 * menu reads every slot to build itself -- so an empty container here means
	 * emptied, not unrolled.
	 *
	 * A Reliquary a player crafted and put in their own base is in no ruin, so
	 * [RuinHollow.hollow] answers no and nothing happens.
	 */
	private fun hollowIfStripped(level: Level, pos: BlockPos) {
		if (level !is ServerLevel) return
		if (!isEmpty) return

		if (!RuinHollow.hollow(level, pos)) return

		// Said once, at the moment it changes. The player has just read six
		// passages on the way down here and the last thing they did was prove
		// the fifth one right, so this is the line landing rather than a warning.
		for (player in level.players()) {
			if (player.blockPosition().closerThan(pos, HOLLOW_EARSHOT)) {
				player.sendSystemMessage(
					Component.translatable(HOLLOWED_KEY).withStyle(ChatFormatting.DARK_PURPLE),
				)
			}
		}
	}

	private fun play(level: Level, pos: BlockPos, sound: SoundEvent) {
		level.playSound(
			null,
			pos.x + 0.5,
			pos.y + 0.5,
			pos.z + 0.5,
			sound,
			SoundSource.BLOCKS,
			0.6f,
			1.0f,
		)
	}

	// ------------------------------------------------------------- rejection

	/** Counts out a refusal. Zero unless one is playing. */
	private var rejectTick = 0

	/** Who is being refused, so the words arrive after the runes rather than with them. */
	private var rejected: UUID? = null

	/**
	 * Turns someone away.
	 *
	 * Deliberately not instant. The point of the sequence is that a player who
	 * tunnelled past the Sentinel is told *why* they cannot have this, and a
	 * refusal that resolved in the same tick as the click would read as the
	 * chest being broken rather than defended.
	 */
	fun refuse(level: ServerLevel, player: Player) {
		if (rejectTick > 0) return

		rejectTick = 1
		rejected = player.uuid
		play(level, blockPos, ModSounds.RELIQUARY_REJECT)
	}

	/**
	 * Runes gather into the heart of the chest, then burst outward through it.
	 *
	 * Server side because the chest has no reason to tick on the server at all
	 * otherwise, and only does while this is running.
	 */
	private fun advanceRefusal(level: ServerLevel) {
		val centre = Vec3(blockPos.x + 0.5, blockPos.y + 0.5, blockPos.z + 0.5)
		val t = rejectTick

		if (t <= GATHER_TICKS) {
			// Drawing in. The radius shrinks so the runes look like they are
			// being pulled to the middle rather than falling there.
			val p = 1.0 - t.toDouble() / GATHER_TICKS
			shell(level, centre, GATHER_REACH * p + 0.2, GATHER_RUNES, t)
		} else {
			val p = (t - GATHER_TICKS).toDouble() / (REJECT_TICKS - GATHER_TICKS)
			shell(level, centre, p * BURST_REACH, BURST_RUNES, t)

			// The shove and the sickness land on the frame the wave leaves, not
			// when the click happened.
			if (t == GATHER_TICKS + 1) strike(level, centre)
		}

		rejectTick = t + 1
		if (rejectTick > REJECT_TICKS) {
			speak(level)
			rejectTick = 0
			rejected = null
		}
	}

	/** Everyone the wave passes through gets pushed off it and turned around. */
	private fun strike(level: ServerLevel, centre: Vec3) {
		play(level, blockPos, ModSounds.RELIQUARY_WARD)

		for (victim in level.players()) {
			if (victim.isSpectator || victim.isCreative) continue
			if (victim.distanceToSqr(centre) > BURST_REACH * BURST_REACH) continue

			val away = victim.position().subtract(centre)
			val len = away.length()
			val push = if (len > 0.001) away.scale(1.0 / len) else Vec3(0.0, 1.0, 0.0)

			victim.push(push.x * SHOVE, SHOVE_LIFT, push.z * SHOVE)
			victim.hurtMarked = true
			victim.addEffect(MobEffectInstance(MobEffects.NAUSEA, NAUSEA_TICKS))
		}
	}

	/** The verdict, once the runes have finished delivering it. */
	private fun speak(level: ServerLevel) {
		val who = rejected ?: return
		level.getPlayerByUUID(who)?.sendSystemMessage(Component.translatable(REJECT_KEY))
	}

	/**
	 * Points spread evenly over a sphere of the given radius.
	 *
	 * A golden-angle spiral rather than nested latitude rings, which bunch at
	 * the poles and leave the shell looking like a globe with seams.
	 *
	 * **[step] is why this reads as a shell and not a star.** The spiral is
	 * deterministic, so without it every tick puts its points at exactly the
	 * same bearings and only the radius changes -- which draws a fixed set of
	 * rays marching outward, and the burst came out as an asterisk. Advancing
	 * the longitude and sliding the latitudes by an amount that never repeats
	 * means consecutive shells never line up, and the eye reads a surface.
	 */
	private fun shell(level: ServerLevel, centre: Vec3, radius: Double, count: Int, step: Int) {
		val drift = step * LATITUDE_DRIFT
		val twist = step * SHELL_TWIST

		for (i in 0 until count) {
			val k = ((i + 0.5) / count + drift) % 1.0
			val phi = acos(1.0 - 2.0 * k)
			val theta = GOLDEN_ANGLE * i + twist

			level.sendParticles(
				ModParticles.WARD_RUNE,
				centre.x + sin(phi) * cos(theta) * radius,
				centre.y + cos(phi) * radius,
				centre.z + sin(phi) * sin(theta) * radius,
				1,
				0.0, 0.0, 0.0,
				0.0,
			)
		}
	}

	override fun getOpenNess(partialTick: Float): Float = lid.openness(partialTick)

	/** Receives the block event fired by [openerCountChanged] on the server. */
	override fun triggerEvent(id: Int, value: Int): Boolean {
		if (id == EVENT_LID) {
			lid.shouldBeOpen(value > 0)
			return true
		}
		return super.triggerEvent(id, value)
	}

	companion object {
		/** Three by three, as a square. */
		const val SIZE: Int = 9

		const val NAME_KEY: String = "container.vanguard-spirits.golden_chest"

		/** Said once, to whoever is standing in the vault when it happens. */
		const val HOLLOWED_KEY: String = "message.vanguard-spirits.ruin.hollowed"

		/** Far enough to cover the vault, short enough not to reach the surface. */
		private const val HOLLOW_EARSHOT = 24.0

		/** Block-event id carrying the lid's open/shut state to clients. */
		const val EVENT_LID: Int = 1

		/** Client-only: eases the lid toward whatever the last block event said. */
		fun clientTick(
			level: Level,
			pos: BlockPos,
			state: BlockState,
			entity: GoldenChestBlockEntity,
		) {
			entity.lid.tick()
		}

		/** Server-only, and idle unless somebody is being turned away. */
		fun serverTick(
			level: Level,
			pos: BlockPos,
			state: BlockState,
			entity: GoldenChestBlockEntity,
		) {
			if (entity.rejectTick > 0 && level is ServerLevel) entity.advanceRefusal(level)
		}

		/** Ticks the runes spend converging before the shell goes out. */
		private const val GATHER_TICKS = 14

		/** And the whole refusal, gathering included. Just under two seconds. */
		private const val REJECT_TICKS = 34

		private const val GATHER_REACH = 1.6
		private const val BURST_REACH = 4.5

		/** Denser than the echo runes ever get: each one is a third the size. */
		private const val GATHER_RUNES = 9
		private const val BURST_RUNES = 40

		/** Enough to put someone on their back foot, not enough to hurl them. */
		private const val SHOVE = 0.62
		private const val SHOVE_LIFT = 0.32

		/** Twelve seconds. Long enough to make the walk back out unpleasant. */
		private const val NAUSEA_TICKS = 240

		/** Radians. Successive points land a golden angle apart around the axis. */
		private const val GOLDEN_ANGLE = 2.399963

		/**
		 * How far each successive shell is turned and slid against the last.
		 *
		 * Both deliberately not near any simple fraction of a turn: values that
		 * come back around after a few ticks would rebuild the spokes rather than
		 * hide them.
		 */
		private const val SHELL_TWIST = 0.7391
		private const val LATITUDE_DRIFT = 0.1373

		const val REJECT_KEY: String = "message.vanguard-spirits.reliquary_rejects"
	}
}
