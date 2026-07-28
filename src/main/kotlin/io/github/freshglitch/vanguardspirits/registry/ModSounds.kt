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

	private fun register(path: String): SoundEvent {
		val id = VanguardSpirits.id(path)
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id))
	}

	/** Touching the object is what actually runs the registrations above. */
	fun register() = Unit
}
