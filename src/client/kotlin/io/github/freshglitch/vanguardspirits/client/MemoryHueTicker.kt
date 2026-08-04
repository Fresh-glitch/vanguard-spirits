package io.github.freshglitch.vanguardspirits.client

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.item.MemoryHue
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.PreparableReloadListener

/**
 * Keeps [MemoryHue] in step with the Fractured Memory's sprite animation.
 *
 * Two lines of work, and both matter for the same reason. The sprite's
 * `AnimationState` is created when the atlas is stitched and advances once per
 * client tick, so to share its phase this has to start from zero at exactly the
 * same moment and count at exactly the same rate.
 *
 * The tick half is obvious. The reload half is the one that would have been
 * missed: press F3+T and the atlas is re-stitched, every sprite drops back to
 * frame zero, and a counter that kept running would leave the name a colour or
 * two behind the picture for the rest of the session -- a drift nobody would
 * think to look for, on a feature whose entire point is that the two agree.
 *
 * Uses the **v1** resource-loader API. The `SimpleSynchronousResourceReloadListener`
 * that every tutorial reaches for is v0, and while that module is present at
 * runtime it is not on the compile classpath here, so it does not resolve.
 */
object MemoryHueTicker : SimpleReloadListener<Unit>() {

	fun register() {
		ClientTickEvents.END_CLIENT_TICK.register { MemoryHue.advance() }
		ResourceLoader.get(PackType.CLIENT_RESOURCES)
			.registerReloadListener(VanguardSpirits.id("memory_hue"), this)
	}

	/** Nothing to read off disk; the reload itself is the whole signal. */
	override fun prepare(state: PreparableReloadListener.SharedState) = Unit

	override fun apply(prepared: Unit, state: PreparableReloadListener.SharedState) =
		MemoryHue.reset()
}
