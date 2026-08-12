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
	 * Putting a hand to a mural and the carving answering.
	 *
	 * Quiet on purpose. It plays every time a passage is opened, including the
	 * eighth re-read, so anything with a swell to it would wear through fast.
	 */
	val MURAL_READ: SoundEvent = register("block.mural.read")

	/**
	 * Turning a page in the mural screen.
	 *
	 * Played client-side straight into the UI channel rather than at the block:
	 * the sound belongs to the interface, not the world, so it should not fall
	 * off with distance or be audible to anyone else standing in the room.
	 */
	val MURAL_TURN: SoundEvent = register("block.mural.turn")

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

	/**
	 * The Binding Altar answering a hand on it.
	 *
	 * Deliberately not a stone sound. The first pair generated were a slab
	 * grinding and a chain snapping taut, and they were wrong for the same
	 * reason the arrow was: what happens at this block is not mechanical. It is
	 * a memory being bound, so the altar sounds like something waking rather
	 * than something opening.
	 */
	val BINDING_ALTAR_OPEN: SoundEvent = register("block.binding_altar.open")

	/** A binding taking hold, played when the deeper charm is lifted off. */
	val BINDING_ALTAR_BIND: SoundEvent = register("block.binding_altar.bind")

	/**
	 * The Nymph, who is the only thing in the mod that is not dead.
	 *
	 * So none of these may be the voice work the Remnant and the Sentinel use --
	 * that whole set is built to sound like something that used to be alive, and
	 * borrowing it here would file her with them. Hers are green and small: the
	 * grass and the leaves, played at pitches that read as speech.
	 */
	val NYMPH_AMBIENT: SoundEvent = register("entity.nymph.ambient")
	val NYMPH_SPEAK: SoundEvent = register("entity.nymph.speak")

	/** Being told once, and then not being told again. */
	val NYMPH_WARN: SoundEvent = register("entity.nymph.warn")
	val NYMPH_WRATH: SoundEvent = register("entity.nymph.wrath")

	/** A flower accepted, and whatever she gives back for it. */
	val NYMPH_GIFT: SoundEvent = register("entity.nymph.gift")

	val NYMPH_HURT: SoundEvent = register("entity.nymph.hurt")
	val NYMPH_DEATH: SoundEvent = register("entity.nymph.death")

	private fun register(path: String): SoundEvent {
		val id = VanguardSpirits.id(path)
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id))
	}

	/** Touching the object is what actually runs the registrations above. */
	fun register() = Unit
}
