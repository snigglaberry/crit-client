package dev.crit.client;

import dev.crit.client.accounts.Sessions;
import dev.crit.client.skins.SkinApi;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

/** The logged-in player's name and a 8x8 face (head + hat layer) taken from their skin. */
public final class Faces {
    private static volatile String name = "";
    private static volatile int[] pixels;
    private static long lastPoll;

    public static String name() {
        poll();
        return name;
    }

    /** 64 ARGB pixels, row by row, or null while loading / for accounts without a public skin. */
    public static int[] face() {
        poll();
        return pixels;
    }

    private static void poll() {
        long now = System.currentTimeMillis();
        if (now - lastPoll < 500) return;
        lastPoll = now;
        String n = Sessions.currentName();
        if (n.equals(name)) return;
        name = n;
        pixels = null;
        if (!n.matches("[A-Za-z0-9_]{1,16}")) return;
        Net.POOL.execute(() -> {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(SkinApi.steal(n).png()));
                int[] out = new int[64];
                boolean hat = img.getWidth() >= 48 && img.getHeight() >= 16;
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        int p = img.getRGB(8 + x, 8 + y);
                        if (hat) {
                            int h = img.getRGB(40 + x, 8 + y);
                            if ((h >>> 24) > 0) p = h;
                        }
                        out[y * 8 + x] = p | 0xFF000000;
                    }
                }
                if (n.equals(name)) pixels = out;
            } catch (Exception ignored) {
            }
        });
    }

    private Faces() {}
}
