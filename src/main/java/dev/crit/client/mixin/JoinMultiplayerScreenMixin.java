package dev.crit.client.mixin;

import dev.crit.client.CritScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds an Accounts button to the top-right of the multiplayer (servers) screen; it opens the popup on that tab. */
@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {
    protected JoinMultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void critclient$addButton(CallbackInfo ci) {
        Button acc = Button.builder(Component.literal("Accounts"),
                        b -> Minecraft.getInstance().setScreenAndShow(new CritScreen(this, CritScreen.TAB_ACCOUNTS)))
                .bounds(this.width - 6 - 70, 6, 70, 20).build();
        acc.setTooltip(Tooltip.create(Component.literal("Crit Client: accounts")));
        this.addRenderableWidget(acc);
    }
}
