package io.github.freshglitch.vanguardspirits.entity

import io.github.freshglitch.vanguardspirits.registry.ModSounds
import io.github.freshglitch.vanguardspirits.registry.ModTags
import java.util.UUID
import net.minecraft.ChatFormatting
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundEvent
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.attributes.AttributeSupplier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.FloatGoal
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

/**
 * The one thing in this mod that was there, and is still alive to say so.
 *
 * Everything else the player meets is evidence. Murals are what somebody wrote
 * down, Remnants are what is left of them, a Sentinel is a machine still
 * following an order nobody has rescinded, and the graves are the people. All
 * of it is past tense. The Nymph is not: she watched the ruin get built, she
 * watched it fail, and she is standing in a flower forest several hundred years
 * later with opinions about it. That is the whole reason she exists -- a mod
 * called Echoes of the Past needs exactly one voice that is not an echo.
 *
 * ## She keeps accounts
 *
 * A player near her is being watched, and [GroveWatch] tells her when they take
 * something. Killing an animal in her sight is worth more than felling a tree,
 * which is worth more than picking a flower, and [grievance] adds them up. Where
 * that total lands decides her [Temper], and the temper decides whether she will
 * talk to you, warn you, or come for you.
 *
 * Two decisions worth defending.
 *
 * **The grievance is hers, not the world's.** It lives on this entity rather
 * than in a per-player attachment, so it is a memory rather than a reputation --
 * walk to the next flower forest and the Nymph there has no opinion of you yet,
 * because she has not seen anything. That is the correct fiction and it is also
 * the honest one: nothing in the world tells her what you did on the far side of
 * the map, so nothing should behave as though it did.
 *
 * **It decays, and it can be paid down.** Left alone she cools at one point
 * every five seconds; offered a flower she forgives fifteen at a stroke. So a
 * player who has angered her has something to *do* about it other than run or
 * kill her, which is the difference between a guardian and a hazard.
 *
 * ## Killing her costs, and pays nothing
 *
 * She drops no loot. The same rule the anchored Mourner follows, for the same
 * reason: the moment the rare thing pays out, shooting it becomes the correct
 * play and the encounter it was built for never happens. What she is worth is
 * the conversation, and that is only available to somebody who has not given her
 * a reason to stop talking.
 */
class Nymph(type: EntityType<out Nymph>, level: Level) : PathfinderMob(type, level) {

	enum class Temper { CALM, WARY, WRATHFUL }

	/**
	 * What she is owed, 0 to [GRIEVANCE_MAX].
	 *
	 * Server-side only. The client never needs the number, only which of three
	 * tempers it lands in, and that travels as [DATA_TEMPER] -- one byte instead
	 * of a counter ticking down every five seconds for as long as anyone is
	 * within render distance.
	 */
	private var grievance = 0

	/** Whose account it is. Saved: she should still be cross after a reload. */
	private var offender: UUID? = null

	/**
	 * Whether she is owed anything at all, however little.
	 *
	 * The one thing [NymphSpeech] needs from the ledger. Deliberately a boolean
	 * rather than the number: a remark about somebody having taken *a little*
	 * from the wood should not start varying with a count nobody can see.
	 */
	internal val sore: Boolean
		get() = grievance > 0

	/**
	 * How far into her temper she looks, 0 to 1, eased on both sides.
	 *
	 * Kept on the entity rather than in the renderer, because a renderer is
	 * shared by every Nymph on screen -- anything remembered on one would be
	 * remembered for all of them, which is the trap the Reliquary's lid fell into
	 * and nobody noticed until there were two of them in view.
	 */
	var wrathAmount: Float = 0.0f
		private set

	/**
	 * How far into her run she is, 0 walking to 1 running, eased like [wrathAmount].
	 *
	 * The gait is a **fact about the server**, not something the client can
	 * infer. The obvious client-side signal is `walkAnimationSpeed`, and it
	 * cannot do this job: `LivingEntity.updateWalkAnimation` is
	 * `min(blocksMovedThisTick * 4, 1)`, so it saturates at a quarter of a block
	 * per tick and a strolling Nymph and a charging one land close enough
	 * together that the gait would flicker between them. What actually decides
	 * it is whether she has a target -- `MeleeAttackGoal` is the only goal that
	 * uses [WRATH_SPEED] and it only runs when one is set -- so that is what
	 * travels, as one bit, and it is exact rather than inferred.
	 *
	 * Standing still is not a separate case. The model scales both gaits by how
	 * fast she is really moving, so a Nymph swinging at somebody from a standstill
	 * has this at 1 and shows no stride at all.
	 */
	var runAmount: Float = 0.0f
		private set

