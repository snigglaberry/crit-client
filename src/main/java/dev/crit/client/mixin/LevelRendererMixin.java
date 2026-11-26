package dev.crit.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.crit.client.modules.BlockOutlineModule;
import dev.crit.client.modules.Modules;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block Outline: draws the outline of the block you look at with your own colour, opacity and thickness. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "submitHitOutline", at = @At("HEAD"), cancellable = true)
    private void critclient$outline(PoseStack pose, SubmitNodeCollector collector, RenderType type,
                                    BlockOutlineRenderState state, int color, float width, boolean translucent,
                                    CallbackInfo ci) {
        BlockOutlineModule m = Modules.BLOCK_OUTLINE;
        if (!m.enabled() || state.highContrast() || SharedConstants.DEBUG_SHAPES) return;
        collector.submitShapeOutline(pose, state.shape(), type, m.argb(), (float) (width * m.thickness()), translucent);
        ci.cancel();
    }
}
