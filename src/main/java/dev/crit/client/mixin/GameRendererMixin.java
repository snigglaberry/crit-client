package dev.crit.client.mixin;

import dev.crit.client.fx.MotionBlur;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Motion Blur: runs the blur pass over the finished world frame (hand included), before the HUD is drawn. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void critclient$motionBlur(DeltaTracker delta, CallbackInfo ci) {
        MotionBlur.apply(Minecraft.getInstance());
    }
}