	/**
	 * How far through what she has to say this listener has got.
	 *
	 * Two numbers, not one. [conversation] counts turns taken and drives which
	 * kind of line comes next; [lorePos] counts how much of the *thread* has
	 * actually been said. Deriving the second from the first is what the first
	 * draft did, and it meant a turn spent on an observation and a turn spent on
	 * a remark about nothing were indistinguishable -- see [NymphSpeech.pick].
	 */
	private var conversation = 0
	private var lorePos = 0
	private var lastSpokeTo: UUID? = null
	private var speakCooldown = 0

	override fun registerGoals() {
		goalSelector.addGoal(0, FloatGoal(this))
		// Only ever runs when something set a target, and the only thing that
		// sets one is her temper reaching WRATHFUL. A calm Nymph carries this
		// goal around unused, which is cheaper than rebuilding the goal set.
		goalSelector.addGoal(1, MeleeAttackGoal(this, WRATH_SPEED, true))
		goalSelector.addGoal(5, WaterAvoidingRandomStrollGoal(this, WANDER_SPEED))
		goalSelector.addGoal(6, LookAtPlayerGoal(this, Player::class.java, WATCH_RANGE))
		goalSelector.addGoal(7, RandomLookAroundGoal(this))

		targetSelector.addGoal(1, HurtByTargetGoal(this))
	}

	override fun defineSynchedData(builder: SynchedEntityData.Builder) {
		super.defineSynchedData(builder)
		builder.define(DATA_TEMPER, Temper.CALM.ordinal)
		builder.define(DATA_HUNTING, false)
	}

	val temper: Temper
		get() = TEMPERS[Mth.clamp(entityData.get(DATA_TEMPER), 0, TEMPERS.size - 1)]

	/**
	 * Rare enough that despawning her would read as a bug.
	 *
	 * A player who finds one, walks home for a stack of flowers and comes back to
	 * an empty meadow has not learned anything about the mechanic -- they have
	 * learned that the game is unreliable. The spawn roll in `ModSpawns` is
	 * punishing precisely so that each one can be permanent.
	 */
	override fun removeWhenFarAway(distance: Double): Boolean = false

	// -------------------------------------------------------------- accounts

	/**
	 * Somebody has taken something, and she saw it.
	 *
	 * Called from [GroveWatch] rather than polled for, so the cost of watching a
	 * grove is paid by the handful of players who actually break something in one
	 * rather than by every Nymph on every tick.
	 */
	fun wrong(who: Player, weight: Int) {
		if (who.isSpectator) return

		val before = temper
		grievance = Mth.clamp(grievance + weight, 0, GRIEVANCE_MAX)
		offender = who.uuid
		reckon()

		// Speaks only when the verdict changes. Announcing every felled log would
		// turn a warning into wallpaper, and she would stop being read at all.
		if (temper != before) declare(who, temper)
	}

	/**
	 * And somebody has given something back.
	 *
	 * Deliberately says nothing about whether it mattered. It used to return
	 * that, and [offered] used it to choose between apology and gift, which was
	 * wrong: [reckon] drops her to CALM once the grievance falls below
	 * `WARY_AT - RELENT`, so between 1 and 7 she holds nothing against you and
	 * this still reported a debt. She would say "Very well. Go carefully" and
	 * then, to the next flower, "It does not undo it." The temper is the thing
	 * she has actually told the player about, so the temper is what to ask.
	 */
	private fun forgive(amount: Int) {
		if (grievance <= 0) return
		grievance = Mth.clamp(grievance - amount, 0, GRIEVANCE_MAX)
		reckon()
	}

