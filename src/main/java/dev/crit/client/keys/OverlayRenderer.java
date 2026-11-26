package dev.crit.client.keys;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws the overlays with plain rectangles (letters and digits are tiny pixel glyphs),
 * so everything scales to any size without touching the GUI matrix stack.
 */
public final class OverlayRenderer {
    private static final int BASE = 20, GAP = 2;

    private static final int FILL_IDLE = 0x55000000, EDGE_IDLE = 0x77FFFFFF, GLYPH_IDLE = 0xCCFFFFFF, GLYPH_DIM = 0x88FFFFFF;
    private static final int FILL_DOWN = 0xB8FFFFFF, EDGE_DOWN = 0xFFFFFFFF, GLYPH_DOWN = 0xFF202020;

    private static final String[] W = {"X...X", "X...X", "X...X", "X.X.X", "X.X.X", "XX.XX", "X...X"};
    private static final String[] A = {".XXX.", "X...X", "X...X", "XXXXX", "X...X", "X...X", "X...X"};
    private static final String[] S = {".XXXX", "X....", "X....", ".XXX.", "....X", "....X", "XXXX."};
    private static final String[] D = {"XXXX.", "X...X", "X...X", "X...X", "X...X", "X...X", "XXXX."};
    private static final String[] L = {"X....", "X....", "X....", "X....", "X....", "X....", "XXXXX"};
    private static final String[] R = {"XXXX.", "X...X", "X...X", "XXXX.", "X.X..", "X..X.", "X...X"};
    private static final String[] C = {".XXX.", "X...X", "X....", "X....", "X....", "X...X", ".XXX."};
    private static final String[] F = {"XXXXX", "X....", "X....", "XXXX.", "X....", "X....", "X...."};
    private static final String[] G = {".XXX.", "X...X", "X....", "X.XXX", "X...X", "X...X", ".XXXX"};
    private static final String[] H = {"X...X", "X...X", "X...X", "XXXXX", "X...X", "X...X", "X...X"};
    private static final String[] I = {".XXX.", "..X..", "..X..", "..X..", "..X..", "..X..", ".XXX."};
    private static final String[] K = {"X...X", "X..X.", "X.X..", "XX...", "X.X..", "X..X.", "X...X"};
    private static final String[] M = {"X...X", "XX.XX", "X.X.X", "X.X.X", "X...X", "X...X", "X...X"};
    private static final String[] N = {"X...X", "XX..X", "X.X.X", "X.X.X", "X..XX", "X...X", "X...X"};
    private static final String[] O = {".XXX.", "X...X", "X...X", "X...X", "X...X", "X...X", ".XXX."};
    private static final String[] P = {"XXXX.", "X...X", "X...X", "XXXX.", "X....", "X....", "X...."};
    private static final String[] T = {"XXXXX", "..X..", "..X..", "..X..", "..X..", "..X..", "..X.."};
    private static final String[] U = {"X...X", "X...X", "X...X", "X...X", "X...X", "X...X", ".XXX."};
    private static final String[] E = {"XXXXX", "X....", "X....", "XXXX.", "X....", "X....", "XXXXX"};
    private static final String[] B = {"XXXX.", "X...X", "X...X", "XXXX.", "X...X", "X...X", "XXXX."};
    private static final String[] X = {"X...X", "X...X", ".X.X.", "..X..", ".X.X.", "X...X", "X...X"};
    private static final String[] Y = {"X...X", "X...X", ".X.X.", "..X..", "..X..", "..X..", "..X.."};
    private static final String[] Z = {"XXXXX", "....X", "...X.", "..X..", ".X...", "X....", "XXXXX"};
    private static final String[] DOT = {".....", ".....", ".....", ".....", ".....", "..XX.", "..XX."};
    private static final String[] SLASH = {"....X", "....X", "...X.", "..X..", ".X...", "X....", "X...."};
    private static final String[] DASH = {".....", ".....", ".....", "XXXXX", ".....", ".....", "....."};
    private static final String[] COLON = {".....", "..X..", "..X..", ".....", "..X..", "..X..", "....."};
    private static final String[][] DIGITS = {
            {".XXX.", "X...X", "X..XX", "X.X.X", "XX..X", "X...X", ".XXX."},
            {"..X..", ".XX..", "..X..", "..X..", "..X..", "..X..", ".XXX."},
            {".XXX.", "X...X", "....X", "...X.", "..X..", ".X...", "XXXXX"},
            {".XXX.", "X...X", "....X", "..XX.", "....X", "X...X", ".XXX."},
            {"...X.", "..XX.", ".X.X.", "X..X.", "XXXXX", "...X.", "...X."},
            {"XXXXX", "X....", "XXXX.", "....X", "....X", "X...X", ".XXX."},
            {".XXX.", "X....", "X....", "XXXX.", "X...X", "X...X", ".XXX."},
            {"XXXXX", "....X", "...X.", "..X..", ".X...", ".X...", ".X..."},
            {".XXX.", "X...X", "X...X", ".XXX.", "X...X", "X...X", ".XXX."},
            {".XXX.", "X...X", "X...X", ".XXXX", "....X", "...X.", ".XXX."},
    };

