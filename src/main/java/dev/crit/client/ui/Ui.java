package dev.crit.client.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/** The drawing primitives shared by Crit screens. Colors and UI style are separate preferences. */
public final class Ui {
    public static int BACKDROP = 0xA0000000;
    public static int PANEL = 0xFF1E1E1E, HEADER = 0xFF181818, SIDEBAR = 0xFF191919;
    public static int EDGE = 0xFF2E2E2E, EDGE_LIGHT = 0xFF3C3C3C;
    public static int TILE = 0xFF262626, TILE_HOVER = 0xFF313131, FIELD = 0xFF141414;
    public static int ACCENT = 0xFF5DBB3A, ACCENT_HOVER = 0xFF74D14F, ACCENT_DARK = 0xFF3E7A28;
    public static int TEXT = 0xFFE6E6E6, MUTED = 0xFF9A9A9A, DIM = 0xFF6A6A6A;
    public static final int ERR = 0xFFE5534B;

    public static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Blends {@code over} onto {@code base} with the given 0..1 alpha. */
    public static int mix(int base, int over, float t) {
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF, ba = (base >>> 24) & 0xFF;
        int or = (over >> 16) & 0xFF, og = (over >> 8) & 0xFF, ob = over & 0xFF, oa = (over >>> 24) & 0xFF;
        int r = Math.round(br + (or - br) * t), g = Math.round(bg + (og - bg) * t), b = Math.round(bb + (ob - bb) * t);
        int a = Math.round(ba + (oa - ba) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + h, c);
    }

    /** A horizontal gradient, drawn one column at a time. */
    public static void hgrad(GuiGraphicsExtractor g, int x, int y, int w, int h, int from, int to) {
        for (int i = 0; i < w; i++) {
            int c = mix(from, to, w <= 1 ? 0f : (float) i / (w - 1));
            g.fill(x + i, y, x + i + 1, y + h, c);
        }
    }

    public static void box(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int edge) {
        shapedBox(g, x, y, w, h, UiThemes.current().radius(), fill, edge);
    }

