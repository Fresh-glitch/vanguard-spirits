package io.github.freshglitch.vanguardspirits.worldgen

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.advancements.predicates.ContextAwarePredicate
import net.minecraft.advancements.predicates.entity.EntityPredicate
import net.minecraft.advancements.triggers.SimpleCriterionTrigger
import net.minecraft.server.level.ServerPlayer
import java.util.Optional

/**
 * Fires the moment a ruin is left hollowed.
 *
 * Nothing vanilla can watch this: it is a chunk attachment set when the last
 * item leaves a Reliquary, and neither half of that -- the emptiness or the
 * ruin -- is a thing an item or location predicate can ask about.
 *
 * No payload. The only question is whether it happened, and unlike the mural
 * and attunement triggers there is no scale to count along: a ruin is hollowed
 * or it is not, and hollowing a second one is the same event again.
 */
class HollowTrigger : SimpleCriterionTrigger<HollowTrigger.TriggerInstance>() {

	override fun codec(): Codec<TriggerInstance> = TriggerInstance.CODEC

	/** Called from the Reliquary, for whoever is standing in the vault. */
	fun fire(player: ServerPlayer) {
		trigger(player) { true }
	}

	class TriggerInstance(
		private val who: Optional<ContextAwarePredicate>,
	) : SimpleCriterionTrigger.SimpleInstance {

		override fun player(): Optional<ContextAwarePredicate> = who

		companion object {
			val CODEC: Codec<TriggerInstance> = RecordCodecBuilder.create { instance ->
				instance.group(
					EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player")
						.forGetter(TriggerInstance::player),
				).apply(instance, ::TriggerInstance)
			}
		}
	}
}
