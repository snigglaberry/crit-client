package dev.crit.client.skins;

import dev.crit.client.Net;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/** Async cache of skin front-renders. Only requested for tiles that are actually on screen. */
public final class Thumbs {
    public static final class Thumb {
        public volatile List<int[]> runs;
        public volatile boolean failed;
    }

    private static final Map<String, Thumb> MAP = new ConcurrentHashMap<>();

    public static Thumb get(String key, Callable<byte[]> loader, Boolean slim) {
        return MAP.computeIfAbsent(key, k -> {
            Thumb t = new Thumb();
            Net.POOL.execute(() -> {
                try {
                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(loader.call()));
                    if (img == null) throw new IllegalStateException("not an image");
                    t.runs = SkinFront.runs(img, img.getHeight() >= 64 && (slim != null ? slim : SkinFront.isSlim(img)));
                } catch (Exception e) {
                    t.failed = true;
                }
            });
            return t;
        });
    }

    public static void drop(String key) { MAP.remove(key); }

    private Thumbs() {}
}
