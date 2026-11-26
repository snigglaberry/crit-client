package dev.crit.client.skins;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Builds the 16x32 front-view "render" of a skin as colour runs we can draw with fill(). */
public final class SkinFront {

    public static boolean isSlim(BufferedImage img) {
        if (img.getHeight() < 64 || img.getWidth() < 64) return false;
        for (int y = 20; y < 32; y++) {
            for (int x = 54; x < 56; x++) if ((img.getRGB(x, y) >>> 24) != 0) return false;
        }
        return true;
    }

    public static boolean validSize(BufferedImage img) {
        return img != null && img.getWidth() == 64 && (img.getHeight() == 64 || img.getHeight() == 32);
    }

    /** @return runs of {x, y, width, argb} in a 16x32 pixel grid. */
    public static List<int[]> runs(BufferedImage src, boolean slim) {
        boolean legacy = src.getHeight() < 64;
        int[] c = new int[16 * 32];
        int aw = slim ? 3 : 4;

        // head + hat
        blit(src, 8, 8, 8, 8, false, c, 4, 0, false);
        blit(src, 40, 8, 8, 8, false, c, 4, 0, true);
        // body + jacket
        blit(src, 20, 20, 8, 12, false, c, 4, 8, false);
        if (!legacy) blit(src, 20, 36, 8, 12, false, c, 4, 8, true);
        // arm on the viewer's left = the character's right arm
        int lx = slim ? 1 : 0;
        blit(src, 44, 20, aw, 12, false, c, lx, 8, false);
        if (!legacy) blit(src, 44, 36, aw, 12, false, c, lx, 8, true);
        // arm on the viewer's right = the character's left arm
        if (legacy) {
            blit(src, 44, 20, aw, 12, true, c, 12, 8, false);
        } else {
            blit(src, 36, 52, aw, 12, false, c, 12, 8, false);
            blit(src, 52, 52, aw, 12, false, c, 12, 8, true);
        }
        // legs
        blit(src, 4, 20, 4, 12, false, c, 4, 20, false);
        if (!legacy) blit(src, 4, 36, 4, 12, false, c, 4, 20, true);
        if (legacy) {
            blit(src, 4, 20, 4, 12, true, c, 8, 20, false);
        } else {
            blit(src, 20, 52, 4, 12, false, c, 8, 20, false);
            blit(src, 4, 52, 4, 12, false, c, 8, 20, true);
        }

        List<int[]> out = new ArrayList<>();
        for (int y = 0; y < 32; y++) {
            int x = 0;
            while (x < 16) {
                int px = c[y * 16 + x];
                if ((px >>> 24) == 0) { x++; continue; }
                int start = x;
                while (x < 16 && c[y * 16 + x] == px) x++;
                out.add(new int[]{start, y, x - start, px | 0xFF000000});
            }
        }
        return out;
    }

    private static void blit(BufferedImage s, int sx, int sy, int w, int h, boolean flip,
                             int[] dst, int dx, int dy, boolean overlay) {
        if (sx + w > s.getWidth() || sy + h > s.getHeight()) return;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int px = s.getRGB(flip ? sx + w - 1 - x : sx + x, sy + y);
                int a = px >>> 24;
                int i = (dy + y) * 16 + dx + x;
                if (!overlay) {
                    dst[i] = px;
                } else if (a == 255 || (dst[i] >>> 24) == 0) {
                    if (a > 0) dst[i] = px;
                } else if (a > 0) {
                    int b = dst[i];
                    int r = (((px >> 16) & 255) * a + ((b >> 16) & 255) * (255 - a)) / 255;
                    int g = (((px >> 8) & 255) * a + ((b >> 8) & 255) * (255 - a)) / 255;
                    int bl = ((px & 255) * a + (b & 255) * (255 - a)) / 255;
                    dst[i] = 0xFF000000 | (r << 16) | (g << 8) | bl;
                }
            }
        }
    }

    private SkinFront() {}
}
