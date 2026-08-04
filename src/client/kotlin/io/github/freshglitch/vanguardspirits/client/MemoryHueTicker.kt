package io.github.freshglitch.vanguardspirits.client

import io.github.freshglitch.vanguardspirits.VanguardSpirits
import io.github.freshglitch.vanguardspirits.item.MemoryHue
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.PreparableReloadListener

/**
 * Puts [MemoryHue] back to the start when the atlas is re-stitched.
 *
 * The *advancing* is not done here. It used to be, off
 * `ClientTickEvents.END_CLIENT_TICK`, on the reasoning that sprite animations
 * tick once per client tick -- which is wrong, and wrong in a way that only
 * shows up below twenty frames a second or after a pause. That job now belongs
 * to `TextureManagerMixin`, which rides the very call vanilla uses, so the two
 * cannot come apart. See that class for the disassembly.
 *
 * What remains is the other half of the same problem. Press F3+T and the atlas
 * is re-stitched, every `AnimationState` is rebuilt at frame zero, and a phase
 * that kept its old value would leave the name a colour or two behind the
 * picture for the rest of the session -- a drift nobody would think to look
 * for, on a feature whose entire point is that the two agree.
 *
 * Uses the **v1** resource-loader API. The `SimpleSynchronousResourceReloadListener`
 * every tutorial reaches for is v0, and while that module is present at runtime
 * it is not on the compile classpath here, so it does not resolve.
 */
object MemoryHueTicker : SimpleReloadListener<Unit>() {

	fun register() {
		ResourceLoader.get(PackType.CLIENT_RESOURCES)
			.registerReloadListener(VanguardSpirits.id("memory_hue"), this)
	}

	/** Nothing to read off disk; the reload itself is the whole signal. */
	override fun prepare(state: PreparableReloadListener.SharedState) = Unit

	override fun apply(prepared: Unit, state: PreparableReloadListener.SharedState) =
		MemoryHue.reset()
}
