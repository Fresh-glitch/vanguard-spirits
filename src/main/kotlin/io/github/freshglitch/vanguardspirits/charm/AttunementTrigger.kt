package io.github.freshglitch.vanguardspirits.charm

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.advancements.predicates.ContextAwarePredicate
import net.minecraft.advancements.predicates.entity.EntityPredicate
import net.minecraft.advancements.triggers.SimpleCriterionTrigger
import net.minecraft.server.level.ServerPlayer
import java.util.Optional

/**
 * Fires when a player's attunement goes up.
 *
 * Attunement is the mod's signature system and the only part of it a player
 * cannot be told about by holding an item: it lives in a data attachment, so no
 * vanilla criterion can see it. Without this the deepest thing in the mod is the
 * one thing the advancement tree cannot mention.
 *
 * The instance carries a *minimum* rather than an exact value, so one trigger
 * serves any threshold an advancement wants to ask about -- and a player who
 * somehow arrives at four without passing through three still qualifies.
 */
class AttunementTrigger : SimpleCriterionTrigger<AttunementTrigger.TriggerInstance>() {

	override fun codec(): Codec<TriggerInstance> = TriggerInstance.CODEC

	/** Called from [Attunement.raise], with the value the player now holds. */
	fun fire(player: ServerPlayer, attunement: Int) {
		trigger(player) { instance -> instance.matches(attunement) }
	}

	/**
	 * Not a `data class` on purpose: the interface requires a `player()` method,
	 * and a constructor property of that name would generate `getPlayer()`
	 * alongside it for no reason. [who] keeps the two apart.
	 */
	class TriggerInstance(
		private val who: Optional<ContextAwarePredicate>,
		val attunement: Int,
	) : SimpleCriterionTrigger.SimpleInstance {

		override fun player(): Optional<ContextAwarePredicate> = who

		fun matches(value: Int): Boolean = value >= attunement

		companion object {
			val CODEC: Codec<TriggerInstance> = RecordCodecBuilder.create { instance ->
				instance.group(
					EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player")
						.forGetter(TriggerInstance::player),
					Codec.INT.optionalFieldOf("attunement", Attunement.BASE)
						.forGetter(TriggerInstance::attunement),
				).apply(instance, ::TriggerInstance)
			}
		}
	}
}