    private static final int COMPASS_W = 168, COMPASS_H = 29;
    private static final double COMPASS_DEG = 1.7;   // glyph pixels per degree

    /** Whole screen pixels per glyph pixel: a fractional size makes glyph edges round differently as the strip moves. */
    private static double compassUnit(float scale) { return Math.max(2, Math.round(textUnit(scale))); }

    // ---- sizes -----------------------------------------------------------------

    // WASD is laid out in real screen pixels (see text() for why); these are all physical pixels.
    private static int physKey(float scale) { return Math.max(12, (int) Math.round(BASE * scale * guiScale())); }
    private static int physGap(float scale) { return Math.max(1, (int) Math.round(GAP * scale * guiScale())); }
    private static int physSpace(float scale) { return Math.max(6, (int) Math.round(physKey(scale) * 0.6)); }
    private static int physEdge() { return Math.max(1, (int) Math.round(guiScale())); }
    private static int toGui(int phys) { return (int) Math.ceil(phys / guiScale()); }
    public static int mouseUnit(float scale) { return Math.max(3, Math.round(6 * scale)); }
    /** GUI pixels -> real screen pixels. Text is drawn in real pixels so its size can change in small steps. */
    private static double guiScale() {
        double gs = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
        return gs < 1 ? 1 : gs;
    }
    /** Size of one glyph pixel in real screen pixels (never below 2, so the letters keep an even shape). */
    private static double textUnit(float scale) { return Math.max(2.0, 1.5 * scale * guiScale()); }
    private static int textWidth(int chars, float scale) { return (int) Math.ceil((chars * 6 - 1) * textUnit(scale) / guiScale()); }
    private static int textHeight(float scale) { return textHeightLines(1, scale); }
    private static int textHeightLines(int lines, float scale) {
        return (int) Math.ceil((7 + 9 * (lines - 1)) * textUnit(scale) / guiScale());
    }

    public static int width(KeyConfig.Kind kind, float scale) {
        return switch (kind) {
            case WASD -> toGui(3 * physKey(scale) + 2 * physGap(scale));
            case MOUSE -> 7 * mouseUnit(scale);
            case CPS -> textWidth(7, scale);
            case STATUS -> textWidth(9, scale);
            case PING -> textWidth(10, scale);
            case FPS -> textWidth(8, scale);
            case TPS -> textWidth(8, scale);
            case SPEED -> textWidth(16, scale);
            case COORDS -> textWidth(12, scale);
            case COMPASS -> (int) Math.ceil(COMPASS_W * compassUnit(scale) / guiScale());
            case ARMOR -> (int) Math.ceil(armorBase()[0] * scale);
        };
    }