	/**
	 * Recomputes the temper from the total and publishes it to the client.
	 *
	 * **A latch, not a comparison**, and that distinction was bought in a
	 * playtest. The first version simply asked which band the number was in, and
	 * because the grievance decays a point every five seconds, a Nymph who
	 * crossed into wrath at 58 fell back under 55 about ten seconds later. The
	 * log showed it plainly: "Enough." at one moment and a wary line twelve
	 * seconds after. At the boundary she flickers -- hunting, stopping, hunting
	 * -- and [hunt] drops her target on every dip, so a fight visibly breaks off
	 * and restarts.
	 *
	 * So entering a temper is harder than staying in one. She reaches wrath at
	 * [WRATH_AT] and holds it until [RELENT] points below that, which at the
	 * decay rate is about two minutes of being genuinely dangerous rather than
	 * two seconds of it.
	 */
	private fun reckon() {
		val now = temper
		val next = when {
			grievance >= WRATH_AT -> Temper.WRATHFUL
			now == Temper.WRATHFUL && grievance >= WRATH_AT - RELENT -> Temper.WRATHFUL
			grievance >= WARY_AT -> Temper.WARY
			now != Temper.CALM && grievance >= WARY_AT - RELENT -> Temper.WARY
			else -> Temper.CALM
		}
		if (next.ordinal != entityData.get(DATA_TEMPER)) {
			entityData.set(DATA_TEMPER, next.ordinal)
		}
		// Nothing left to hold against anyone. Dropped so a player who made
		// amends is not still the standing quarry the moment somebody else
		// annoys her.
		if (grievance == 0) offender = null
	}

	private fun declare(who: Player, now: Temper) {
		when (now) {
			Temper.WARY -> {
				say(who, NymphSpeech.warning(this))
				playSound(ModSounds.NYMPH_WARN, 1.0f, 1.0f)
			}
			Temper.WRATHFUL -> {
				say(who, NymphSpeech.WRATH_KEY)
				playSound(ModSounds.NYMPH_WRATH, 1.2f, 0.9f)
				(level() as? ServerLevel)?.sendParticles(
					ParticleTypes.ANGRY_VILLAGER,
					x, y + bbHeight * 0.9, z,
					8, 0.4, 0.3, 0.4, 0.0,
				)
			}
			Temper.CALM -> {
				say(who, NymphSpeech.SETTLED_KEY)
				playSound(ModSounds.NYMPH_GIFT, 0.8f, 1.1f)
			}
		}
	}

	// ------------------------------------------------------------------ tick

	override fun tick() {
		super.tick()

		// Both sides. The client eases toward the temper it was told about, and
		// the server eases toward the same number so nothing here depends on
		// which side is asking.
		val want = when (temper) {
			Temper.CALM -> 0.0f
			Temper.WARY -> WARY_POSE
			Temper.WRATHFUL -> 1.0f
		}
		if (wrathAmount != want) {
			wrathAmount = Mth.clamp(
				wrathAmount + Mth.clamp(want - wrathAmount, -WRATH_EASE, WRATH_EASE),
				0.0f,
				1.0f,
			)
		}

		// And the gait, on the same terms. Eased rather than switched because
		// the two animations are crossfaded by this number: at a hard 0-to-1 the
		// stride would change length between one frame and the next.
		val wantRun = if (entityData.get(DATA_HUNTING)) 1.0f else 0.0f
		if (runAmount != wantRun) {
			runAmount = Mth.clamp(
				runAmount + Mth.clamp(wantRun - runAmount, -RUN_EASE, RUN_EASE),
				0.0f,
				1.0f,
			)
		}
	}

