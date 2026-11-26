package dev.crit.client.title;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import dev.crit.client.CritClient;
import dev.crit.client.FilePicker;
import dev.crit.client.Net;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Imports a local still image and draws it in place of the title panorama. State and texture uploads are client-thread only. */
public final class MenuBackground {
    private static final Identifier IMAGE = Identifier.fromNamespaceAndPath("critclient", "menu_background");
    private static final int MAX_SIZE = 4096;
    private static DynamicTexture texture;
    private static boolean busy, error;
    private static String status = "";
    private static String imageName = "";

    private static Path file() { return CritClient.DIR.resolve("menu-background.png"); }

    public static boolean busy() { return busy; }
    public static boolean error() { return error; }
    public static String status() {
        if (!status.isEmpty()) return status;
        if (texture == null) return "Choose or drop a PNG, JPG, or BMP.";
        return (MenuSettings.customBackground() ? "Using: " : "Saved (background off): ") + imageName;
    }

    public static void load(Minecraft mc) {
        Path saved = file();
        if (Files.isRegularFile(saved)) start(mc, saved, false);
    }

    public static void browse(Minecraft mc) {
        start(mc, null, true);
    }

    public static void importImage(Minecraft mc, Path path) {
        start(mc, path, true);
    }

    private static void start(Minecraft mc, Path path, boolean importing) {
        if (busy) return;
        busy = true;
        error = false;
        status = path == null ? "Choose an image..." : "Loading image...";
        Net.POOL.execute(() -> {
            try {
                Path picked = path == null ? FilePicker.pickImage("Choose a main menu background") : path;
                if (picked == null) {
                    mc.execute(() -> finish("", false));
                    return;
                }
                BufferedImage image = readImage(picked);
                if (importing) saveImage(image);
                String name = importing ? picked.getFileName().toString() : "menu-background.png";
                NativeImage pixels = toNative(image);
                mc.execute(() -> upload(mc, pixels, name));
            } catch (IOException | RuntimeException e) {
                mc.execute(() -> fail(e));
            }
        });
    }

    /** Subsample oversized files during decoding, then keep a bounded, opaque RGB copy for the still background. */
    private static BufferedImage readImage(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("Choose an image file");
        try (ImageInputStream in = ImageIO.createImageInputStream(path.toFile())) {
            if (in == null) throw new IOException("Can't open the image");
            var readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new IOException("Use a PNG, JPG, or BMP image");
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                int longest = Math.max(width, height);
                double scale = Math.min(1.0, MAX_SIZE / (double) longest);
                int step = Math.ceilDiv(longest, MAX_SIZE + MAX_SIZE / 2);
                var params = reader.getDefaultReadParam();
                params.setSourceSubsampling(step, step, 0, 0);
                BufferedImage source = reader.read(0, params);
                int w = Math.max(1, (int) Math.round(width * scale));
                int h = Math.max(1, (int) Math.round(height * scale));
                BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = image.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(source, 0, 0, w, h, null);
                } finally {
                    g.dispose();
                }
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private static void saveImage(BufferedImage image) throws IOException {
        Path target = file();
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "menu-background-", ".png");
        try {
            if (!ImageIO.write(image, "png", temp.toFile())) throw new IOException("Can't save the image");
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static NativeImage toNative(BufferedImage image) {
        int width = image.getWidth(), height = image.getHeight();
        int[] row = new int[width];
        NativeImage pixels = new NativeImage(width, height, false);
        try {
            for (int y = 0; y < height; y++) {
                image.getRGB(0, y, width, 1, row, 0, width);
                for (int x = 0; x < width; x++) pixels.setPixel(x, y, row[x]);
            }
            return pixels;
        } catch (RuntimeException e) {
            pixels.close();
            throw e;
        }
    }

    private static void upload(Minecraft mc, NativeImage pixels, String name) {
        DynamicTexture next = null;
        try (pixels) {
            next = new DynamicTexture(() -> "Crit menu background", pixels);
            mc.getTextureManager().register(IMAGE, next);
            texture = next;
            imageName = name;
            finish("", false);
        } catch (RuntimeException e) {
            if (next != null) next.close();
            fail(e);
        }
    }

    private static void fail(Exception e) {
        CritClient.LOG.warn("Could not load the main menu background", e);
        finish("Couldn't use that image. Try PNG, JPG, or BMP.", true);
    }

    private static void finish(String message, boolean failed) {
        status = message;
        error = failed;
        busy = false;
    }

    /** Fills the window with a centered crop, without distorting the image. */
    public static boolean draw(GuiGraphicsExtractor g, int width, int height) {
        if (!MenuSettings.customBackground() || texture == null || width <= 0 || height <= 0) return false;
        float screenAspect = (float) width / height;
        float imageAspect = (float) texture.getTexture().getWidth(0) / texture.getTexture().getHeight(0);
        float u = Math.min(1f, screenAspect / imageAspect);
        float v = Math.min(1f, imageAspect / screenAspect);
        g.blit(texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR),
                0, 0, width, height, (1f - u) / 2f, (1f + u) / 2f, (1f - v) / 2f, (1f + v) / 2f);
        return true;
    }

    private MenuBackground() {}
}
