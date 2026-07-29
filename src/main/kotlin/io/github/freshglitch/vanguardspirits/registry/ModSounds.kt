package io.github.freshglitch.vanguardspirits.registry

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.sounds.SoundEvent

/**
 * Sound events owned by this mod.
 *
 * The event ids are ours, but what they *play* is decided in `sounds.json`.
 * That indirection is the point: swapping in bespoke audio later is an edit to
 * one resource file, with no code change anywhere.
 */
object ModSounds {
	val GOLDEN_CHEST_OPEN: SoundEvent = register("block.golden_chest.open")
	val GOLDEN_CHEST_CLOSE: SoundEvent = register("block.golden_chest.close")

	/** The waking sequence, in the order it is heard. */
	val SENTINEL_STIR: SoundEvent = register("entity.stone_sentinel.stir")
	val SENTINEL_RUMBLE: SoundEvent = register("entity.stone_sentinel.rumble")
	val SENTINEL_ROAR: SoundEvent = register("entity.stone_sentinel.roar")
	val SENTINEL_BELLOW: SoundEvent = register("entity.stone_sentinel.bellow")

	val SENTINEL_STEP: SoundEvent = register("entity.stone_sentinel.step")
	val SENTINEL_SLAM: SoundEvent = register("entity.stone_sentinel.slam")
	val SENTINEL_SWEEP: SoundEvent = register("entity.stone_sentinel.sweep")
	val SENTINEL_HURT: SoundEvent = register("entity.stone_sentinel.hurt")
	val SENTINEL_DEATH: SoundEvent = register("entity.stone_sentinel.death")

	private fun register(path: String): SoundEvent {
		val id = VanguardSpirits.id(path)
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id))
	}

	/** Touching the object is what actually runs the registrations above. */
	fun register() = Unit
}