	override fun customServerAiStep(level: ServerLevel) {
		super.customServerAiStep(level)

		if (speakCooldown > 0) speakCooldown--

		// Cools off on her own. Eight minutes from the top of the scale to
		// nothing, which is long enough that a felled tree is not forgotten
		// while the player is still standing in the clearing.
		if (grievance > 0 && tickCount % DECAY_EVERY == 0) {
			val before = temper
			grievance--
			reckon()
			if (temper != before && temper == Temper.CALM) {
				level.getPlayerByUUID(offender ?: NOBODY)?.let { declare(it, Temper.CALM) }
			}
		}

		hunt(level)

		// Read *after* [hunt], and off `target` rather than off the temper.
		//
		// `MeleeAttackGoal` is the only goal that moves her at [WRATH_SPEED] and
		// it only runs when something set a target, so a target is exactly the
		// condition under which she is travelling fast enough to run -- which
		// also covers retaliation, where she is chasing somebody who hit her
		// without being wrathful about it. Taking it from the temper instead
		// would put a merely angry Nymph into a sprint while she pottered about
		// at [WANDER_SPEED].
		//
		// It is read here rather than set inside [hunt] because that method has
		// three early returns, and a flag written down one branch of it is a flag
		// that goes stale down the other two.
		val chasing = target != null
		if (entityData.get(DATA_HUNTING) != chasing) entityData.set(DATA_HUNTING, chasing)

		// A quiet one is worth looking at. Sparse enough not to be a fog: one
		// mote every couple of seconds, which is what makes finding her feel
		// like finding something rather than walking into an effect.
		if (temper == Temper.CALM && tickCount % BLOOM_EVERY == 0) {
			level.sendParticles(
				ParticleTypes.HAPPY_VILLAGER,
				x, y + bbHeight * 0.6, z,
				1, 0.5, 0.5, 0.5, 0.0,
			)
		}
	}

	/**
	 * Who she goes looking for. Ending a fight is [canAttack]'s job, not this one.
	 *
	 * Below wrath she will not seek anybody out, but she will defend herself from
	 * whoever is actually hitting her, so a target that came from
	 * `HurtByTargetGoal` stays and anything else goes.
	 */
	private fun hunt(level: ServerLevel) {
		if (temper != Temper.WRATHFUL) {
			// Retaliation is not the same as wrath, and clearing indiscriminately
			// broke it. `HurtByTargetGoal` sets a target the moment she is
			// struck, and this runs *after* it -- `Mob.serverAiStep` ticks the
			// target selector, then the goal selector, then `customServerAiStep`,
			// which the bytecode confirms. So the two fought each other tick by
			// tick: the goal acquired, this dropped it, the goal acquired again,
			// and what came out was a mob attacking in stutters.
			//
			// **This line cannot stop a fight, and used to be relied on to.**
			// `TargetGoal.canContinueToUse` keeps its own copy of the quarry in
			// `targetMob`, falls back to it whenever the mob's target is null,
			// and then calls `setTarget` again -- every tick, ahead of the goal
			// selector. So a target cleared here is simply restored before
			// anything reads it, and a Nymph who had been struck once went on
			// attacking after she had been calmed all the way down: she said
			// "Very well. Go carefully" and killed the player fifteen seconds
			// later. Seen in a playtest, then found in the bytecode.
			//
			// What ends it is [canAttack], which is the only question the goal
			// actually asks permission on.
			val struckBy = lastHurtByMob
			if (target != null && target !== struckBy) target = null
			return
		}

		val quarry = offender?.let { level.getPlayerByUUID(it) }
		if (quarry == null || !quarry.isAlive || quarry.isCreative || quarry.isSpectator) return
		if (distanceToSqr(quarry) > HUNT_RANGE_SQ) return
		if (target !== quarry) target = quarry
	}

	/**
	 * A calm Nymph does not fight, and this is the only place that can say so.
	 *
	 * Clearing her target does not end a fight -- see [hunt] -- because
	 * `TargetGoal.canContinueToUse` restores it from a copy it holds itself. But
	 * the same method calls `Mob.canAttack` first and returns `false` when it
	 * refuses, which makes the goal *stop*, and `TargetGoal.stop` is what nulls
	 * both the mob's target and the goal's own copy of it. So refusing here is
	 * the difference between a fight that is interrupted every tick and one that
	 * is actually over.
	 *
	 * This is what makes the flowers mean something. Her whole design asks a
	 * player who has angered her to make amends rather than run or kill her, and
	 * that promise is broken if she accepts the apology, says so, and keeps
	 * swinging.
	 *
	 * Being struck again is a fresh grievance worth [STRUCK], which takes her
	 * straight back out of CALM, so this cannot be used to make her a punchbag.
	 */
	override fun canAttack(target: LivingEntity): Boolean =
		temper != Temper.CALM && super.canAttack(target)

