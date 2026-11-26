package dev.crit.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Little 9x9 pixel pictures drawn with fills, in the same style as the Crit logo. Module icons are PNG files, see drawIcon. */
public final class Pixel {
    /** The Crit crosshair: G = green, W = off-white. */
    public static final String[] LOGO = {
            "....G....", "....G....", "....G....", "...WGW...", "GGGGWGGGG", "...WGW...", "....G....", "....G....", "....G...."};

    /** The keys tab icon (the Key Overlay module's own icon is a PNG file). */
    public static final String[] KEYS = {
            ".........", "XXXXXXXXX", "X.X.X.X.X", "XXXXXXXXX", "X.X.X.X.X", "XXXXXXXXX", "X.XXXXX.X", "XXXXXXXXX", "........."};
    public static final String[] SKINS = {
            "...XXX...", "...XXX...", "...XXX...", ".XXXXXXX.", ".X.XXX.X.", ".X.XXX.X.", "...X.X...", "...X.X...", "...X.X..."};
    public static final String[] ACCOUNTS = {
            "...XXX...", "..X...X..", "..X...X..", "...XXX...", ".........", ".XXXXXXX.", "XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX"};
    public static final String[] STORE = {
            "....X....", "....X....", "....X....", "..XXXXX..", "...XXX...", "....X....", ".........", "X.......X", "XXXXXXXXX"};
    public static final String[] MODS = {
            "..XXXXX..", ".XXXXXXX.", "XXXXXXXXX", "XX.XXX.XX", "XXXXXXXXX", "XXXXXXXXX", ".XXXXXXX.", "..XXXXX..", "........."};



    /** Draws every 'X' cell of the picture in one colour. */
    public static void draw(GuiGraphicsExtractor g, String[] rows, int x, int y, int scale, int color) {
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int c = 0;
            while (c < row.length()) {
                if (row.charAt(c) != 'X') { c++; continue; }
                int start = c;
                while (c < row.length() && row.charAt(c) == 'X') c++;
                g.fill(x + start * scale, y + r * scale, x + c * scale, y + (r + 1) * scale, color);
            }
        }
    }

    public static void logo(GuiGraphicsExtractor g, int x, int y, int scale) {
        logo(g, x, y, scale, Ui.ACCENT, Ui.TEXT);
    }

    public static void logo(GuiGraphicsExtractor g, int x, int y, int scale, int armColor, int centerColor) {
        for (int r = 0; r < LOGO.length; r++) {
            for (int c = 0; c < LOGO[r].length(); c++) {
                char ch = LOGO[r].charAt(c);
                if (ch == '.') continue;
                g.fill(x + c * scale, y + r * scale, x + (c + 1) * scale, y + (r + 1) * scale,
                        ch == 'G' ? armColor : centerColor);
            }
        }
    }

    // ------------------------------------------------------------------ module icons (editable PNG files)

    private static final java.util.Map<String, int[]> ICONS = new java.util.HashMap<>();
    private static final int[] MISSING = new int[0];

    /** The pixels (ARGB, row by row) of a module's icon, read once from assets/critclient/textures/modules/<id>.png. */
    private static int[] icon(String id) {
        return ICONS.computeIfAbsent(id, k -> {
            try (java.io.InputStream in = Pixel.class.getResourceAsStream("/assets/critclient/textures/modules/" + k + ".png")) {
                if (in == null) return MISSING;
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
                if (img == null || img.getWidth() != 9 || img.getHeight() != 9) return MISSING;
                return img.getRGB(0, 0, 9, 9, null, 0, 9);
            } catch (Exception e) {
                return MISSING;
            }
        });
    }

    /**
     * Draws a module's icon picture (9x9, scaled up). Each pixel is multiplied by the tint colour, so paint icons in
     * white or grey: white shows as the tint itself, darker greys as a darker version of it.
     */
    public static void drawIcon(GuiGraphicsExtractor g, String id, int x, int y, int scale, int tint) {
        int[] px = icon(id);
        if (px.length != 81) return;
        int ta = (tint >>> 24) & 0xFF, tr = (tint >> 16) & 0xFF, tg = (tint >> 8) & 0xFF, tb = tint & 0xFF;
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                int p = px[r * 9 + c];
                int a = (p >>> 24) & 0xFF;
                if (a == 0) continue;
                int col = ((a * ta / 255) << 24) | ((((p >> 16) & 0xFF) * tr / 255) << 16)
                        | ((((p >> 8) & 0xFF) * tg / 255) << 8) | ((p & 0xFF) * tb / 255);
                g.fill(x + c * scale, y + r * scale, x + (c + 1) * scale, y + (r + 1) * scale, col);
            }
        }
    }

    private Pixel() {}
}
