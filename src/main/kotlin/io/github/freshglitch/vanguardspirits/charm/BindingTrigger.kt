package io.github.freshglitch.vanguardspirits.charm

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.advancements.predicates.ContextAwarePredicate
import net.minecraft.advancements.predicates.entity.EntityPredicate
import net.minecraft.advancements.triggers.SimpleCriterionTrigger
import net.minecraft.server.level.ServerPlayer
import java.util.Optional

/**
 * Fires when a player takes a charm off a Binding Altar.
 *
 * A custom trigger rather than `inventory_changed` with an item predicate,
 * because the thing being asked about is a component *value* -- depth two or
 * better -- and item predicates match components exactly. Two criteria joined by
 * an OR would work for three depths and would need revisiting the moment a
 * fourth existed.
 *
 * The instance carries a minimum, so one trigger serves both "bind a charm at
 * all" and "take one as deep as it goes", the same way [MuralTrigger][
 * io.github.freshglitch.vanguardspirits.lore.MuralTrigger] does.
 */
class BindingTrigger : SimpleCriterionTrigger<BindingTrigger.TriggerInstance>() {

	override fun codec(): Codec<TriggerInstance> = TriggerInstance.CODEC

	/** Called from the altar, with the depth the charm has just reached. */
	fun fire(player: ServerPlayer, depth: Int) {
		trigger(player) { instance -> instance.matches(depth) }
	}

	/**
	 * Not a `data class`: the interface requires a `player()` method, and a
	 * constructor property of that name would generate `getPlayer()` beside it
	 * for no reason. [who] keeps the two apart.
	 */
	class TriggerInstance(
		private val who: Optional<ContextAwarePredicate>,
		val depth: Int,
	) : SimpleCriterionTrigger.SimpleInstance {

		override fun player(): Optional<ContextAwarePredicate> = who

		fun matches(value: Int): Boolean = value >= depth

		companion object {
			val CODEC: Codec<TriggerInstance> = RecordCodecBuilder.create { instance ->
				instance.group(
					EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player")
						.forGetter(TriggerInstance::player),
					// Two is the shallowest a binding can produce: depth one is
					// every charm that has never been to an altar.
					Codec.INT.optionalFieldOf("depth", 2)
						.forGetter(TriggerInstance::depth),
				).apply(instance, ::TriggerInstance)
			}
		}
	}
}