	/**
	 * Roots, rather than a bigger hit.
	 *
	 * She has no weapon and it would be wrong to give her one. What a forest does
	 * to somebody it has decided about is hold them there, so the damage is
	 * modest and the slowness is the point -- it is very hard to walk away from
	 * her, which is a far better reason to have made amends earlier than a large
	 * number would be.
	 */
	override fun doHurtTarget(level: ServerLevel, victim: net.minecraft.world.entity.Entity): Boolean {
		val hurt = super.doHurtTarget(level, victim)
		if (hurt && victim is LivingEntity) {
			victim.addEffect(MobEffectInstance(MobEffects.SLOWNESS, ROOT_TICKS, ROOT_LEVEL))
			level.sendParticles(
				ParticleTypes.HAPPY_VILLAGER,
				victim.x, victim.y + 0.1, victim.z,
				6, 0.3, 0.05, 0.3, 0.0,
			)
		}
		return hurt
	}

	/**
	 * Being struck is itself a grievance, and a large one.
	 *
	 * Routed through [wrong] rather than straight to the target, so a player who
	 * takes a swing at a calm Nymph gets the same warning any other trespass
	 * earns. It also means the retaliation stops when the grievance does, instead
	 * of running on its own timer.
	 */
	override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean {
		val hurt = super.hurtServer(level, source, amount)
		if (hurt && isAlive) {
			(source.entity as? Player)?.let { wrong(it, STRUCK) }
		}
		return hurt
	}

	// ------------------------------------------------------------ the player

	override fun mobInteract(player: Player, hand: InteractionHand): InteractionResult {
		val held = player.getItemInHand(hand)

		if (held.`is`(ModTags.NYMPH_OFFERINGS)) return offered(player, hand)

		if (level().isClientSide) return InteractionResult.SUCCESS

		if (speakCooldown > 0) return InteractionResult.CONSUME
		// A refusal is held far longer than a reply. Talking to her is meant to be
		// responsive, but being turned away repeats the same words, and repeating
		// them once a second fills the chat log with somebody saying "Stop."
		speakCooldown = if (temper == Temper.CALM) SPEAK_COOLDOWN else REFUSE_COOLDOWN

		when (temper) {
			// She will not talk to somebody she is currently deciding about.
			Temper.WRATHFUL -> {
				say(player, NymphSpeech.REFUSAL_KEY)
				playSound(ModSounds.NYMPH_WRATH, 0.7f, 1.1f)
			}
			Temper.WARY -> {
				say(player, NymphSpeech.warning(this))
				playSound(ModSounds.NYMPH_WARN, 0.7f, 1.05f)
			}
			Temper.CALM -> {
				// A fresh listener starts the thread again. Two players sharing a
				// world should each get the whole of what she has to say rather
				// than picking up halfway through somebody else's conversation.
				if (lastSpokeTo != player.uuid) {
					lastSpokeTo = player.uuid
					conversation = 0
					lorePos = 0
				}
				val spoken = NymphSpeech.pick(this, player, conversation, lorePos)
				say(player, spoken.key)
				conversation++
				if (spoken.advancedLore) lorePos++
				playSound(ModSounds.NYMPH_SPEAK, 0.7f, 0.95f + random.nextFloat() * 0.1f)
			}
		}

		return InteractionResult.CONSUME
	}

	/**
	 * A flower, held out.
	 *
	 * Two quite different things depending on whether she is owed anything. To
	 * somebody she has a grievance against it is an apology and it works; to
	 * somebody she has none against it is a gift, and she gives something back,
	 * because a mechanic that only exists to undo damage would teach players that
	 * the way to interact with her is to wrong her first.
	 */
	private fun offered(player: Player, hand: InteractionHand): InteractionResult {
		if (level().isClientSide) return InteractionResult.SUCCESS
		val level = level() as? ServerLevel ?: return InteractionResult.CONSUME

		val held = player.getItemInHand(hand)
		val before = temper
		// Asked *before* the flower is counted, and asked of the temper rather
		// than of the number. See [forgive]: the grievance can be nonzero while
		// she is perfectly calm, and in that band she was telling players they
		// still owed her a moment after telling them they did not.
		val owed = before != Temper.CALM

		forgive(AMENDS)
		if (!player.hasInfiniteMaterials()) held.shrink(1)

		if (owed) {
			playSound(ModSounds.NYMPH_GIFT, 0.9f, 1.0f)
		} else {
			player.addEffect(MobEffectInstance(MobEffects.REGENERATION, BLESSING_TICKS, 0))
			playSound(ModSounds.NYMPH_GIFT, 0.9f, 1.15f)
		}

		// The sound and the motes fire for every flower, because they are the
		// confirmation that it was taken and holding them back reads as the
		// offering failing. The *line* does not: handed a stack, she repeated
		// herself eleven times in nine seconds, which is the same way the
		// refusal line became wallpaper before [REFUSE_COOLDOWN] was added.
		//
		// A temper change is news and jumps the queue -- it can only happen a
		// few times, and it is the one thing the player needs told.
		if (temper != before) {
			declare(player, temper)
		} else if (speakCooldown <= 0) {
			speakCooldown = GIFT_COOLDOWN
			say(player, if (owed) NymphSpeech.AMENDS_KEY else NymphSpeech.GIFT_KEY)
		}

		level.sendParticles(
			ParticleTypes.HAPPY_VILLAGER,
			x, y + bbHeight * 0.7, z,
			10, 0.4, 0.4, 0.4, 0.0,
		)
		return InteractionResult.CONSUME
	}

