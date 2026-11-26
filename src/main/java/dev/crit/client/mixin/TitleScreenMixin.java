package dev.crit.client.mixin;

import dev.crit.client.CritScreen;
import dev.crit.client.title.MenuBackground;
import dev.crit.client.title.QuickPlay;
import dev.crit.client.title.TitleExtras;
import dev.crit.client.ui.ButtonTheme;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    protected TitleScreenMixin(Component title) {
        super(title);
    }

    /**
     * Rebuilds the menu: the language, accessibility, friends and Realms buttons go; Options moves up into the gap; the
     * wide Crit Client button takes the place Quit Game had, and Quit Game becomes a small X in the top right corner.
     * Then the click areas for the custom-drawn parts (TitleExtras draws everything you see).
     */
    @Inject(method = "init", at = @At("RETURN"))
    private void critclient$setupMenu(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        Screen self = this;

        String realmsText = Component.translatable("menu.online").getString();
        String quitText = Component.translatable("menu.quit").getString();
        String optionsText = Component.translatable("menu.options").getString();

        AbstractWidget options = null, quit = null;
        for (GuiEventListener child : new ArrayList<>(this.children())) {
            if (!(child instanceof AbstractWidget w)) continue;
            String text = w.getMessage().getString();
            if (text.equals(quitText)) quit = w;
            else if (text.equals(optionsText)) options = w;
            else if (text.equals(realmsText) || w instanceof SpriteIconButton || (w.getWidth() == 20 && w.getHeight() == 20)) {
                this.removeWidget(w);
            }
        }

        int top = this.height / 4 + 48;                    // where Singleplayer is
        int rowY = top + 48;                               // where Realms was; Options and Crit Client sit here now
        if (options != null) options.setY(rowY);

        int critX = quit != null ? quit.getX() : this.width / 2 + 2;
        int critW = quit != null ? quit.getWidth() : 98;
        TitleExtras.critRect = new int[]{critX, rowY, critW, 20};
        critclient$area(critX, rowY, critW, 20, () -> mc.setScreenAndShow(new CritScreen(self)));

        if (quit != null) {
            quit.setPosition(this.width - 20, 4);
            quit.setSize(16, 16);
            quit.setMessage(Component.empty());
            quit.setTooltip(Tooltip.create(Component.literal("Quit Game")));
            if (quit instanceof AbstractButton button) ButtonTheme.hide(button);
            TitleExtras.quitRect = new int[]{this.width - 20, 4, 16, 16};
        } else {
            TitleExtras.quitRect = null;
        }

        critclient$addMenuExtras(mc, self);
        for (GuiEventListener child : children()) {
            if (child instanceof AbstractButton button) ButtonTheme.style(button);
        }
    }

    private void critclient$addMenuExtras(Minecraft mc, Screen self) {
        TitleExtras.Layout l = TitleExtras.layout(this.width, this.height);
        if (!l.show()) return;

        Button server = critclient$area(l.lx(), l.btn1Y(), l.pw(), l.btnH(), () -> QuickPlay.joinServer(self));
        server.active = QuickPlay.hasServer();
        if (QuickPlay.hasServer()) server.setTooltip(Tooltip.create(Component.literal(QuickPlay.serverName() + "\n" + QuickPlay.serverIp())));

        Button world = critclient$area(l.lx(), l.btn2Y(), l.pw(), l.btnH(), () -> QuickPlay.joinWorld(self));
        world.active = QuickPlay.hasWorld();
        if (QuickPlay.hasWorld()) world.setTooltip(Tooltip.create(Component.literal(QuickPlay.worldName())));

        critclient$area(l.rx(), l.acctY(), l.pw(), 20, () -> {
            TitleExtras.accountsOpen = !TitleExtras.accountsOpen;
            this.rebuildWidgets();
        });

        if (TitleExtras.accountsOpen) {
            List<TitleExtras.Row> rows = TitleExtras.accountRows();
            int shown = TitleExtras.shownRows(l, this.height, rows.size());
            int y = l.acctY() + 23;
            for (int i = 0; i < shown; i++) {
                Runnable action = rows.get(i).action();
                critclient$area(l.rx() + 1, y + i * TitleExtras.ROW_H, l.pw() - 2, TitleExtras.ROW_H, () -> {
                    action.run();
                    TitleExtras.accountsOpen = false;
                    this.rebuildWidgets();
                });
            }
            critclient$area(l.rx() + 1, y + shown * TitleExtras.ROW_H, l.pw() - 2, TitleExtras.ROW_H, () -> {
                TitleExtras.accountsOpen = false;
                mc.setScreenAndShow(new CritScreen(self, CritScreen.TAB_ACCOUNTS));
            });
        }
    }

    /** A see-through button: it only receives the clicks, TitleExtras draws what you see. */
    private Button critclient$area(int x, int y, int w, int h, Runnable onClick) {
        Button b = Button.builder(Component.empty(), btn -> onClick.run()).bounds(x, y, w, h).build();
        ButtonTheme.hide(b);
        this.addRenderableWidget(b);
        return b;
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void critclient$titleDrawStart(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
        TitleExtras.drawingTitleHeight = this.height;
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IF)V"))
    private void critclient$hideVanillaLogo(LogoRenderer logo, GuiGraphicsExtractor g, int width, float opacity) {}

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/SplashRenderer;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/client/gui/Font;F)V"))
    private void critclient$hideVanillaSplash(SplashRenderer splash, GuiGraphicsExtractor g, int width, Font font, float opacity) {}

    // Replace just the panorama pass, before the vanilla buttons, tooltips and copyright are drawn.
    @Override
    protected void extractPanorama(GuiGraphicsExtractor g, float pt) {
        if (!MenuBackground.draw(g, this.width, this.height)) super.extractPanorama(g, pt);
    }

    /** The Crit Client logo, button faces and panels, drawn after the rest of the screen. */
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void critclient$drawExtras(GuiGraphicsExtractor g, int mx, int my, float pt, CallbackInfo ci) {
        TitleExtras.drawingTitleHeight = 0;
        TitleExtras.draw(g, this.width, this.height, mx, my);
    }
}
