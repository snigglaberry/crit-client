package dev.crit.client.ui;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.util.ARGB;

/** Changes only the paint of the title buttons marked during menu setup. Input, focus and tooltips stay native. */
public final class ButtonTheme {
    private enum Paint { CRIT, HIDDEN }
    private static final Map<AbstractButton, Paint> CUSTOM = new WeakHashMap<>();

    public static void style(AbstractButton button) {
        if ((button instanceof Button || button instanceof CycleButton<?>)
                && !(button instanceof PlainTextButton) && !(button instanceof SpriteIconButton)) {
            CUSTOM.putIfAbsent(button, Paint.CRIT);
        }
    }

    public static void hide(AbstractButton button) { CUSTOM.put(button, Paint.HIDDEN); }

    public static boolean draw(GuiGraphicsExtractor g, AbstractButton button) {
        Paint paint = CUSTOM.get(button);
        if (paint == null) return false;
        float alpha = button.getAlpha();
        if (paint == Paint.HIDDEN || alpha <= 0.02f) return true;

        int x = button.getX(), y = button.getY(), w = button.getWidth(), h = button.getHeight();
        boolean active = button.active, hover = active && button.isHoveredOrFocused();
        int fill = !active ? Ui.FIELD : hover ? Ui.TILE_HOVER : Ui.TILE;
        int edge = !active ? Ui.EDGE : hover ? Ui.ACCENT : Ui.EDGE_LIGHT;
        Ui.box(g, x, y, w, h, ARGB.multiplyAlpha(fill, alpha), ARGB.multiplyAlpha(edge, alpha));
        if (hover) Ui.indicator(g, x, y, h, ARGB.multiplyAlpha(Ui.ACCENT, alpha));
        Font font = Ui.font(Minecraft.getInstance().font);
        String text = Ui.trim(font, button.getMessage().getString(), w - 16);
        Ui.center(g, font, text, x + w / 2, y + (h - 8) / 2, ARGB.multiplyAlpha(active ? Ui.TEXT : Ui.MUTED, alpha));
        return true;
    }

    private ButtonTheme() {}
}
