package dev.crit.client.mixin;

import dev.crit.client.keys.ScrollState;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only used so the mouse overlay can show the scroll wheel moving. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), require = 0)
    private void critclient$onScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        ScrollState.onScroll(yOffset);
    }
}
