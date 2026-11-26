package dev.crit.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.crit.client.CritClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Uses Minecraft's key-press event rather than a later snapshot of the key and screen. */
@Mixin(Minecraft.class)
public abstract class MenuHotkeyMixin {
    @Inject(method = "handleGlobalKeyPress", at = @At("RETURN"), cancellable = true)
    private void critclient$menuHotkey(InputConstants.Key key, boolean controlDown, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && CritClient.handleMenuHotkey((Minecraft) (Object) this, key)) {
            cir.setReturnValue(true);
        }
    }
}