	/**
	 * Speech goes to chat, not to the action bar.
	 *
	 * The mod's other messages -- an Epitaph's name, a charm waking -- go to the
	 * action bar via `sendOverlayMessage`, and copying that here would be wrong
	 * for the only reason that matters: those are *labels* and these are
	 * *sentences*. "The last of them buried everyone else and began a stone for
	 * themselves. They did not finish it. There was no one left to." is a line
	 * and a half of text that fades after a couple of seconds and cannot be
	 * scrolled back to. Chat holds it, and holds the one before it, which is
	 * what makes a thread advancing over several conversations legible at all.
	 *
	 * `sendSystemMessage` is the chat route; `sendOverlayMessage` is the action
	 * bar. (`displayClientMessage` does not exist in 26.2.)
	 */
	private fun say(to: Player, key: String) {
		// Only if she is close enough to be heard.
		//
		// Everything else here finds the player by UUID, which reaches them
		// anywhere in the world. A Nymph cooling off four minutes after a fight
		// duly messaged a player who was by then talking to a *different* Nymph,
		// and since a chat line carries no speaker it read as a bug rather than
		// as somebody several hundred blocks away relenting.
		if (distanceToSqr(to) > EARSHOT_SQ) return

		to.sendSystemMessage(
			Component.translatable(key).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC),
		)
	}

	override fun getAmbientSound(): SoundEvent = ModSounds.NYMPH_AMBIENT

	override fun getAmbientSoundInterval(): Int = AMBIENT_INTERVAL

	override fun getHurtSound(source: DamageSource): SoundEvent = ModSounds.NYMPH_HURT

	override fun getDeathSound(): SoundEvent = ModSounds.NYMPH_DEATH

	/**
	 * Nothing. See the class docs: the moment she is worth killing, she is.
	 *
	 * `shouldDropLoot` rather than an empty loot table, so the kill still yields
	 * the ordinary experience -- the bytecode of `LivingEntity.dropAllDeathLoot`
	 * shows this gates `dropFromLootTable` and `dropCustomDeathLoot` while leaving
	 * `dropExperience` alone.
	 */
	override fun shouldDropLoot(level: ServerLevel): Boolean = false

	override fun addAdditionalSaveData(output: ValueOutput) {
		super.addAdditionalSaveData(output)
		output.putInt(TAG_GRIEVANCE, grievance)
		offender?.let { output.putString(TAG_OFFENDER, it.toString()) }
	}

	override fun readAdditionalSaveData(input: ValueInput) {
		super.readAdditionalSaveData(input)
		grievance = Mth.clamp(input.getIntOr(TAG_GRIEVANCE, 0), 0, GRIEVANCE_MAX)
		offender = input.getString(TAG_OFFENDER).map {
			runCatching { UUID.fromString(it) }.getOrNull()
		}.orElse(null)
		reckon()
		// Arrives already in the pose the temper calls for, rather than easing
		// into it over the second after the chunk loads.
		wrathAmount = when (temper) {
			Temper.CALM -> 0.0f
			Temper.WARY -> WARY_POSE
			Temper.WRATHFUL -> 1.0f
		}
		// A reloaded Nymph has no target yet -- the goals have not run -- so she
		// starts walking and picks the run back up on the tick she reacquires.
		runAmount = 0.0f
	}

	companion object {
		private const val TAG_GRIEVANCE = "Grievance"
		private const val TAG_OFFENDER = "Offender"

		private val TEMPERS = Temper.entries

		private val DATA_TEMPER: EntityDataAccessor<Int> =
			SynchedEntityData.defineId(Nymph::class.java, EntityDataSerializers.INT)

		/** Whether she is closing on somebody, which is what decides her gait. */
		private val DATA_HUNTING: EntityDataAccessor<Boolean> =
			SynchedEntityData.defineId(Nymph::class.java, EntityDataSerializers.BOOLEAN)

		private val NOBODY: UUID = UUID(0L, 0L)

		const val GRIEVANCE_MAX: Int = 100

		/**
		 * Where the two lines fall.
		 *
		 * Read them against what [GroveWatch] charges and the shape is the design:
		 * one flower picked is nothing, four is a warning; two logs is a warning;
		 * a killed animal is a warning on its own and two is a fight. Somebody
		 * passing through a flower forest gathering a bouquet never meets the
		 * angry version of her, and somebody felling the wood she stands in meets
		 * it almost immediately.
		 */
		const val WARY_AT: Int = 20
		const val WRATH_AT: Int = 55

		/**
		 * How far the grievance must fall before she comes back down a step.
		 *
		 * The hysteresis in [reckon]. Twelve points at one point per five seconds
		 * is a minute of holding a temper she would otherwise drop the instant she
		 * reached it -- and in practice much longer, because she arrives above the
		 * line rather than exactly on it.
		 */
		private const val RELENT = 12

		/** One point every five seconds -- eight minutes from the top. */
		private const val DECAY_EVERY = 100

		/** What one flower buys back. Two undo a felled tree. */
		private const val AMENDS = 15

		/** Taking a swing at her. Enough on its own to be warned, not to be hunted. */
		private const val STRUCK = 26

		private const val SPEAK_COOLDOWN = 20
		private const val REFUSE_COOLDOWN = 100

		/**
		 * Two seconds between remarks about a flower.
		 *
		 * Longer than [SPEAK_COOLDOWN], because a conversation is a player
		 * choosing to hear the next thing and a fistful of poppies is not.
		 */
		private const val GIFT_COOLDOWN = 40

		/** Thirty two blocks. Past that she lets you go rather than following. */
		private const val HUNT_RANGE_SQ = 32.0 * 32.0

		/**
		 * How far a thing she says carries. Twenty four blocks.
		 *
		 * The same span [GroveWatch] watches over, so she can be heard by anyone
		 * close enough to have angered her.
		 */
		private const val EARSHOT_SQ = 24.0 * 24.0

		private const val WRATH_EASE = 0.045f
		private const val WARY_POSE = 0.35f

		/** Eight ticks to change gait. Slower reads as a stumble, faster as a cut. */
		private const val RUN_EASE = 0.125f

		private const val BLOOM_EVERY = 40
		private const val AMBIENT_INTERVAL = 400

		private const val ROOT_TICKS = 100
		/** Slowness III. Enough to make walking away a decision rather than a reflex. */
		private const val ROOT_LEVEL = 2

		private const val BLESSING_TICKS = 200

		private const val WANDER_SPEED = 0.7
		private const val WRATH_SPEED = 1.15
		private const val WATCH_RANGE = 12.0f

		/**
		 * Sturdy, and slow to close.
		 *
		 * Deliberately not a boss. She has more health than a Remnant and hits
		 * about as hard, and what actually makes her dangerous is [ROOT_LEVEL] --
		 * a fight with her is one you struggle to leave, which is the right shape
		 * for something whose entire design is asking you not to start it.
		 */
		fun createAttributes(): AttributeSupplier.Builder = createMobAttributes()
			.add(Attributes.MAX_HEALTH, 50.0)
			.add(Attributes.MOVEMENT_SPEED, 0.28)
			.add(Attributes.ATTACK_DAMAGE, 6.0)
			.add(Attributes.ARMOR, 4.0)
			.add(Attributes.FOLLOW_RANGE, 32.0)
			.add(Attributes.STEP_HEIGHT, 1.0)
	}
}
