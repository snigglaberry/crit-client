package dev.crit.client.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import dev.crit.client.modules.Modules;
import dev.crit.client.modules.SkyModule;
import dev.crit.client.sky.CustomSky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom Sky. A colour just replaces the sky colour. An image is drawn through the game's End-sky pass: we flag the
 * overworld as an End-style sky for the frame, then swap in our texture and cube mesh while that pass runs.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Shadow @Final @Mutable private AbstractTexture endSkyTexture;
    @Shadow @Final @Mutable private GpuBuffer endSkyBuffer;

    @Unique private AbstractTexture critclient$origTexture;
    @Unique private GpuBuffer critclient$origBuffer;
    @Unique private boolean critclient$swapped;

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void critclient$extract(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
        CustomSky.active = false;
        CustomSky.forcedEnd = false;
        SkyModule sky = Modules.SKY;
        if (!sky.enabled() || state.skybox != DimensionType.Skybox.OVERWORLD) return;

        CustomSky.active = true;
        if (sky.mode() == SkyModule.Mode.IMAGE && CustomSky.ready()) {
            state.skybox = DimensionType.Skybox.END;
            CustomSky.forcedEnd = true;
        } else {
            state.skyColor = 0xFF000000 | sky.color();
        }
    }

    @Inject(method = "renderEndSky", at = @At("HEAD"))
    private void critclient$swapIn(CallbackInfo ci) {
        if (!CustomSky.forcedEnd || !CustomSky.ready()) return;
        critclient$origTexture = this.endSkyTexture;
        critclient$origBuffer = this.endSkyBuffer;
        this.endSkyTexture = CustomSky.texture();
        this.endSkyBuffer = CustomSky.buffer();
        critclient$swapped = true;
    }

    @Inject(method = "renderEndSky", at = @At("RETURN"))
    private void critclient$swapOut(CallbackInfo ci) {
        if (!critclient$swapped) return;
        this.endSkyTexture = critclient$origTexture;
        this.endSkyBuffer = critclient$origBuffer;
        critclient$swapped = false;
    }
}
