package dev.crit.client.mixin;

import dev.crit.client.modules.CrosshairModule;
import dev.crit.client.modules.Modules;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.level.GameType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the Crosshair Tweaks module replace the vanilla crosshair (and its attack indicator) entirely. */
@Mixin(Hud.class)
public abstract class HudMixin {
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void critclient$crosshair(GuiGraphicsExtractor g, DeltaTracker delta, CallbackInfo ci) {
        CrosshairModule m = Modules.CROSSHAIR;
        if (!m.enabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || !mc.options.getCameraType().isFirstPerson()) return;
        if (mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.SPECTATOR) return;
        ci.cancel();
        g.nextStratum();
        m.render(g, mc);
    }
}