    public static int height(KeyConfig.Kind kind, float scale, boolean space) {
        return switch (kind) {
            case WASD -> toGui(2 * physKey(scale) + physGap(scale) + (space ? physGap(scale) + physSpace(scale) : 0));
            case MOUSE -> 10 * mouseUnit(scale);
            case CPS, STATUS, PING, FPS, TPS, SPEED -> textHeight(scale);
            case COORDS -> textHeightLines(3, scale);
            case COMPASS -> (int) Math.ceil(COMPASS_H * compassUnit(scale) / guiScale());
            case ARMOR -> (int) Math.ceil(armorBase()[1] * scale);
        };
    }

    // ---- WASD + space ----------------------------------------------------------

    public static void drawWasd(GuiGraphicsExtractor g, int x, int y, float scale,
                                boolean w, boolean a, boolean s, boolean d, boolean showSpace, boolean space) {
        double gs = guiScale();
        int k = physKey(scale), gap = physGap(scale), step = k + gap;
        int px = (int) Math.round(x * gs), py = (int) Math.round(y * gs);
        g.pose().pushMatrix();
        g.pose().scale((float) (1.0 / gs), (float) (1.0 / gs));
        key(g, px + step, py, k, W, w);
        key(g, px, py + step, k, A, a);
        key(g, px + step, py + step, k, S, s);
        key(g, px + 2 * step, py + step, k, D, d);
        if (showSpace) spaceBar(g, px, py + 2 * step, 3 * k + 2 * gap, physSpace(scale), k, space);
        g.pose().popMatrix();
    }

    private static void outlinePx(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        int t = Math.min(physEdge(), Math.min(w, h) / 2);
        g.fill(x, y, x + w, y + t, color);
        g.fill(x, y + h - t, x + w, y + h, color);
        g.fill(x, y + t, x + t, y + h - t, color);
        g.fill(x + w - t, y + t, x + w, y + h - t, color);
    }

    private static void key(GuiGraphicsExtractor g, int x, int y, int k, String[] glyph, boolean down) {
        g.fill(x, y, x + k, y + k, down ? FILL_DOWN : FILL_IDLE);
        outlinePx(g, x, y, k, k, down ? EDGE_DOWN : EDGE_IDLE);
        double gp = k / 10.0; // letter pixels are 1/10 of the key, so a letter always keeps its proportions
        glyph(g, glyph, x + (k - 5 * gp) / 2.0, y + (k - 7 * gp) / 2.0, gp, down ? GLYPH_DOWN : GLYPH_IDLE);
    }

    private static void spaceBar(GuiGraphicsExtractor g, int x, int y, int w, int h, int k, boolean down) {
        g.fill(x, y, x + w, y + h, down ? FILL_DOWN : FILL_IDLE);
        outlinePx(g, x, y, w, h, down ? EDGE_DOWN : EDGE_IDLE);
        // a short centered bar, like the space symbol
        int lw = Math.round(w * 0.4f), lh = Math.max(1, Math.round(k / 10f));
        int lx = x + (w - lw) / 2, ly = y + (h - lh) / 2;
        g.fill(lx, ly, lx + lw, ly + lh, down ? GLYPH_DOWN : GLYPH_IDLE);
    }

    // ---- mouse -----------------------------------------------------------------

    /** A top-down mouse: left button, right button, a scroll wheel between them, and the palm below. */
    public static void drawMouse(GuiGraphicsExtractor g, int x, int y, float scale,
                                 boolean left, boolean right, boolean middle, boolean scrollUp, boolean scrollDown) {
        int u = mouseUnit(scale);

        // palm (starts one pixel up so its top edge shares the buttons' bottom edge)
        piece(g, x, y + 4 * u - 1, 7 * u, 6 * u + 1, false);
        piece(g, x, y, 3 * u, 4 * u, left);
        piece(g, x + 4 * u, y, 3 * u, 4 * u, right);
        // scroll wheel: whole thing lights on middle click, top half on scroll up, bottom half on scroll down
        int wx = x + 3 * u, wy = y + u, ww = u, wh = 2 * u;
        piece(g, wx, wy, ww, wh, middle);
        if (!middle && scrollUp) g.fill(wx, wy, wx + ww, wy + u, FILL_DOWN);
        if (!middle && scrollDown) g.fill(wx, wy + u, wx + ww, wy + wh, FILL_DOWN);
    }

