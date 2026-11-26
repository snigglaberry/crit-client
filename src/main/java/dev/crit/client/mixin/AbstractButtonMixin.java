package dev.crit.client.mixin;

import dev.crit.client.ui.ButtonTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin extends AbstractWidget {
    protected AbstractButtonMixin(int x, int y, int w, int h, Component message) {
        super(x, y, w, h, message);
    }

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void critclient$drawThemedButton(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
        if (ButtonTheme.draw(g, (AbstractButton) (Object) this)) {
            handleCursor(g);
            ci.cancel();
        }
    }
}
