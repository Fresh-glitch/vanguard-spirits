package io.github.freshglitch.vanguardspirits.registry

import com.mojang.serialization.Codec
import io.github.freshglitch.vanguardspirits.VanguardSpirits
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.codec.ByteBufCodecs

/**
 * Data components this mod puts on item stacks.
 */
object ModComponents {

	/**
	 * Set on a charm its holder has deliberately switched off.
	 *
	 * On the stack rather than on the player, so the decision travels with the
	 * charm: hand one to somebody and it stays as you left it, and two copies of
	 * the same charm can be in different states.
	 *
	 * Network synchronised because the client draws the tooltip from it, and
	 * persistent so it survives the world being closed.
	 */
	val CHARM_HUSHED: DataComponentType<Boolean> =
		Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			VanguardSpirits.id("charm_hushed"),
			DataComponentType.Builder<Boolean>()
				.persistent(Codec.BOOL)
				.networkSynchronized(ByteBufCodecs.BOOL)
				.build(),
		)

	/** Touching the object is what runs the registration above. */
	fun register() = Unit
}
