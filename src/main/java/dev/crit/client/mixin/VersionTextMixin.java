package dev.crit.client.mixin;

import dev.crit.client.CritClient;
import dev.crit.client.title.TitleExtras;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hides the "Minecraft 26.2 modded" line in the bottom left corner of the main menu. The game draws it with an ordinary
 * text call, so this skips that one call on the title screen (text that starts with "Minecraft" in the bottom left
 * corner) and nothing else. The game has several forms of the text call, and which one it uses depends on the version,
 * so every form is listed; the ones that don't exist are ignored (require = 0).
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class VersionTextMixin {
    private static boolean critclient$logged;

    private static boolean critclient$isVersionLine(String text, int x, int y) {
        if (text == null || !text.startsWith("Minecraft") || x > 6) return false;
        int titleHeight = TitleExtras.drawingTitleHeight;   // only non-zero while the main menu is drawing itself
        if (titleHeight == 0 || y < titleHeight - 16) return false;
        if (!critclient$logged) {
            critclient$logged = true;
            CritClient.LOG.info("Main menu: hiding the version line");
        }
        return true;
    }

    // ---- String text, with and without the shadow flag, returning nothing

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void critclient$hideA(Font f, String s, int x, int y, int c, boolean shadow, CallbackInfo ci) {
        if (critclient$isVersionLine(s, x, y)) ci.cancel();
    }

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void critclient$hideB(Font f, String s, int x, int y, int c, CallbackInfo ci) {
        if (critclient$isVersionLine(s, x, y)) ci.cancel();
    }

    // ---- the same, for versions where the text call returns a number

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I", at = @At("HEAD"), cancellable = true, require = 0)
    private void critclient$hideC(Font f, String s, int x, int y, int c, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        if (critclient$isVersionLine(s, x, y)) cir.setReturnValue(x);
    }

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)I", at = @At("HEAD"), cancellable = true, require = 0)
    private void critclient$hideD(Font f, String s, int x, int y, int c, CallbackInfoReturnable<Integer> cir) {
        if (critclient$isVersionLine(s, x, y)) cir.setReturnValue(x);
    }

    // ---- Component text

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void critclient$hideE(Font f, Component t, int x, int y, int c, boolean shadow, CallbackInfo ci) {
        if (critclient$isVersionLine(t.getString(), x, y)) ci.cancel();
    }

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void critclient$hideF(Font f, Component t, int x, int y, int c, CallbackInfo ci) {
        if (critclient$isVersionLine(t.getString(), x, y)) ci.cancel();
    }

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I", at = @At("HEAD"), cancellable = true, require = 0)
    private void critclient$hideG(Font f, Component t, int x, int y, int c, boolean shadow, CallbackInfoReturnable<Integer> cir) {
        if (critclient$isVersionLine(t.getString(), x, y)) cir.setReturnValue(x);
    }

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I", at = @At("HEAD"), cancellable = true, require = 0)
    private void critclient$hideH(Font f, Component t, int x, int y, int c, CallbackInfoReturnable<Integer> cir) {
        if (critclient$isVersionLine(t.getString(), x, y)) cir.setReturnValue(x);
    }
}
