package dev.crit.client.mixin;

import dev.crit.client.modules.FullbrightModule;
import dev.crit.client.modules.Modules;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fullbright: gives the lightmap the full night-vision boost, and refreshes it once more when switched off. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapRenderStateExtractorMixin {
    @Inject(method = "extract", at = @At("RETURN"))
    private void critclient$fullbright(LightmapRenderState state, float partialTick, CallbackInfo ci) {
        if (Modules.FULLBRIGHT.enabled()) {
            state.nightVisionEffectIntensity = 1.0F;
            state.nightVisionColor = LightmapRenderStateExtractor.WHITE;
            state.darknessEffectScale = 0.0F;
            state.needsUpdate = true;
            FullbrightModule.forced = true;
        } else if (FullbrightModule.forced) {
            state.needsUpdate = true;
            FullbrightModule.forced = false;
        }
    }
}