    private static void piece(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean down) {
        g.fill(x, y, x + w, y + h, down ? FILL_DOWN : FILL_IDLE);
        g.outline(x, y, w, h, down ? EDGE_DOWN : EDGE_IDLE);
    }

    // ---- text overlays -----------------------------------------------------

    /** "CPS: 12", left clicks only. */
    public static void drawCps(GuiGraphicsExtractor g, int x, int y, float scale, int cps) {
        text(g, "CPS: " + Math.max(0, Math.min(99, cps)), x, y, scale);
    }

    /** One word such as SPRINTING or CROUCHING. */
    public static void drawStatus(GuiGraphicsExtractor g, int x, int y, float scale, String label) {
        text(g, label, x, y, scale);
    }

    /** "PING: 42" (milliseconds). */
    public static void drawPing(GuiGraphicsExtractor g, int x, int y, float scale, int ms) {
        text(g, "PING: " + (ms < 0 ? "--" : String.valueOf(Math.min(9999, ms))), x, y, scale);
    }

    /** "FPS: 144". */
    public static void drawFps(GuiGraphicsExtractor g, int x, int y, float scale, int fps) {
        text(g, "FPS: " + Math.max(0, Math.min(9999, fps)), x, y, scale);
    }

    /** "TPS: 20" (whole ticks per second, capped at 20). */
    public static void drawTps(GuiGraphicsExtractor g, int x, int y, float scale, int tps) {
        text(g, "TPS: " + (tps < 0 ? "--" : String.valueOf(Math.min(20, tps))), x, y, scale);
    }

    /** "SPEED: 12.3 B/S": horizontal speed in blocks per second. */
    public static void drawSpeed(GuiGraphicsExtractor g, int x, int y, float scale, double blocksPerSecond) {
        text(g, "SPEED: " + String.format(java.util.Locale.ROOT, "%.1f", Math.max(0, Math.min(999.9, blocksPerSecond))) + " B/S", x, y, scale);
    }

    /** Three lines: X, Y and Z of the block you stand in. */
    public static void drawCoords(GuiGraphicsExtractor g, int x, int y, float scale, int bx, int by, int bz) {
        text(g, "X: " + bx, x, y, scale, 0);
        text(g, "Y: " + by, x, y, scale, 1);
        text(g, "Z: " + bz, x, y, scale, 2);
    }


    // ---- compass ---------------------------------------------------------------

