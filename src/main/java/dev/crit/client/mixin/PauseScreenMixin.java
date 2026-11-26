package dev.crit.client.mixin;

import dev.crit.client.CritScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the Crit Client mini button to the Esc (pause) menu, at the end of the Feedback / Report Bugs row. */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    /** Same sprite as the title-screen button: assets/critclient/textures/gui/sprites/crit_button.png */
    private static final Identifier CRIT_ICON = Identifier.fromNamespaceAndPath("critclient", "crit_button");

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void critclient$addButton(CallbackInfo ci) {
        // 1) find the row with "Give Feedback" / "Report Bugs"
        int rowY = Integer.MIN_VALUE;
        for (GuiEventListener l : this.children()) {
            if (!(l instanceof AbstractWidget w)) continue;
            Component msg = w.getMessage();
            String text = msg.getString().toLowerCase();
            boolean feedbackRow = text.contains("bug") || text.contains("feedback");
            if (!feedbackRow && msg.getContents() instanceof TranslatableContents tc) {
                feedbackRow = tc.getKey().equals("menu.reportBugs") || tc.getKey().equals("menu.sendFeedback");
            }
            if (feedbackRow) {
                rowY = w.getY();
                break;
            }
        }
        if (rowY == Integer.MIN_VALUE) { // fall back to the top button's row
            int topY = Integer.MAX_VALUE;
            for (GuiEventListener l : this.children()) {
                if (l instanceof AbstractWidget w && w.getWidth() >= 98 && w.getY() < topY) topY = w.getY();
            }
            rowY = topY == Integer.MAX_VALUE ? this.height / 4 + 24 : topY;
        }

        // 2) go to the very END of that row: right of every button already sitting in it (icon buttons included)
        int bx = this.width / 2 + 106;
        int rowEnd = Integer.MIN_VALUE;
        for (GuiEventListener l : this.children()) {
            if (l instanceof AbstractWidget w && w.getY() == rowY) {
                rowEnd = Math.max(rowEnd, w.getX() + w.getWidth());
            }
        }
        if (rowEnd != Integer.MIN_VALUE) bx = rowEnd + 4;

        SpriteIconButton btn = SpriteIconButton.builder(Component.literal("Crit Client"),
                        b -> Minecraft.getInstance().setScreenAndShow(new CritScreen(this)), true)
                .width(20)
                .sprite(CRIT_ICON, 18, 18)
                .build();
        btn.setPosition(bx, rowY);
        btn.setTooltip(Tooltip.create(Component.literal("Crit Client")));
        this.addRenderableWidget(btn);
    }
}