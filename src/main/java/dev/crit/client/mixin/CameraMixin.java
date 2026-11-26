package dev.crit.client.mixin;

import dev.crit.client.modules.Modules;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Applies the Zoom module to the world field of view. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "update", at = @At("HEAD"))
    private void critclient$zoomAdvance(DeltaTracker delta, CallbackInfo ci) {
        Modules.ZOOM.advance(delta.getRealtimeDeltaTicks() / 20.0);
    }

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void critclient$zoomFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        float f = Modules.ZOOM.factor();
        if (f != 1.0f) cir.setReturnValue(cir.getReturnValueF() * f);
    }
}
