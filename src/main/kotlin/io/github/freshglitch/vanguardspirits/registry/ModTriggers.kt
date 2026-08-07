package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.charm.AttunementTrigger
import io.github.freshglitch.vanguardspirits.lore.MuralTrigger
import net.minecraft.advancements.triggers.Criterion
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import java.util.Optional

/**
 * Criterion triggers the mod fires itself.
 *
 * Everything else the tree asks about -- items held, mobs killed, standing
 * inside a structure -- vanilla already watches. Both of these are exceptions
 * for the same reason: attunement and the mural codex are data attachments, and
 * nothing in the game looks at either but us.
 *
 * Registered into `BuiltInRegistries.TRIGGER_TYPES` like any other content, so
 * the id has to exist before a datapack referencing it loads. Common source set,
 * because the server is what fires it.
 */
object ModTriggers {

	val ATTUNEMENT: AttunementTrigger = Registry.register(
		BuiltInRegistries.TRIGGER_TYPES,
		VanguardSpirits.id("attunement"),
		AttunementTrigger(),
	)

	val MURALS: MuralTrigger = Registry.register(
		BuiltInRegistries.TRIGGER_TYPES,
		VanguardSpirits.id("murals_read"),
		MuralTrigger(),
	)

	/**
	 * A criterion satisfied once the player's attunement reaches [level].
	 *
	 * Lives here rather than in the datagen provider so the advancement and the
	 * firing site are built from the same trigger instance -- a criterion naming
	 * a trigger that was never registered generates perfectly happily and then
	 * never fires.
	 */
	fun attunementReached(level: Int): Criterion<AttunementTrigger.TriggerInstance> =
		ATTUNEMENT.createCriterion(
			AttunementTrigger.TriggerInstance(Optional.empty(), level),
		)

	/** A criterion satisfied once the player has read [count] distinct passages. */
	fun muralsRead(count: Int): Criterion<MuralTrigger.TriggerInstance> =
		MURALS.createCriterion(
			MuralTrigger.TriggerInstance(Optional.empty(), count),
		)

	/** Touching the object is what actually runs the registration above. */
	fun register() = Unit
}
