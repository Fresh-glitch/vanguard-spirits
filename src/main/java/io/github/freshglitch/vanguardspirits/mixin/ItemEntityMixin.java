package io.github.freshglitch.vanguardspirits.mixin;

import io.github.freshglitch.vanguardspirits.item.EchoOfKinshipItem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets an Echo of Kinship behave like the thing it is meant to be.
 *
 * A mixin rather than anything in the item class because none of this is about
 * the item -- it is about the dropped entity carrying it, and vanilla gives a
 * mod no hook on that. Fabric can tell us when one is added to the world but
 * not when one ticks, and hovering has to happen every tick.
 *
 * Kept to a single call out to Kotlin. Everything interesting lives in
 * {@link EchoOfKinshipItem}, so this file only exists to be an injection point.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

	/**
	 * Whether the stack being picked up was a freed Echo, noted before the
	 * pickup empties it.
	 */
	@Unique
	private boolean vanguardSpirits$wasFreedEcho;

	@Inject(method = "tick", at = @At("TAIL"))
	private void vanguardSpirits$driftIfKinship(CallbackInfo info) {
		ItemEntity self = (ItemEntity) (Object) this;
		Level level = self.level();
		if (self.getItem().getItem() instanceof EchoOfKinshipItem) {
			EchoOfKinshipItem.drift(level, self);
		}
	}

	@Inject(method = "playerTouch", at = @At("HEAD"))
	private void vanguardSpirits$noteKinship(Player player, CallbackInfo info) {
		ItemEntity self = (ItemEntity) (Object) this;
		vanguardSpirits$wasFreedEcho = EchoOfKinshipItem.isFreed(self.getItem());
	}

	/**
	 * Read after the fact rather than before, because a touch is not a pickup:
	 * a full inventory leaves the item on the floor, and it should carry on
	 * calling. The entity being gone is what says it was actually taken.
	 */
	@Inject(method = "playerTouch", at = @At("TAIL"))
	private void vanguardSpirits$claimKinship(Player player, CallbackInfo info) {
		ItemEntity self = (ItemEntity) (Object) this;
		if (vanguardSpirits$wasFreedEcho && self.isRemoved()) {
			EchoOfKinshipItem.claimed(self.level(), self);
		}
	}
}
