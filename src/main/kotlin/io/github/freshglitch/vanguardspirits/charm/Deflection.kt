package io.github.freshglitch.vanguardspirits.charm

import io.github.freshglitch.vanguardspirits.registry.ModEffects
import io.github.freshglitch.vanguardspirits.registry.ModParticles
import io.github.freshglitch.vanguardspirits.registry.ModSounds
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.util.Mth
import net.minecraft.world.entity.EntityReference
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.ProjectileDeflection
import net.minecraft.world.phys.Vec3
import java.util.UUID

/**
 * Turns projectiles around on anyone holding [ModEffects.DEFLECTION].
 *
 * **Caught before contact, not after damage.** The first version answered the
 * damage event, which works for an arrow and cannot work for a ghast fireball
 * at all: that one calls `Level.explode` and then `discard`, so by the time
 * anything is hurt the projectile no longer exists to send anywhere. Waiting for
 * damage also ties the whole feature to whether a given projectile happens to
 * name itself as the direct entity of its damage source, which varies.
 *
 * So each tick every guard looks a tick ahead instead: any projectile whose
 * next move would cross them is turned now, before it arrives. That is one rule
 * for arrows, tridents, fireballs and anything a mod adds, and nothing has to
 * be hurt for it to work.
 *
 * The damage hook stays as a backstop for the cases prediction cannot see -- a
 * projectile spawned already inside someone, most obviously -- where it refuses
 * the damage even though there is nothing left worth throwing back.
 */
object Deflection {

	/**
	 * Bolts turned recently, by UUID and the tick it happened.
	 *
	 * A turned bolt belongs to the guard, and vanilla lets a projectile strike
	 * its owner once it has left them -- which this one has, from its first
	 * shooter. Without this, a bolt turned into a wall a step away comes
	 * straight back into the person who turned it.
	 *
	 * Keyed by UUID rather than entity id because ids are recycled, and a fresh
	 * projectile inheriting a number still inside the grace window would be
	 * silently swallowed.
	 */
	private val recent = HashMap<UUID, Long>()

