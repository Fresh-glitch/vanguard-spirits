package io.github.freshglitch.vanguardspirits.block

import io.github.freshglitch.vanguardspirits.entity.Remnant
import io.github.freshglitch.vanguardspirits.registry.ModEntities
import io.github.freshglitch.vanguardspirits.registry.ModParticles
import io.github.freshglitch.vanguardspirits.registry.ModSounds
import io.github.freshglitch.vanguardspirits.worldgen.RuinHollow
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.EntitySpawnReason
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.AABB

/**
 * Turned earth over somebody who has not finished with the place.
 *
 * Whoever is under it comes up when the mound is opened, which makes a graveyard
 * something a player walks *through* rather than mines. The reward for being
 * careful is nothing happening.
 *
 * Whether anyone is actually buried here is a blockstate rather than an
 * assumption about where the block came from. Worldgen lays occupied graves; the
 * item in a player's hand places empty ones, so carrying a stack home does not
 * turn into a portable spawner. It also means an emptied grave can be left in
 * place as a plain mound if that is ever wanted.
 */
class GraveBlock(properties: Properties) : Block(properties) {

	init {
		registerDefaultState(stateDefinition.any().setValue(OCCUPIED, false))
	}

	override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
		builder.add(OCCUPIED)
	}

	/**
	 * Answers every way a block can leave the world, not just a player's pick.
	 *
	 * `LevelChunk.setBlockState` is what calls this, so an explosion, a piston or
	 * a command reaches it on the same path a shovel does -- which matters,
	 * because blowing the graveyard open is exactly the shortcut this is meant to
	 * close. Picked over `playerWillDestroy` for that reason, and because that one
	 * also fires client-side for prediction.
	 */
	override fun affectNeighborsAfterRemoval(
		state: BlockState,
		level: ServerLevel,
		pos: BlockPos,
		movedByPiston: Boolean,
	) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston)

		// A piston has not opened anything -- the grave is still a grave, it is
		// simply somewhere else now, with its occupant still in it.
		if (movedByPiston) return
		if (!state.getValue(OCCUPIED)) return

		rise(level, pos)
	}

	/**
	 * A hollowed graveyard giving its dead back, after dark.
	 *
	 * The fifth passage promised this and the mod never charged for it: emptying
	 * a Reliquary takes the fragments that were holding these people together,
	 * and *"they still rise"*. So a ruin the player has stripped stops being dead
	 * ground and starts producing the consequence, permanently.
	 *
	 * Four gates, and each is load-bearing:
	 *
	 * - **Occupied.** An emptied mound has nobody left in it, and a mound a
	 *   player placed themselves never had anybody.
	 * - **Hollowed.** An untouched ruin's graveyard stays quiet. This is a
	 *   consequence of looting, not weather.
	 * - **Dark.** Daylight is the window to cross a graveyard you have stripped,
	 *   which is what stops it being a wall rather than a risk.
	 * - **Not already crowded.** Twenty-eight mounds rising freely would be a
	 *   flood inside a night. The cap turns it into a trickle a player can farm
	 *   and cannot be buried by.
	 *
	 * The grave stays occupied afterwards. These are the ones with nothing left
	 * to settle, so there is no bottom to the supply -- which is the whole point,
	 * since [io.github.freshglitch.vanguardspirits.charm.Binding] is a sink that
	 * needs one.
	 */
	override fun randomTick(
		state: BlockState,
		level: ServerLevel,
		pos: BlockPos,
		random: RandomSource,
	) {
		if (!state.getValue(OCCUPIED)) return
		if (!level.isDarkOutside) return
		if (random.nextFloat() >= RISE_CHANCE) return
		if (!RuinHollow.isHollowed(level, pos)) return

		val crowd = level.getEntitiesOfClass(
			Remnant::class.java,
			AABB(pos).inflate(CROWD_REACH),
		)
		if (crowd.size >= CROWD_CAP) return

		rise(level, pos, hollowed = true)
	}

	/** Lets whoever was under the mound out. */
	private fun rise(level: ServerLevel, pos: BlockPos, hollowed: Boolean = false) {
		val x = pos.x + 0.5
		val y = pos.y.toDouble()
		val z = pos.z + 0.5

		level.playSound(null, x, y, z, ModSounds.GRAVE_DISTURB, SoundSource.BLOCKS, 1.0f, 0.9f + level.random.nextFloat() * 0.2f)

		// Thrown earth first, then the ruin's own glyphs over it, so the effect
		// reads as something being dug out rather than conjured.
		level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.4, z, SOIL_PUFF, 0.28, 0.1, 0.28, 0.02)
		level.sendParticles(ModParticles.ECHO_RUNE, x, y + 0.6, z, RUNES, 0.3, 0.35, 0.3, 0.0)

		val risen = ModEntities.REMNANT.create(level, EntitySpawnReason.TRIGGERED) ?: return

		// One that came up out of a stripped ruin is carrying what is left of
		// whoever it was, and gives it up when put down. Marked on the entity
		// rather than decided at death, because by then there is nothing left to
		// ask about where it came from.
		risen.hollowed = hollowed

		// Facing a quarter turn off at random, so a row of graves opened together
		// does not produce a rank of Remnants all looking the same way.
		risen.snapTo(x, y, z, level.random.nextFloat() * 360.0f, 0.0f)
		level.addFreshEntity(risen)
	}

	companion object {
		/** Whether anyone is still down there. */
		val OCCUPIED: BooleanProperty = BlockStateProperties.OCCUPIED

		private const val SOIL_PUFF = 12
		private const val RUNES = 5

		/**
		 * Chance a hollowed mound gives somebody up on a random tick.
		 *
		 * Random ticks arrive at roughly one per block per 47 seconds at the
		 * default rate, so across a plot of twenty-eight this is a handful an
		 * hour of night rather than a wave. The crowd cap is what actually holds
		 * the ceiling; this sets the pace under it.
		 */
		private const val RISE_CHANCE = 0.06f

		/** How far out a mound looks before deciding the ground is busy enough. */
		private const val CROWD_REACH = 12.0

		/** Remnants already up within that reach before the graves stay shut. */
		private const val CROWD_CAP = 6
	}
}