    /**
     * A backgroundless heading strip: the bearing on top with a pointer under it, then a tick every 15 degrees
     * labelled with the number (or N / NE / E ... at the compass points). Marks fade towards the edges.
     */
    public static void drawCompass(GuiGraphicsExtractor g, int x, int y, float scale, float yaw) {
        double gs = guiScale(), u = compassUnit(scale);
        double px = x * gs, py = y * gs, w = COMPASS_W * u;
        long mid = Math.round(px + w / 2);
        double bearing = (((yaw + 180.0) % 360.0) + 360.0) % 360.0;
        int off = (int) Math.max(1, Math.round(u / 2));

        g.pose().pushMatrix();
        g.pose().scale((float) (1.0 / gs), (float) (1.0 / gs));

        String head = Integer.toString((int) Math.round(bearing) % 360);
        textPhys(g, head, mid - Math.round((head.length() * 6 - 1) * u / 2), Math.round(py), u, 1f);

        // pointer
        for (int pass = 0; pass < 2; pass++) {
            int col = pass == 0 ? 0x99000000 : 0xFFFFFFFF, o = pass == 0 ? off : 0;
            for (int r = 0; r < 3; r++) {
                long half = Math.round((2 - r + 0.5) * u);
                long top = Math.round(py + (9 + r) * u);
                g.fill((int) (mid - half + o), (int) (top + o), (int) (mid + half + o), (int) (top + Math.round(u) + o), col);
            }
        }

        int first = (int) Math.floor((bearing - 62) / 15.0), last = (int) Math.ceil((bearing + 62) / 15.0);
        int tw = (int) Math.round(u);
        for (int n = first; n <= last; n++) {
            double dx = (n * 15.0 - bearing) * COMPASS_DEG * u;
            double edge = Math.abs(dx) / (w / 2);
            if (edge >= 1) continue;
            float a = (float) Math.max(0.15, 1 - edge * edge);
            int k = Math.floorMod(n * 15, 360);
            String label = switch (k) {
                case 0 -> "N"; case 45 -> "NE"; case 90 -> "E"; case 135 -> "SE";
                case 180 -> "S"; case 225 -> "SW"; case 270 -> "W"; case 315 -> "NW";
                default -> Integer.toString(k);
            };
            long cx = Math.round(mid + dx);
            long ty0 = Math.round(py + 13 * u), ty1 = Math.round(py + 20 * u);
            g.fill((int) (cx - tw / 2 + off), (int) (ty0 + off), (int) (cx - tw / 2 + tw + off), (int) (ty1 + off), ((int) (a * 0x99) << 24));
            g.fill((int) (cx - tw / 2), (int) ty0, (int) (cx - tw / 2 + tw), (int) ty1, ((int) (a * 255) << 24) | 0xFFFFFF);
            textPhys(g, label, cx - Math.round((label.length() * 6 - 1) * u / 2), Math.round(py + 23 * u), u, a);
        }
        g.pose().popMatrix();
    }

    /** Text in real screen pixels (the matrix must already be scaled back by the GUI scale), with a fade. */
    private static void textPhys(GuiGraphicsExtractor g, String s, double px, double py, double u, float alpha) {
        int unit = (int) Math.round(u);
        for (int pass = 0; pass < 2; pass++) {
            int off = pass == 0 ? Math.max(1, unit / 2) : 0;
            int color = pass == 0 ? ((int) (alpha * 0x99) << 24) : (((int) (alpha * 255)) << 24) | 0xFFFFFF;
            for (int i = 0; i < s.length(); i++) {
                String[] gl = glyphFor(s.charAt(i));
                if (gl != null) glyph(g, gl, px + off + i * 6 * u, py + off, u, color);
            }
        }
    }

    // ---- armour ----------------------------------------------------------------

    /** Size of the armour block at scale 1, in GUI pixels: {width, height}. */
    private static int[] armorBase() {
        boolean horiz = KeyConfig.armorHorizontal;
        int mode = KeyConfig.armorDurability;
        boolean bar = mode == 0, num = mode == 1;
        if (!horiz) {
            int sw = 16 + (num ? 30 : 0), sh = 16 + (bar ? 3 : 0);
            return new int[]{sw, 4 * sh + 3 * 2};
        }
        int sw = num ? 30 : 16, sh = 16 + (bar ? 3 : 0) + (num ? 10 : 0);
        return new int[]{4 * sw + 3 * 2, sh};
    }