	fun register() {
		ServerTickEvents.END_SERVER_TICK.register { server ->
			for (level in server.allLevels) {
				val now = level.gameTime
				for (player in level.players()) {
					if (player.hasEffect(ModEffects.DEFLECTION)) intercept(level, player, now)
				}
			}

			val clock = server.overworld().gameTime
			recent.entries.removeIf { clock - it.value >= GRACE_TICKS }
		}

		// Backstop only. Anything that reaches this has already got past the
		// sweep above, so there is nothing to aim anywhere -- but it still must
		// not land.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register { victim, source, _ ->
			val bolt = source.directEntity as? Projectile ?: return@register true
			if (!victim.hasEffect(ModEffects.DEFLECTION)) return@register true
			if (bolt.owner === victim) return@register true
			false
		}
	}

	/**
	 * Looks one tick ahead and turns anything on its way in.
	 *
	 * Run at the end of the tick, after everything has moved, so the segment
	 * tested is exactly the move the projectile is about to make. The guard's
	 * box is inflated a little because vanilla widens a target by the
	 * projectile's own size when it tests the same thing, and a graze that
	 * would have clipped a shoulder should ring the ward too.
	 */
	private fun intercept(level: ServerLevel, guard: LivingEntity, now: Long) {
		val hunt = guard.boundingBox.inflate(SCAN_REACH)
		val target = guard.boundingBox.inflate(GRAZE)

		for (bolt in level.getEntitiesOfClass(Projectile::class.java, hunt)) {
			if (bolt.owner === guard) continue

			val sent = recent[bolt.uuid]
			if (sent != null && now - sent < GRACE_TICKS) continue

			val travel = bolt.deltaMovement
			if (travel.lengthSqr() < 1.0e-6) continue

			val from = bolt.position()
			val struck = target.clip(from, from.add(travel)).orElse(null) ?: continue

			turn(level, guard, bolt, struck, travel.length(), now)
		}
	}

	/**
	 * Sends one bolt back where it came from.
	 *
	 * Routed through vanilla's own `deflect` rather than by assigning a velocity,
	 * because that is what tells a hurting projectile to rescale the
	 * acceleration it flies under -- a fireball given only a new delta would
	 * turn and then accelerate itself straight back round.
	 */
	private fun turn(
		level: ServerLevel,
		guard: LivingEntity,
		bolt: Projectile,
		struck: Vec3,
		speed: Double,
		now: Long,
	) {
		val shooter = bolt.owner

		// Back at whoever loosed it, or simply back the way it came if that is
		// no longer anybody -- a dispenser, or a shooter since gone.
		val aim = if (shooter != null) {
			shooter.position().add(0.0, shooter.bbHeight * 0.5, 0.0).subtract(bolt.position()).normalize()
		} else {
			bolt.deltaMovement.normalize().scale(-1.0)
		}

		bolt.deflect(ProjectileDeflection.NONE, guard, EntityReference.of(guard), true)

		// Clear of the guard first, or it strikes them on the way out now that
		// they are no longer its owner.
		bolt.snapTo(
			bolt.x + aim.x * CLEARANCE,
			bolt.y + aim.y * CLEARANCE,
			bolt.z + aim.z * CLEARANCE,
			bolt.yRot,
			bolt.xRot,
		)
		bolt.deltaMovement = aim.scale(speed.coerceAtLeast(MIN_SPEED))
		bolt.hurtMarked = true

		recent[bolt.uuid] = now
		mark(level, struck, speed)
	}

	/**
	 * One rune, exactly where it was turned, and a note pitched to the shot.
	 *
	 * A single glyph rather than a burst: the ward is answering one arrow, and a
	 * cloud of them would say something much larger had happened. The ward rune
	 * rather than the echo rune the ruins give off -- that one is sized to be
	 * read across a room, which at arm's length covers the whole shoulder.
	 *
	 * Pitch rises with how hard the shot came in and never falls below its own
	 * note, so a point-blank crossbow bolt rings higher than a spent arrow but
	 * nothing ever sounds sluggish.
	 */
	private fun mark(level: ServerLevel, struck: Vec3, speed: Double) {
		level.sendParticles(
			ModParticles.WARD_RUNE,
			struck.x, struck.y, struck.z,
			1,
			0.0, 0.0, 0.0,
			0.0,
		)

		val hard = Mth.clamp((speed - SOFT_SHOT) / (HARD_SHOT - SOFT_SHOT), 0.0, 1.0)

		level.playSound(
			null,
			struck.x, struck.y, struck.z,
			ModSounds.CHARM_DEFLECT,
			SoundSource.PLAYERS,
			0.8f,
			(1.0 + hard * PITCH_RISE).toFloat(),
		)
	}

	/**
	 * How far out to look for something on its way in.
	 *
	 * Has to cover the fastest thing that could cross the guard in a single
	 * move: a fully drawn arrow travels about three blocks a tick.
	 */
	private const val SCAN_REACH = 6.0

	/** Slack on the guard's own box, standing in for the projectile's width. */
	private const val GRAZE = 0.3

	/** Slowest a returned bolt travels, however gently it arrived. */
	private const val MIN_SPEED = 0.8

	/** Far enough out of the guard's own bulk not to strike them on the way. */
	private const val CLEARANCE = 1.0

	/** How long a bolt we just turned is ignored if it finds us again. */
	private const val GRACE_TICKS = 20L

	/**
	 * Speeds the note is stretched between.
	 *
	 * A fully drawn bow leaves at about three blocks a tick and a spent or
	 * thrown thing at well under one, so this covers the range without the top
	 * end being reachable only by a crossbow.
	 */
	private const val SOFT_SHOT = 1.0
	private const val HARD_SHOT = 3.2

	/** Half an octave at most. Beyond that it stops sounding like the same ward. */
	private const val PITCH_RISE = 0.5
}
