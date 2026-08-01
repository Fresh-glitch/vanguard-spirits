package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.entity.Mourner
import io.github.freshglitch.vanguardspirits.entity.Remnant
import io.github.freshglitch.vanguardspirits.entity.StoneSentinel
// `object` is a Kotlin keyword, so this package path only imports with backticks.
import net.fabricmc.fabric.api.`object`.builder.v1.entity.FabricDefaultAttributeRegistry
import net.minecraft.core.Registry
import net.minecraft.world.entity.Entity
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory

object ModEntities {

	/**
	 * The Guarded Ruins' guardian.
	 *
	 * Sized a little narrower than the model's pauldrons, which stick out to 1.75
	 * blocks -- matching them exactly would make it impossible to walk past one
	 * in the sanctum.
	 */
	val STONE_SENTINEL: EntityType<StoneSentinel> = register("stone_sentinel") { key ->
		EntityType.Builder.of(::StoneSentinel, MobCategory.MONSTER)
			.sized(1.4f, 2.6f)
			.eyeHeight(2.2f)
			.fireImmune()
			.clientTrackingRange(12)
			.build(key)
	}

	/**
	 * What is left of the people who lived here.
	 *
	 * Narrow and only slightly taller than a player, so unlike the sentinel it
	 * fits anywhere the player does -- including the crypt stair and the rooms
	 * below it. There is nowhere down there it cannot follow.
	 */
	val REMNANT: EntityType<Remnant> = register("remnant") { key ->
		EntityType.Builder.of(::Remnant, MobCategory.MONSTER)
			.sized(0.6f, 1.95f)
			.eyeHeight(1.74f)
			.clientTrackingRange(8)
			.build(key)
	}

	/**
	 * The sign that a ruin is below.
	 *
	 * Ambient rather than a monster, so it never counts against the hostile mob
	 * cap and never stops anyone sleeping. Tracked from far away on purpose --
	 * a landmark that only renders once you are on top of it is useless.
	 */
	val MOURNER: EntityType<Mourner> = register("mourner") { key ->
		EntityType.Builder.of(::Mourner, MobCategory.AMBIENT)
			.sized(1.2f, 0.7f)
			.eyeHeight(0.5f)
			.clientTrackingRange(16)
			.build(key)
	}

	private fun <T : Entity> register(
		path: String,
		factory: (ResourceKey<EntityType<*>>) -> EntityType<T>,
	): EntityType<T> {
		val key = ResourceKey.create(Registries.ENTITY_TYPE, VanguardSpirits.id(path))
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, factory(key))
	}

	/** Touching the object is what actually runs the registrations above. */
	fun register() {
		FabricDefaultAttributeRegistry.register(STONE_SENTINEL, StoneSentinel.createAttributes())
		FabricDefaultAttributeRegistry.register(REMNANT, Remnant.createAttributes())
		FabricDefaultAttributeRegistry.register(MOURNER, Mourner.createAttributes())
	}
}
