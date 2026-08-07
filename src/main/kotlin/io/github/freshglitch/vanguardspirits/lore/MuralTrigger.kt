package io.github.freshglitch.vanguardspirits.lore

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.advancements.predicates.ContextAwarePredicate
import net.minecraft.advancements.predicates.entity.EntityPredicate
import net.minecraft.advancements.triggers.SimpleCriterionTrigger
import net.minecraft.server.level.ServerPlayer
import java.util.Optional

/**
 * Fires when a player reads a mural passage they had not read before.
 *
 * Same shape as [AttunementTrigger][
 * io.github.freshglitch.vanguardspirits.charm.AttunementTrigger], and for the
 * same reason: how much of the ruin's story a player has found lives in a data
 * attachment, so nothing vanilla can watch it.
 *
 * The instance carries a *minimum* count rather than an exact one, so a single
 * trigger serves both "read your first" and "read all eight" -- and a player
 * who somehow arrives at eight without the tree seeing seven still qualifies.
 */
class MuralTrigger : SimpleCriterionTrigger<MuralTrigger.TriggerInstance>() {

	override fun codec(): Codec<TriggerInstance> = TriggerInstance.CODEC

	/** Called from [MuralCodex.markRead], with the total the player now holds. */
	fun fire(player: ServerPlayer, read: Int) {
		trigger(player) { instance -> instance.matches(read) }
	}

	/**
	 * Not a `data class`: the interface requires a `player()` method, and a
	 * constructor property of that name would generate `getPlayer()` beside it
	 * for no reason. [who] keeps the two apart.
	 */
	class TriggerInstance(
		private val who: Optional<ContextAwarePredicate>,
		val read: Int,
	) : SimpleCriterionTrigger.SimpleInstance {

		override fun player(): Optional<ContextAwarePredicate> = who

		fun matches(value: Int): Boolean = value >= read

		companion object {
			val CODEC: Codec<TriggerInstance> = RecordCodecBuilder.create { instance ->
				instance.group(
					EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player")
						.forGetter(TriggerInstance::player),
					Codec.INT.optionalFieldOf("read", 1)
						.forGetter(TriggerInstance::read),
				).apply(instance, ::TriggerInstance)
			}
		}
	}
}
