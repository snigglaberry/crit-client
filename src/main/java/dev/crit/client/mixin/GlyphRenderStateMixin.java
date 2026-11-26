package dev.crit.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.crit.client.ui.UiFonts;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlyphRenderState.class)
public abstract class GlyphRenderStateMixin {
    @Shadow @Final private TextRenderable renderable;

    @Inject(method = "textureSetup", at = @At("HEAD"), cancellable = true)
    private void critclient$filterInter(CallbackInfoReturnable<TextureSetup> cir) {
        GpuTextureView view = renderable.textureView();
        if (UiFonts.isInterAtlas(view.texture())) {
            cir.setReturnValue(TextureSetup.singleTextureWithLightmap(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)));
        }
    }
}
