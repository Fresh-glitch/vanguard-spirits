package io.github.freshglitch.vanguardspirits.entity

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.SaplingBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/**
 * How a Nymph finds out what you did.
 *
 * One place, hooked to the two events that matter, rather than a Nymph scanning
 * her surroundings every tick for things that have changed. That is not only
 * cheaper -- it is the only version that can work at all, because "a tree was
 * felled" and "an animal was killed" are *events*, and a mob polling the world
 * can only ever see the aftermath. A poll cannot tell a chopped log from a log
 * that was never there.
 *
 * ## She feels it rather than sees it
 *
 * There is no line-of-sight check. A Nymph on the far side of a hill knows when
 * something in her wood is cut, and that is deliberate on two counts. The
 * fiction is the better one -- she is not a guard with eyes, she is the thing
 * the wood belongs to. And the alternative behaves badly: with a sight check, a
 * player learns within minutes that the way to strip a flower forest is to stand
 * behind a rock, which turns a piece of characterisation into an exploit to be
 * farmed.
 *
 * ## The weights
 *
 * They are not a scale of ecological harm, they are a scale of *what she would
 * mind*, and the two are not the same. A flower is nothing much and a whole tree
 * is a great deal, but a killed animal outweighs both, because it is the only
 * one of the three that cannot grow back.
 *
 * Read them against [Nymph.WARY_AT] and [Nymph.WRATH_AT] to see the design: a
 * bouquet of four flowers earns a warning, two logs earns a warning, one dead
 * sheep earns a warning on its own, and two earns a fight.
 */
object GroveWatch {

	fun register() {
		ServerLivingEntityEvents.AFTER_DEATH.register { victim, source ->
			if (victim !is Animal) return@register
			val level = victim.level() as? ServerLevel ?: return@register

			// The responsible entity, not the arrow. `getEntity` already walks
			// back from a projectile to whoever loosed it, which is the whole
			// difference between a bow being a loophole and not.
			val killer = source.entity as? Player ?: return@register

			// A calf or a lamb is worse, and she would say so.
			val weight = if (victim.isBaby) KILLED_ANIMAL * 2 else KILLED_ANIMAL
			tell(level, victim.blockPosition(), killer, weight)
		}

		PlayerBlockBreakEvents.AFTER.register { level, player, pos, state, _ ->
			val server = level as? ServerLevel ?: return@register
			val weight = costOf(state)
			if (weight > 0) tell(server, pos, player, weight)
		}
	}

	/**
	 * What she would mind about losing this, or zero for anything she would not.
	 *
	 * Saplings are matched by class rather than by tag, because 26.2 has no
	 * `BlockTags.SAPLINGS` -- the only sapling tag vanilla ships is the *item*
	 * one, `ItemTags.SAPLINGS`, and there is no block-side counterpart to pair
	 * with it. Worth knowing before reaching for the obvious name: the asymmetry
	 * runs the other way for flowers, where the block tags exist and the item tag
	 * does not.
	 */
	private fun costOf(state: BlockState): Int = when {
		state.`is`(BlockTags.LOGS) -> FELLED_LOG
		state.block is SaplingBlock -> UPROOTED_SAPLING
		// Checked before LEAVES so a flowering bush is priced as a flower.
		state.`is`(BlockTags.FLOWERS) -> PICKED_FLOWER
		state.`is`(BlockTags.LEAVES) -> STRIPPED_LEAVES
		else -> 0
	}

	/**
	 * Every Nymph close enough to care.
	 *
	 * Plural on purpose. Two of them within earshot of the same felled tree both
	 * mind, and they each keep their own account of it -- there is no shared
	 * reputation to update, which is the point made at length in [Nymph].
	 */
	private fun tell(level: ServerLevel, where: BlockPos, who: Player, weight: Int) {
		// `Vec3.atCenterOf` rather than a `center` on the position: `BlockPos` has
		// no such accessor in 26.2, and the one that reads like it -- Vec3i's
		// `distToCenterSqr` -- is a distance rather than a point.
		val range = AABB.ofSize(Vec3.atCenterOf(where), WATCH_SPAN, WATCH_SPAN, WATCH_SPAN)
		for (nymph in level.getEntitiesOfClass(Nymph::class.java, range)) {
			nymph.wrong(who, weight)
		}
	}

	/**
	 * Twenty four blocks in every direction.
	 *
	 * Wide enough that she covers the clearing she is standing in, narrow enough
	 * that a player who has walked well away from her is genuinely away. It is
	 * also comfortably inside her `FOLLOW_RANGE` of 32, so anything that angers
	 * her is something she can actually reach -- a grievance she could not act on
	 * would read as the feature not working.
	 */
	private const val WATCH_SPAN = 48.0

	private const val KILLED_ANIMAL = 30
	private const val FELLED_LOG = 12
	private const val UPROOTED_SAPLING = 8
	private const val PICKED_FLOWER = 6
	private const val STRIPPED_LEAVES = 4
}
