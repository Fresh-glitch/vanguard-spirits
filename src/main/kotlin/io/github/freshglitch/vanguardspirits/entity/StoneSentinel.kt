package io.github.freshglitch.vanguardspirits.entity

import io.github.freshglitch.vanguardspirits.registry.ModParticles
import net.minecraft.core.BlockPos
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

/**
 * The thing on the altar.
 *
 * It is one entity in two states rather than a block that swaps for a mob. A
 * swap would have to hide the seam between two different models; keeping the
 * same entity means the statue that was standing there *is* the thing now
 * walking toward you, and the only thing that changed is whether it moves.
 *
 * Dormant it has no AI, so it does not drift, breathe or track the player --
 * [net.minecraft.world.entity.Mob.setNoAi] freezes it in place, and the client
 * freezes its head to match. The eyes glow either way.
 */
class StoneSentinel(type: EntityType<out StoneSentinel>, level: Level) : Monster(type, level) {

	/**
	 * The spot it is set to watch, in world coordinates.
	 *
	 * Set by the structure to the mouth of the crypt stairwell. Null for one
	 * spawned any other way, which then wakes when someone approaches it instead.
	 */
	private var wardPos: BlockPos? = null

	override fun registerGoals() {
		goalSelector.addGoal(0, FloatGoal(this))
		goalSelector.addGoal(1, MeleeAttackGoal(this, 1.0, true))
		goalSelector.addGoal(6, WaterAvoidingRandomStrollGoal(this, 0.7))
		goalSelector.addGoal(7, LookAtPlayerGoal(this, Player::class.java, 12.0f))
		goalSelector.addGoal(8, RandomLookAroundGoal(this))

		targetSelector.addGoal(1, HurtByTargetGoal(this))
		targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
	}

	override fun defineSynchedData(builder: SynchedEntityData.Builder) {
		super.defineSynchedData(builder)
		builder.define(DATA_DORMANT, true)
	}

	var isDormant: Boolean
		get() = entityData.get(DATA_DORMANT)
		private set(value) = entityData.set(DATA_DORMANT, value)

	fun setWard(pos: BlockPos) {
		wardPos = pos
	}

	/** Called by the structure, so a freshly generated one starts as stone. */
	fun sleep() {
		isDormant = true
		isNoAi = true
	}

	override fun tick() {
		super.tick()

		val level = level()
		if (level is ServerLevel && isDormant && tickCount % WATCH_INTERVAL == 0) {
			watch(level)
		}
	}

	/**
	 * Wakes if anyone has come close enough to whatever it is guarding.
	 *
	 * Measured from [wardPos] rather than from the sentinel, so the trigger is
	 * the stairwell the player is heading for -- crossing the sanctum toward the
	 * crypt is what sets it off, not walking past the altar.
	 */
	private fun watch(level: ServerLevel) {
		val ward = wardPos ?: blockPosition()
		val victim = level.getNearestPlayer(
			ward.x + 0.5,
			ward.y + 0.5,
			ward.z + 0.5,
			TRIGGER_RANGE,
			/* filter = */ null,
		) ?: return

		if (victim.isSpectator || victim.isCreative) return
		awaken()
		target = victim
	}

	/**
	 * Stone to enemy.
	 *
	 * No transition animation: the rest pose and the statue pose are the same
	 * pose, so movement is the transition. It just starts moving.
	 */
	fun awaken() {
		if (!isDormant) return

		isDormant = false
		isNoAi = false

		val level = level()
		level.playSound(
			null,
			x, y, z,
			SoundEvents.SCULK_SHRIEKER_SHRIEK,
			SoundSource.HOSTILE,
			1.2f,
			0.6f,
		)
		level.playSound(
			null,
			x, y, z,
			SoundEvents.DEEPSLATE_BRICKS_BREAK,
			SoundSource.HOSTILE,
			1.0f,
			0.5f,
		)

		if (level is ServerLevel) {
			level.sendParticles(
				ModParticles.MEMORY_MOTE,
				x, y + bbHeight * 0.6, z,
				AWAKEN_MOTES,
				bbWidth.toDouble(), (bbHeight * 0.4), bbWidth.toDouble(),
				0.0,
			)
			level.sendParticles(
				ModParticles.ECHO_RUNE,
				x, y + bbHeight * 0.8, z,
				AWAKEN_RUNES,
				0.6, 0.4, 0.6,
				0.0,
			)
		}
	}

	/**
	 * Hitting a statue wakes it rather than chipping it.
	 *
	 * Letting a player whittle one down before it ever moves would waste the
	 * whole encounter, so the first blow lands as a wake-up and nothing else.
	 */
	override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
		if (isDormant) {
			awaken()
			(source.entity as? Player)?.let { target = it }
			return false
		}
		return super.hurtServer(level, source, amount)
	}

	/** Placed deliberately; it should still be there when the player comes back. */
	override fun removeWhenFarAway(distance: Double): Boolean = false

	override fun addAdditionalSaveData(output: ValueOutput) {
		super.addAdditionalSaveData(output)
		output.putBoolean(TAG_DORMANT, isDormant)
		wardPos?.let {
			output.putInt(TAG_WARD_X, it.x)
			output.putInt(TAG_WARD_Y, it.y)
			output.putInt(TAG_WARD_Z, it.z)
		}
	}

	override fun readAdditionalSaveData(input: ValueInput) {
		super.readAdditionalSaveData(input)
		isDormant = input.getBooleanOr(TAG_DORMANT, false)
		isNoAi = isDormant

		val x = input.getInt(TAG_WARD_X)
		val y = input.getInt(TAG_WARD_Y)
		val z = input.getInt(TAG_WARD_Z)
		wardPos = if (x.isPresent && y.isPresent && z.isPresent) {
			BlockPos(x.get(), y.get(), z.get())
		} else {
			null
		}
	}

	companion object {
		private val DATA_DORMANT: EntityDataAccessor<Boolean> =
			SynchedEntityData.defineId(StoneSentinel::class.java, EntityDataSerializers.BOOLEAN)

		private const val TAG_DORMANT = "Dormant"
		private const val TAG_WARD_X = "WardX"
		private const val TAG_WARD_Y = "WardY"
		private const val TAG_WARD_Z = "WardZ"

		/** How close to the guarded spot a player has to get. */
		private const val TRIGGER_RANGE = 6.0

		/** Ticks between checks while dormant. A statue can afford to be slow. */
		private const val WATCH_INTERVAL = 10

		private const val AWAKEN_MOTES = 40
		private const val AWAKEN_RUNES = 5

		/**
		 * Slow, heavily armoured and hard to shift. It is meant to be a wall that
		 * followed you, not a fight you win by backing up.
		 */
		fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 60.0)
			.add(Attributes.MOVEMENT_SPEED, 0.22)
			.add(Attributes.ATTACK_DAMAGE, 8.0)
			.add(Attributes.ATTACK_KNOCKBACK, 1.5)
			.add(Attributes.ARMOR, 12.0)
			.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
			.add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.STEP_HEIGHT, 1.0)
	}
}
