package dev.crit.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.crit.client.store.Modrinth;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * Icon textures. Two kinds: store icons (downloaded from Modrinth, freed when the store closes) and
 * local icons (installed mods' own icons, kept for the session). Render-thread access only, except decode().
 */
public final class Icons {
    public static final int SIZE = 64;

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private static final Map<String, Identifier> STORE = new HashMap<>();
    private static final Set<String> STORE_PENDING = ConcurrentHashMap.newKeySet();
    private static final Set<String> STORE_FAILED = ConcurrentHashMap.newKeySet();

    private static final Map<String, Identifier> LOCAL = new HashMap<>();
    private static final Set<String> LOCAL_PENDING = ConcurrentHashMap.newKeySet();
    private static final Set<String> LOCAL_FAILED = ConcurrentHashMap.newKeySet();

    /** A Modrinth icon, or null while it's still downloading. */
    public static Identifier store(String url) {
        if (url == null) return null;
        Identifier id = STORE.get(url);
        if (id != null) return id;
        if (!STORE_FAILED.contains(url) && STORE_PENDING.add(url)) {
            Modrinth.EXEC.submit(() -> load(url, () -> Modrinth.getBytes(url), "store/", STORE, STORE_PENDING, STORE_FAILED));
        }
        return null;
    }

    /** An installed mod's icon (key = anything unique), or null while loading / if it has none. */
    public static Identifier local(String key, Callable<byte[]> loader) {
        Identifier id = LOCAL.get(key);
        if (id != null) return id;
        if (!LOCAL_FAILED.contains(key) && LOCAL_PENDING.add(key)) {
            Net.POOL.execute(() -> load(key, loader, "mod/", LOCAL, LOCAL_PENDING, LOCAL_FAILED));
        }
        return null;
    }

    private static void load(String key, Callable<byte[]> loader, String prefix, Map<String, Identifier> ready,
                             Set<String> pending, Set<String> failed) {
        byte[] png;
        try {
            png = decode(loader.call());
        } catch (Exception e) {
            failed.add(key);
            pending.remove(key);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            try {
                NativeImage img = NativeImage.read(new ByteArrayInputStream(png));
                Identifier id = Identifier.fromNamespaceAndPath("critclient",
                        "icons/" + prefix + Integer.toHexString(key.hashCode()) + Integer.toHexString(key.length()));
                mc.getTextureManager().register(id, texture(img));
                ready.put(key, id);
            } catch (Exception e) {
                CritClient.LOG.warn("Couldn't load icon {}: {}", key, e.toString());
                failed.add(key);
            }
            pending.remove(key);
        });
    }

    /** Frees every store icon texture; call when the store closes. */
    public static void clearStore() {
        Minecraft mc = Minecraft.getInstance();
        List<Identifier> ids = new ArrayList<>(STORE.values());
        STORE.clear();
        for (Identifier id : ids) mc.getTextureManager().release(id);
    }

    /** Any image Java can read -> a 64x64 PNG, letterboxed. */
    public static byte[] decode(byte[] raw) throws IOException {
        BufferedImage in = ImageIO.read(new ByteArrayInputStream(raw));
        if (in == null) throw new IOException("Unsupported image format");
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        double k = Math.min((double) SIZE / in.getWidth(), (double) SIZE / in.getHeight());
        int w = Math.max(1, (int) Math.round(in.getWidth() * k));
        int h = Math.max(1, (int) Math.round(in.getHeight() * k));
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(in, (SIZE - w) / 2, (SIZE - h) / 2, w, h, null);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(out, "png", bos);
        return bos.toByteArray();
    }

    /** DynamicTexture's constructor has changed across versions, so build it by whichever one fits. */
    public static AbstractTexture texture(NativeImage img) throws ReflectiveOperationException {
        for (Constructor<?> c : DynamicTexture.class.getConstructors()) {
            Class<?>[] pt = c.getParameterTypes();
            Object[] args = new Object[pt.length];
            boolean ok = false, fits = true;
            for (int i = 0; i < pt.length && fits; i++) {
                if (pt[i] == NativeImage.class) {
                    args[i] = img;
                    ok = true;
                } else if (pt[i] == Supplier.class) {
                    Supplier<String> label = () -> "critclient icon";
                    args[i] = label;
                } else if (pt[i] == String.class) {
                    args[i] = "critclient icon";
                } else {
                    fits = false;
                }
            }
            if (ok && fits) return (AbstractTexture) c.newInstance(args);
        }
        throw new NoSuchMethodException("No usable DynamicTexture constructor");
    }

    private Icons() {}
}
