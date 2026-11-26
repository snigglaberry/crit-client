package dev.crit.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import dev.crit.client.ui.LoadingSplash;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Repaints the splash while leaving vanilla's reload progress, timing and transitions in charge. */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow private float currentProgress;

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Ljava/util/function/IntSupplier;getAsInt()I"), require = 3)
    private int critclient$background(IntSupplier vanilla) {
        return LoadingSplash.background();
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"), require = 2)
    private void critclient$logo(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture,
                                 int x, int y, float u, float v, int w, int h,
                                 int regionW, int regionH, int textureW, int textureH, int tint) {
        // Vanilla draws the two halves separately (upper and lower halves of the source texture).
        if (v == 0) LoadingSplash.logo(g, ARGB.alpha(tint));
    }

    @Inject(method = "extractProgressBar", at = @At("HEAD"), cancellable = true)
    private void critclient$progress(GuiGraphicsExtractor g, int left, int top, int right, int bottom,
                                     float opacity, CallbackInfo ci) {
        LoadingSplash.progress(g, (left + right) / 2, (top + bottom) / 2, currentProgress, opacity);
        ci.cancel();
    }
}
