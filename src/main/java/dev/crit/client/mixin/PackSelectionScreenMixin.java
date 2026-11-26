package dev.crit.client.mixin;

import dev.crit.client.CritScreen;
import dev.crit.client.store.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds a "Get Packs" button to the top-right of the Resource Packs screen; it opens the popup's store on resource packs. */
@Mixin(PackSelectionScreen.class)
public abstract class PackSelectionScreenMixin extends Screen {
    protected PackSelectionScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void critclient$addButton(CallbackInfo ci) {
        Button btn = Button.builder(Component.literal("Get Packs"),
                        b -> Minecraft.getInstance().setScreenAndShow(CritScreen.store(this, Kind.RESOURCEPACK)))
                .bounds(this.width - 6 - 90, 6, 90, 20).build();
        btn.setTooltip(Tooltip.create(Component.literal("Browse resource packs on Modrinth")));
        this.addRenderableWidget(btn);
    }
}
