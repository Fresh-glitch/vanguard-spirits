package io.github.freshglitch.vanguardspirits.entity

import net.minecraft.core.BlockPos
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.util.Mth
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
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
import net.minecraft.world.level.block.state.BlockState

/**
 * One of the people who lived here, still going about it.
 *
 * The counterpart to [StoneSentinel] in every way that matters: where the
 * sentinel is slow, armoured and announces itself, a Remnant is quick, brittle
 * and already moving by the time it is seen. The ruin's defence is not one
 * thing but two opposite ones.
 *
 * Its eyes only light once it has something to chase, which is the same rule
 * the sentinel follows -- a dark socket means it has not noticed you yet.
 */
class Remnant(type: EntityType<out Remnant>, level: Level) : Monster(type, level), RuinDefender {

	/**
	 * Never turns on the other things guarding this place.
	 *
	 * Both halves of the check are needed. [canAttack] stops the target goals
	 * ever picking one up, and [setTarget] catches everything else -- above all
	 * the retaliation path, where a sentinel's slam clipping a Remnant would
	 * otherwise start a fight the player could just stand back and watch.
	 */
	override fun canAttack(victim: LivingEntity): Boolean =
		victim !is RuinDefender && super.canAttack(victim)

	override fun setTarget(victim: LivingEntity?) {
		if (victim is RuinDefender) return
		super.setTarget(victim)
	}

	override fun registerGoals() {
		goalSelector.addGoal(0, FloatGoal(this))
		goalSelector.addGoal(1, MeleeAttackGoal(this, CHASE_SPEED, false))
		goalSelector.addGoal(6, WaterAvoidingRandomStrollGoal(this, 0.8))
		goalSelector.addGoal(7, LookAtPlayerGoal(this, Player::class.java, 10.0f))
		goalSelector.addGoal(8, RandomLookAroundGoal(this))

		targetSelector.addGoal(1, HurtByTargetGoal(this))
		targetSelector.addGoal(2, NearestAttackableTargetGoal(this, Player::class.java, true))
	}

	override fun defineSynchedData(builder: SynchedEntityData.Builder) {
		super.defineSynchedData(builder)
		builder.define(DATA_GLOW, 0)
	}

	/** 0..100, same scale the sentinel uses, so both renderers read it alike. */
	var glow: Int
		get() = entityData.get(DATA_GLOW)
		private set(value) = entityData.set(DATA_GLOW, value)

	override fun tick() {
		super.tick()
		if (level() !is ServerLevel) return

		// Eases rather than snaps. A Remnant that spots you across a room should
		// look like something kindling, not like a light switch.
		val want = if (target?.isAlive == true) GLOW_FULL else 0
		val step = if (want > glow) GLOW_RISE else GLOW_FADE
		val next = Mth.clamp(glow + Mth.clamp(want - glow, -step, step), 0, GLOW_FULL)
		if (next != glow) glow = next
	}

	override fun getAmbientSound(): SoundEvent = SoundEvents.ZOMBIE_AMBIENT

	override fun getHurtSound(source: DamageSource): SoundEvent = SoundEvents.ZOMBIE_HURT

	override fun getDeathSound(): SoundEvent = SoundEvents.ZOMBIE_DEATH

	/** Quick, soft and far too many of them. */
	override fun playStepSound(pos: BlockPos, state: BlockState) {
		playSound(SoundEvents.SOUL_SAND_STEP, 0.4f, 0.8f + random.nextFloat() * 0.3f)
	}

	companion object {
		private val DATA_GLOW: EntityDataAccessor<Int> =
			SynchedEntityData.defineId(Remnant::class.java, EntityDataSerializers.INT)

		const val GLOW_FULL: Int = 100

		/** Kindles quickly, dies down slowly. */
		private const val GLOW_RISE = 12
		private const val GLOW_FADE = 3

		/** Well above the 1.0 a vanilla zombie uses. It runs you down. */
		private const val CHASE_SPEED = 1.35

		/**
		 * Fast and fragile, deliberately opposite to the sentinel upstairs.
		 *
		 * These arrive in numbers from spawners, so individually they have to be
		 * killable in a hit or two -- the pressure is meant to come from how many
		 * there are and how quickly they close, not from any one of them.
		 */
		fun createAttributes(): AttributeSupplier.Builder = createMonsterAttributes()
			.add(Attributes.MAX_HEALTH, 22.0)
			.add(Attributes.MOVEMENT_SPEED, 0.36)
			.add(Attributes.ATTACK_DAMAGE, 5.0)
			.add(Attributes.ARMOR, 2.0)
			.add(Attributes.FOLLOW_RANGE, 28.0)
			.add(Attributes.STEP_HEIGHT, 1.0)
	}
}
