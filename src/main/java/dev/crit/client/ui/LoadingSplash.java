package dev.crit.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

/** Splash artwork using only the vanilla GUI fill pipeline, which is available before the first resource reload. */
public final class LoadingSplash {
    private static final int BRAND_GREEN = 0xFF5DBB3A, BRAND_WHITE = 0xFFE8F0DC;

    public static int background() { return Theme.colorway().swatch(); }

    public static void logo(GuiGraphicsExtractor g, int alpha) {
        if (alpha == 0) return;
        int cell = Math.clamp(Math.min(g.guiWidth(), g.guiHeight()) / 48, 1, 9);
        int size = cell * Pixel.LOGO.length;
        Pixel.logo(g, (g.guiWidth() - size) / 2, (g.guiHeight() - size) / 2, cell,
                ARGB.color(alpha, BRAND_GREEN), ARGB.color(alpha, BRAND_WHITE));
    }

    public static void progress(GuiGraphicsExtractor g, int centerX, int centerY, float progress, float opacity) {
        if (opacity <= 0) return;
        int width = Math.min(200, g.guiWidth() * 3 / 5);
        if (width < 8) return;
        boolean rounded = UiThemes.rounded();
        int height = rounded ? 8 : 6;
        int x = centerX - width / 2;
        int y = centerY - height / 2;
        int filled = Math.round((width - 2) * Math.clamp(progress, 0, 1));
        int track = ARGB.multiplyAlpha(Ui.mix(background(), Ui.TEXT, 0.14f), opacity);
        int accent = ARGB.multiplyAlpha(Ui.ACCENT, opacity);

        // Paint disjoint scanlines so the accent and track don't double-blend during the fade.
        // Rounded caps use the GUI fill shader too; Crit's rounded-rectangle shader isn't loaded yet at startup.
        for (int row = 0; row < height; row++) {
            int inset = rounded ? capInset(width, height, row) : 0;
            int left = x + inset, right = x + width - inset;
            if (filled == 0 || row == 0 || row == height - 1) {
                Ui.rect(g, left, y + row, right - left, 1, track);
            } else {
                int cap = rounded ? capInset(filled, height - 2, row - 1) : 0;
                int start = x + 1 + cap, end = x + 1 + filled - cap;
                Ui.rect(g, left, y + row, start - left, 1, track);
                Ui.rect(g, start, y + row, end - start, 1, accent);
                if (end < right) Ui.rect(g, end, y + row, right - end, 1, track);
            }
        }
    }

    private static int capInset(int width, int height, int row) {
        double radius = Math.min(width, height) * 0.5;
        double dy = Math.max(0, radius - Math.min(row + 0.5, height - row - 0.5));
        return (int) Math.round(radius - Math.sqrt(radius * radius - dy * dy));
    }

    private LoadingSplash() {}
}
