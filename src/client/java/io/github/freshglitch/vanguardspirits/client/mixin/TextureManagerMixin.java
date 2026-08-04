package io.github.freshglitch.vanguardspirits.client.mixin;

import io.github.freshglitch.vanguardspirits.item.MemoryHue;
import net.minecraft.client.renderer.texture.TextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drives {@link MemoryHue} off the exact call that advances sprite animations.
 *
 * The Fractured Memory's name is coloured to match the frame its texture is
 * showing, so the two have to advance together. The first attempt counted
 * {@code ClientTickEvents.END_CLIENT_TICK} on the reasoning that sprites tick
 * once per client tick. They do not, and the disassembly of
 * {@code Minecraft.runTick} says so plainly:
 *
 * <pre>
 *   if (pendingTicks &gt; 0 &amp;&amp; isLevelRunningNormally())
 *       textureManager.tick();                      // ONCE per rendered frame
 *   for (i = 0; i &lt; Math.min(10, pendingTicks); i++)
 *       this.tick();                                // fires END_CLIENT_TICK
 * </pre>
 *
 * Two consequences, neither visible at a normal frame rate, which is exactly
 * why the first version looked correct when tested. Below twenty frames a
 * second several game ticks land in one frame, so the counter ran up to ten
 * times faster than the sprite. And while the game is paused the level stops
 * running normally, so the sprite freezes while client ticks keep firing --
 * meaning any visit to the escape menu desynchronised the two permanently.
 *
 * Injecting here fixes both without enumerating either. This is the same method
 * under the same guard, so every condition vanilla applies to sprite animation
 * is inherited rather than reproduced -- including any it grows later.
 *
 * Kept to an injection point that calls straight out to Kotlin, matching
 * {@code ItemEntityMixin}.
 */
@Mixin(TextureManager.class)
public abstract class TextureManagerMixin {

	@Inject(method = "tick", at = @At("TAIL"))
	private void vanguardSpirits$advanceMemoryHue(CallbackInfo info) {
		MemoryHue.advance();
	}
}