    public static void shapedBox(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int fill, int edge) {
        if (w <= 0 || h <= 0) return;
        if (radius > 0) {
            RoundedRect.draw(g, x, y, w, h, radius, fill, fill, edge);
        } else if (w <= 2 || h <= 2 || fill == edge) {
            rect(g, x, y, w, h, edge);
        } else {
            g.outline(x, y, w, h, edge);
            rect(g, x + 1, y + 1, w - 2, h - 2, fill);
        }
    }

    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, int top, int bottom, int edge) {
        if (UiThemes.rounded()) RoundedRect.draw(g, x, y, w, h, UiThemes.current().panelRadius(), top, bottom, edge);
        else box(g, x, y, w, h, bottom, edge);
    }

    public static void panelCutout(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (UiThemes.rounded()) RoundedRect.draw(g, x, y, w, h, -UiThemes.current().panelRadius(), color, color, color);
    }

    public static void surface(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (w > 0 && h > 0) box(g, x, y, w, h, color, color);
    }

    public static void indicator(GuiGraphicsExtractor g, int x, int y, int h, int color) {
        if (UiThemes.rounded()) surface(g, x + 4, y + 5, 2, h - 10, color);
        else rect(g, x + 1, y + 1, 2, h - 2, color);
    }

    /** Controls already sit on top of the panel. Keep their tint subtle until the panel is nearly opaque. */
    public static int controlColor(int color) {
        float panel = Theme.panelOpacity() / 100f;
        float squared = panel * panel;
        return ARGB.multiplyAlpha(color, 0.15f + 0.85f * squared * squared);
    }

    public static void controlBox(GuiGraphicsExtractor g, int x, int y, int w, int h, int fill, int edge) {
        box(g, x, y, w, h, controlColor(fill), controlColor(edge));
    }

    public static void switchControl(GuiGraphicsExtractor g, int x, int y, boolean on, boolean hover) {
        shapedBox(g, x, y, 26, 12, UiThemes.rounded() ? 6 : 0,
                controlColor(on ? ACCENT : hover ? TILE_HOVER : FIELD), controlColor(on ? ACCENT : hover ? MUTED : EDGE_LIGHT));
        int kx = on ? x + 15 : x + 3;
        if (UiThemes.rounded()) {
            int color = on ? 0xFFFFFFFF : MUTED;
            RoundedRect.draw(g, kx, y + 2, 8, 8, 4, color, color, color);
        } else rect(g, kx, y + 3, 8, 6, on ? 0xFFFFFFFF : MUTED);
    }

    public static void slider(GuiGraphicsExtractor g, int x, int y, int w, int handleWidth, double value, boolean hover) {
        if (w < handleWidth) return;
        int knob = x + (int) Math.round((w - handleWidth) * value);
        controlBox(g, x, y, w, 6, FIELD, EDGE_LIGHT);
        int filled = (int) Math.round((w - 2) * value);
        if (filled > 0) surface(g, x + 1, y + 1, filled, 4, ACCENT);
        box(g, knob, y - 3, handleWidth, 12, TEXT, hover ? ACCENT : EDGE_LIGHT);
    }

    public static void chevron(GuiGraphicsExtractor g, int cx, int cy, boolean up, int color) {
        if (UiThemes.rounded()) {
            float angle = (float) (Math.PI / 4 * (up ? -1 : 1));
            g.pose().pushMatrix().translate(cx, cy + (up ? -1 : 1)).rotate(angle);
            RoundedRect.draw(g, -5, -1, 6, 2, 1, color, color, color);
            g.pose().popMatrix().pushMatrix().translate(cx, cy + (up ? -1 : 1)).rotate(-angle);
            RoundedRect.draw(g, -1, -1, 6, 2, 1, color, color, color);
            g.pose().popMatrix();
        } else {
            for (int i = 0; i < 3; i++) {
                int half = up ? i : 2 - i;
                rect(g, cx - half, cy - 1 + i, half * 2 + 1, 1, color);
            }
        }
    }

    public static Font font(Font vanilla) { return UiFonts.font(vanilla); }

    public static Component heading(String text) { return UiFonts.heading(text); }

    public static void text(GuiGraphicsExtractor g, Font f, String s, int x, int y, int c) {
        g.text(f, s, x, y, c, false);
    }

    public static void center(GuiGraphicsExtractor g, Font f, String s, int cx, int y, int c) {
        g.text(f, s, cx - f.width(s) / 2, y, c, false);
    }

    public static void right(GuiGraphicsExtractor g, Font f, String s, int rx, int y, int c) {
        g.text(f, s, rx - f.width(s), y, c, false);
    }

    public static String trim(Font f, String s, int maxW) {
        if (s == null || maxW <= 0) return "";
        if (f.width(s) <= maxW) return s;
        int suffix = f.width("…");
        return suffix > maxW ? "" : f.plainSubstrByWidth(s, maxW - suffix) + "…";
    }

    public static List<String> wrap(Font f, String text, int maxW) {
        List<String> lines = new ArrayList<>();
        if (text == null) return lines;
        for (String para : text.split("\n")) {
            StringBuilder cur = new StringBuilder();
            for (String word : para.split(" ")) {
                String test = cur.isEmpty() ? word : cur + " " + word;
                if (f.width(test) > maxW && !cur.isEmpty()) {
                    lines.add(cur.toString());
                    cur = new StringBuilder(word);
                } else {
                    cur = new StringBuilder(test);
                }
            }
            lines.add(cur.toString());
        }
        return lines;
    }

    public static void scrollbar(GuiGraphicsExtractor g, int x, int top, int h, double scroll, double max) {
        if (max <= 0) return;
        int barH = Math.max(18, (int) (h * (h / (h + max))));
        int barY = top + (int) ((h - barH) * (scroll / max));
        surface(g, x, barY, 3, barH, controlColor(ACCENT_DARK));
    }

    private Ui() {}
}
