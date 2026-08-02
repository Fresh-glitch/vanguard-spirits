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

	/**
	 * Set on an Echo of Kinship the moment a Sentinel gives it up.
	 *
	 * Only one that came off a guardian hangs in the air; one a player throws on
	 * the floor is just an item, and should land like one. The distinction has
	 * to live on the stack because the loot table is where it is decided, and it
	 * is stripped again the moment anyone picks it up -- see
	 * [io.github.freshglitch.vanguardspirits.item.EchoOfKinshipItem.inventoryTick].
	 */
	val KINSHIP_FREED: DataComponentType<Boolean> =
		Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			VanguardSpirits.id("kinship_freed"),
			DataComponentType.Builder<Boolean>()
				.persistent(Codec.BOOL)
				.networkSynchronized(ByteBufCodecs.BOOL)
				.build(),
		)

	/** Touching the object is what runs the registration above. */
	fun register() = Unit
}
