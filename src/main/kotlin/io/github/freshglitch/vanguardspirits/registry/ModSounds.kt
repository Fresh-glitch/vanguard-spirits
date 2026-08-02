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

	/** Turning away someone who has not earned it: the runes gathering, then the ward. */
	val RELIQUARY_REJECT: SoundEvent = register("block.golden_chest.reject")
	val RELIQUARY_WARD: SoundEvent = register("block.golden_chest.ward")

	/**
	 * A grave mound coming open, and whoever was under it arriving.
	 *
	 * Its own event rather than the Remnant's notice cry, because the two land on
	 * different frames and mean different things: this is the earth giving way,
	 * and the cry follows once it has seen you.
	 */
	val GRAVE_DISTURB: SoundEvent = register("block.grave.disturb")

	/**
	 * Switching a charm off and on again.
	 *
	 * [CHARM_WAKE] is the note the Echo of Kinship uses, and [CHARM_HUSH] is
	 * that same file played backwards -- not a different sound chosen to feel
	 * opposite, but literally the inverse, so the pair reads as one action
	 * undoing the other.
	 */
	val CHARM_WAKE: SoundEvent = register("item.charm.wake")
	val CHARM_HUSH: SoundEvent = register("item.charm.hush")

	/** A ward turning a shot aside. Ours, not a shield. */
	val CHARM_DEFLECT: SoundEvent = register("item.charm.deflect")

	/** An Echo of Kinship coming loose, heard once as it is dropped. */
	val KINSHIP_RELEASE: SoundEvent = register("item.echo_of_kinship.release")

	/** And the answer, once somebody finally takes it. */
	val KINSHIP_CLAIM: SoundEvent = register("item.echo_of_kinship.claim")

	/**
	 * The Remnant's voice.
	 *
	 * These are the only sounds in the mod that are not vanilla samples: the
	 * `.ogg` files are synthesised from a source-filter voice model, which is
	 * what makes them read as something that used to speak.
	 */
	/** Heard well before the bird is seen, which is rather the point. */
	val MOURNER_CALL: SoundEvent = register("entity.mourner.call")
	val MOURNER_HURT: SoundEvent = register("entity.mourner.hurt")

	val REMNANT_RASP: SoundEvent = register("entity.remnant.rasp")
	val REMNANT_NOTICE: SoundEvent = register("entity.remnant.notice")
	val REMNANT_HURT: SoundEvent = register("entity.remnant.hurt")
	val REMNANT_DEATH: SoundEvent = register("entity.remnant.death")
	val REMNANT_STEP: SoundEvent = register("entity.remnant.step")

	/** The waking sequence, in the order it is heard. */
	val SENTINEL_STIR: SoundEvent = register("entity.stone_sentinel.stir")
	val SENTINEL_RUMBLE: SoundEvent = register("entity.stone_sentinel.rumble")
	val SENTINEL_ROAR: SoundEvent = register("entity.stone_sentinel.roar")
	val SENTINEL_BELLOW: SoundEvent = register("entity.stone_sentinel.bellow")

	val SENTINEL_STEP: SoundEvent = register("entity.stone_sentinel.step")
	val SENTINEL_SLAM: SoundEvent = register("entity.stone_sentinel.slam")
	val SENTINEL_SWEEP: SoundEvent = register("entity.stone_sentinel.sweep")
	/** The answer to being shot at from somewhere it cannot climb. */
	val SENTINEL_REACH: SoundEvent = register("entity.stone_sentinel.reach")
	val SENTINEL_RECKONING: SoundEvent = register("entity.stone_sentinel.reckoning")

	/** The answer to being walled in: the arm drawing back, then the wall going. */
	val SENTINEL_BRACE: SoundEvent = register("entity.stone_sentinel.brace")
	val SENTINEL_SUNDER: SoundEvent = register("entity.stone_sentinel.sunder")

	/** The answer to being kited: a spin you can hear coming across a room. */
	val SENTINEL_WIND: SoundEvent = register("entity.stone_sentinel.wind")

	/** The answer to being shot at from out of reach: shutting itself away, and mending. */
	val SENTINEL_SHELL: SoundEvent = register("entity.stone_sentinel.shell")
	val SENTINEL_MEND: SoundEvent = register("entity.stone_sentinel.mend")

	val SENTINEL_HURT: SoundEvent = register("entity.stone_sentinel.hurt")
	val SENTINEL_DEATH: SoundEvent = register("entity.stone_sentinel.death")

	private fun register(path: String): SoundEvent {
		val id = VanguardSpirits.id(path)
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id))
	}

	/** Touching the object is what actually runs the registrations above. */
	fun register() = Unit
}
