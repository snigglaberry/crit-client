package dev.crit.client.ui;

import java.awt.Color;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A small colour picker. Three draggable sliders: the rainbow (hue), whiteness (all the way right is pure white)
 * and darkness (black at the left, the chosen colour at the right). A live preview and the hex value
 * sit above them. Changes apply straight away, so whatever is behind updates as you drag.
 */
public class ColorPickerScreen extends Screen {
    private static final int TRACK = 200, TH = 14, ROW = 32, BTN_H = 20;
    private static final int SL_HUE = 0, SL_WHITE = 1, SL_DARK = 2;

    private final Screen parent;
    private final String heading;
    private final IntConsumer onChange;
    private final int original;
    private float hue, sat, val;
    private int dragging = -1;
    private int px, py, pw, ph;

    public ColorPickerScreen(Screen parent, String heading, int color, IntConsumer onChange) {
        super(Component.literal("Colour"));
        this.parent = parent;
        this.heading = heading;
        this.onChange = onChange;
        this.original = color | 0xFF000000;
        float[] hsb = Color.RGBtoHSB((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, null);
        this.hue = hsb[0];
        this.sat = hsb[1];
        this.val = hsb[2];
    }

    private int hsv(float h, float s, float v) {
        return 0xFF000000 | (Color.HSBtoRGB(h, s, v) & 0xFFFFFF);
    }

    private int color() {
        return hsv(hue, sat, val);
    }

    private String hex() {
        return String.format("#%06X", color() & 0xFFFFFF);
    }

    @Override
    protected void init() {
        pw = TRACK + 108;
        ph = 246;
        px = (width - pw) / 2;
        py = (height - ph) / 2;
    }

    private int trackX() { return px + 68; }
    private int sliderY(int i) { return py + 100 + i * ROW; }
    private int btnY() { return py + ph - 30; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        g.fill(0, 0, width, height, Ui.BACKDROP);
        Ui.panel(g, px, py, pw, ph, Ui.TILE, Ui.PANEL, Ui.EDGE_LIGHT);

        Font font = Ui.font(this.font);
        g.text(font, Ui.heading(heading), px + 16, py + 12, Ui.TEXT, false);

        int pvx = px + 16, pvy = py + 30, pvw = pw - 32, pvh = 48;
        Ui.box(g, pvx, pvy, pvw, pvh, color(), Ui.EDGE);
        Ui.center(g, font, hex(), pvx + pvw / 2, pvy + pvh / 2 - 4, Theme.onColor(color()));

        slider(g, SL_HUE, "Colour");
        slider(g, SL_WHITE, "Whiteness");
        slider(g, SL_DARK, "Darkness");

        button(g, px + pw / 2 - 96, btnY(), 88, "Cancel", mx, my, false);
        button(g, px + pw / 2 + 8, btnY(), 88, "Done", mx, my, true);
    }

    /** The track gradient for a slider: a full rainbow for hue, black -> colour for darkness. */
    private void track(GuiGraphicsExtractor g, int x, int y, int w, int id) {
        for (int i = 0; i < w; i++) {
            float t = w <= 1 ? 0f : (float) i / (w - 1);
            int c = id == SL_HUE ? hsv(t, 1f, 1f) : id == SL_WHITE ? hsv(hue, 1f - t, val) : hsv(hue, sat, t);
            g.fill(x + i, y, x + i + 1, y + TH, c);
        }
    }

    private float sliderValue(int id) { return id == SL_HUE ? hue : id == SL_WHITE ? 1f - sat : val; }

    private void slider(GuiGraphicsExtractor g, int id, String label) {
        int x = trackX(), y = sliderY(id);
        float v = sliderValue(id);
        Font font = Ui.font(this.font);
        Ui.text(g, font, label, px + 16, y + 3, Ui.MUTED);
        Ui.shapedBox(g, x - 1, y - 1, TRACK + 2, TH + 2, 0, Ui.FIELD, Ui.EDGE);
        track(g, x, y, TRACK, id);
        int kx = x + (int) Math.round((TRACK - 5) * v);
        Ui.box(g, kx, y - 2, 5, TH + 4, 0xFFFFFFFF, 0xFF101010);
        Ui.text(g, font, id == SL_HUE ? "hue" : Math.round(v * 100) + "%", x + TRACK + 8, y + 3, Ui.DIM);
    }

    private void button(GuiGraphicsExtractor g, int x, int y, int w, String label, int mx, int my, boolean primary) {
        boolean hov = Ui.in(mx, my, x, y, w, BTN_H);
        int fill = primary ? (hov ? Ui.ACCENT_HOVER : Ui.ACCENT) : (hov ? Ui.TILE_HOVER : Ui.TILE);
        Ui.box(g, x, y, w, BTN_H, fill, primary ? fill : (hov ? Ui.ACCENT_DARK : Ui.EDGE_LIGHT));
        Ui.center(g, Ui.font(font), label, x + w / 2, y + (BTN_H - 8) / 2, primary ? Theme.onColor(fill) : Ui.TEXT);
    }

    private int hitSlider(double mx, double my) {
        for (int i = 0; i < 3; i++) {
            if (Ui.in(mx, my, trackX() - 2, sliderY(i) - 3, TRACK + 4, TH + 6)) return i;
        }
        return -1;
    }

    private void setFromMouse(double mx, int id) {
        float v = (float) ((mx - trackX()) / (double) (TRACK - 1));
        v = Math.max(0f, Math.min(1f, v));
        if (id == SL_HUE) {
            hue = v;
        } else if (id == SL_WHITE) {
            sat = 1f - v;   // all the way right is white
        } else {
            val = v;
        }
        onChange.accept(color());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        if (event.button() == 0) {
            int id = hitSlider(event.x(), event.y());
            if (id >= 0) {
                dragging = id;
                setFromMouse(event.x(), id);
                return true;
            }
            if (Ui.in(event.x(), event.y(), px + pw / 2 + 8, btnY(), 88, BTN_H)) {
                close();
                return true;
            }
            if (Ui.in(event.x(), event.y(), px + pw / 2 - 96, btnY(), 88, BTN_H)) {
                onChange.accept(original);
                close();
                return true;
            }
        }
        return super.mouseClicked(event, dbl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging >= 0) {
            setFromMouse(event.x(), dragging);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging >= 0) {
            dragging = -1;
            return true;
        }
        return super.mouseReleased(event);
    }

    private void close() {
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public void onClose() {
        close();
    }
}