    /**
     * The armour you wear, each piece with its item icon and (optionally) its remaining durability as a bar or a
     * number. Empty slots are skipped so the pieces you wear always sit together.
     */
    public static void drawArmor(GuiGraphicsExtractor g, int x, int y, float scale, net.minecraft.world.item.ItemStack[] pieces) {
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        boolean horiz = KeyConfig.armorHorizontal;
        int mode = KeyConfig.armorDurability;
        boolean bar = mode == 0, num = mode == 1;
        int slotW = horiz ? (num ? 30 : 16) : 0;
        int slotH = 16 + (bar ? 3 : 0);

        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        int idx = 0;
        for (net.minecraft.world.item.ItemStack st : pieces) {
            if (st == null || st.isEmpty()) continue;
            int sx = horiz ? idx * (slotW + 2) : 0;
            int sy = horiz ? 0 : idx * (slotH + 2);
            int ix = horiz ? sx + (slotW - 16) / 2 : sx;
            g.item(st, ix, sy);
            if (mode != 2 && st.isDamageableItem() && st.getMaxDamage() > 0) {
                int max = st.getMaxDamage(), left = Math.max(0, max - st.getDamageValue());
                float f = Math.max(0f, Math.min(1f, left / (float) max));
                int col = 0xFF000000 | (java.awt.Color.HSBtoRGB(f / 3f, 1f, 1f) & 0xFFFFFF);
                if (bar) {
                    g.fill(ix + 1, sy + 17, ix + 15, sy + 19, 0xFF000000);
                    g.fill(ix + 1, sy + 17, ix + 1 + Math.max(1, Math.round(14 * f)), sy + 18, col);
                    g.fill(ix + 1, sy + 18, ix + 1 + Math.max(1, Math.round(14 * f)), sy + 19, 0xFF000000 | ((col & 0xFEFEFE) >> 1));
                } else {
                    String t = Integer.toString(left);
                    int tw = font.width(t);
                    int tx = horiz ? sx + (slotW - tw) / 2 : ix + 19;
                    int ty = horiz ? sy + 18 : sy + 4;
                    g.text(font, t, tx, ty, col, true);
                }
            }
            idx++;
        }
        g.pose().popMatrix();
    }

    /**
     * Plain text, no background, with a small drop shadow. Drawn in real screen pixels (the matrix is scaled back
     * by the GUI scale), so the size follows the slider in small steps and no glyph pixel is ever uneven.
     */
    private static void text(GuiGraphicsExtractor g, String s, int x, int y, float scale) {
        text(g, s, x, y, scale, 0);
    }

    private static void text(GuiGraphicsExtractor g, String s, int x, int y, float scale, int line) {
        double gs = guiScale();
        double u = textUnit(scale);
        int unit = (int) Math.round(u);
        double px = x * gs, py = y * gs + line * 9 * u;
        g.pose().pushMatrix();
        g.pose().scale((float) (1.0 / gs), (float) (1.0 / gs));
        for (int pass = 0; pass < 2; pass++) {
            int off = pass == 0 ? Math.max(1, unit / 2) : 0;
            int color = pass == 0 ? 0x99000000 : 0xFFFFFFFF;
            for (int i = 0; i < s.length(); i++) {
                String[] gl = glyphFor(s.charAt(i));
                if (gl != null) glyph(g, gl, px + off + i * 6 * u, py + off, u, color);
            }
        }
        g.pose().popMatrix();
    }

    private static String[] glyphFor(char c) {
        if (c >= '0' && c <= '9') return DIGITS[c - '0'];
        return switch (c) {
            case 'A' -> A; case 'C' -> C; case 'D' -> D; case 'F' -> F; case 'G' -> G; case 'H' -> H; case 'I' -> I;
            case 'K' -> K; case 'L' -> L; case 'M' -> M; case 'N' -> N; case 'O' -> O; case 'P' -> P;
            case 'R' -> R; case 'S' -> S; case '-' -> DASH; case 'T' -> T; case 'U' -> U; case 'W' -> W; case ':' -> COLON;
            case 'E' -> E; case 'B' -> B; case 'X' -> X; case 'Y' -> Y; case 'Z' -> Z; case '.' -> DOT; case '/' -> SLASH;
            default -> null;
        };
    }

    // ---- pixel glyphs ----------------------------------------------------------

    private static void glyph(GuiGraphicsExtractor g, String[] rows, double gx, double gy, double gp, int color) {
        for (int r = 0; r < 7; r++) {
            for (int c = 0; c < 5; c++) {
                if (rows[r].charAt(c) != 'X') continue;
                int x0 = (int) Math.round(gx + c * gp), x1 = (int) Math.round(gx + (c + 1) * gp);
                int y0 = (int) Math.round(gy + r * gp), y1 = (int) Math.round(gy + (r + 1) * gp);
                g.fill(x0, y0, x1, y1, color);
            }
        }
    }

    private OverlayRenderer() {}
}
