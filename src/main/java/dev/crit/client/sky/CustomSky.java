package dev.crit.client.sky;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.crit.client.CritClient;
import dev.crit.client.Icons;
import dev.crit.client.Net;
import dev.crit.client.modules.Modules;
import dev.crit.client.modules.SkyModule;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Everything the Custom Sky module needs besides its settings: the image on disk, the cube texture built from it,
 * and the cube mesh. The sky is drawn by borrowing the game's own End-sky pass (see SkyRendererMixin): that pass
 * draws a cube around the camera, so we give it our texture and a cube whose UVs read from a 3x2 atlas of faces.
 */
public final class CustomSky {
    /** The overworld sky is being replaced this frame (set by SkyRendererMixin). */
    public static volatile boolean active;
    /** The image (not just a colour) is being drawn this frame. */
    public static volatile boolean forcedEnd;

    private static final int FACE = 768;
    private static final int MAX_SOURCE_WIDTH = 4096;

    private static volatile boolean ready;
    private static volatile int fogRgb = 0x78A7FF;
    private static volatile String status = "";
    private static volatile boolean statusError;
    private static volatile int generation;
    private static int counter;

    private static Identifier texId;
    private static AbstractTexture tex;
    private static GpuBuffer buffer;

    private static Path source() { return CritClient.DIR.resolve("sky-image.png"); }

    // ------------------------------------------------------------------ state for the settings page

    public static boolean ready() { return ready; }
    public static String status() { return status; }
    public static boolean statusError() { return statusError; }
    public static boolean hasImage() { return Files.exists(source()); }

    private static void say(String msg, boolean error) {
        status = msg;
        statusError = error;
    }

    /** The colour the fog is tinted with: the sky colour, or the average colour of the image. */
    public static Integer fog() {
        if (!active) return null;
        int rgb = forcedEnd ? fogRgb : Modules.SKY.color();
        return 0xFF000000 | rgb;
    }

    public static AbstractTexture texture() { return tex; }

    /** The cube mesh. Render thread only; built the first time it is needed. */
    public static GpuBuffer buffer() {
        if (buffer == null) buffer = buildCube();
        return buffer;
    }

    // ------------------------------------------------------------------ loading

    /** Re-reads the saved image (if any) and rebuilds the cube texture. Safe to call often. */
    public static void reload() {
        if (!Files.exists(source())) {
            ready = false;
            return;
        }
        int gen = ++generation;
        SkyModule.Style style = Modules.SKY.style();
        boolean mirror = Modules.SKY.mirror();
        say("Building the sky...", false);
        Net.POOL.execute(() -> {
            try {
                BufferedImage src = ImageIO.read(source().toFile());
                if (src == null) throw new IOException("The saved image can't be read");
                int avg = average(src);
                BufferedImage atlas = buildAtlas(src, style, mirror);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ImageIO.write(atlas, "png", bos);
                NativeImage image = NativeImage.read(new ByteArrayInputStream(bos.toByteArray()));
                Minecraft mc = Minecraft.getInstance();
                mc.execute(() -> {
                    if (gen != generation) {
                        image.close();
                        return;
                    }
                    try {
                        swap(mc, image, avg);
                        say("Sky ready.", false);
                    } catch (Exception e) {
                        say("Couldn't load the sky: " + e.getMessage(), true);
                        CritClient.LOG.warn("Custom sky texture failed", e);
                    }
                });
            } catch (Exception e) {
                say("Couldn't load the sky: " + e.getMessage(), true);
                CritClient.LOG.warn("Custom sky image failed", e);
            }
        });
    }

    private static void swap(Minecraft mc, NativeImage image, int avg) throws ReflectiveOperationException {
        Identifier id = Identifier.fromNamespaceAndPath("critclient", "sky_" + (counter++));
        AbstractTexture t = Icons.texture(image);
        mc.getTextureManager().register(id, t);
        Identifier old = texId;
        texId = id;
        tex = t;
        fogRgb = avg;
        ready = true;
        if (old != null) mc.getTextureManager().release(old);
    }

    /** Reads an image the player picked, saves a copy next to the other Crit Client data and builds the sky from it. */
    public static void importImage(Path file) {
        say("Reading " + file.getFileName() + "...", false);
        Net.POOL.execute(() -> {
            try {
                BufferedImage in = ImageIO.read(file.toFile());
                if (in == null) throw new IOException("That isn't an image Java can read (use PNG or JPG)");
                BufferedImage img = in;
                if (in.getWidth() > MAX_SOURCE_WIDTH) {
                    int nh = Math.max(1, Math.round(in.getHeight() * (MAX_SOURCE_WIDTH / (float) in.getWidth())));
                    img = scale(in, MAX_SOURCE_WIDTH, nh);
                }
                Files.createDirectories(source().getParent());
                BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                try {
                    g.drawImage(img, 0, 0, null);
                } finally {
                    g.dispose();
                }
                ImageIO.write(rgb, "png", source().toFile());
                String name = file.getFileName().toString();
                Minecraft.getInstance().execute(() -> {
                    Modules.SKY.imageName(name);
                    reload();
                });
            } catch (Exception e) {
                say("Couldn't use that image: " + e.getMessage(), true);
            }
        });
    }

    // ------------------------------------------------------------------ image -> cube atlas

    private static BufferedImage scale(BufferedImage in, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(in, 0, 0, w, h, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static int average(BufferedImage src) {
        int w = src.getWidth(), h = src.getHeight();
        int stepX = Math.max(1, w / 64), stepY = Math.max(1, h / 64);
        long r = 0, g = 0, b = 0, n = 0;
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int p = src.getRGB(x, y);
                r += (p >> 16) & 255;
                g += (p >> 8) & 255;
                b += p & 255;
                n++;
            }
        }
        if (n == 0) return 0x78A7FF;
        return ((int) (r / n) << 16) | ((int) (g / n) << 8) | (int) (b / n);
    }

    /** The rotation the game applies to each of the six faces of the End-sky cube. */
    private static Matrix4f faceMatrix(int i) {
        Matrix4f m = new Matrix4f();
        float half = (float) (Math.PI / 2.0);
        switch (i) {
            case 1 -> m.rotationX(half);
            case 2 -> m.rotationX(-half);
            case 3 -> m.rotationX((float) Math.PI);
            case 4 -> m.rotationZ(half);
            case 5 -> m.rotationZ(-half);
            default -> { }
        }
        return m;
    }

    /**
     * A 3x2 atlas with one square per cube face. For every texel we work out which way it points in the world
     * (the same transform the GPU applies to the cube) and read the image in that direction, so the picture lines
     * up across the edges of the cube.
     */
    private static BufferedImage buildAtlas(BufferedImage srcImg, SkyModule.Style style, boolean mirror) {
        int w = srcImg.getWidth(), h = srcImg.getHeight();
        int[] px = srcImg.getRGB(0, 0, w, h, null, 0, w);
        int n = FACE;
        int[] out = new int[3 * n * 2 * n];
        Vector3f d = new Vector3f();
        for (int face = 0; face < 6; face++) {
            Matrix4f m = faceMatrix(face);
            int col = face % 3, row = face / 3;
            for (int j = 0; j < n; j++) {
                double v = (j + 0.5) / n;
                for (int i = 0; i < n; i++) {
                    double u = (i + 0.5) / n;
                    m.transformPosition((float) (2 * u - 1), -1f, (float) (2 * v - 1), d);
                    double su, sv;
                    if (style == SkyModule.Style.PANORAMA) {
                        double len = Math.sqrt(d.x * d.x + d.y * d.y + d.z * d.z);
                        double theta = Math.atan2(d.x, -d.z);                       // clockwise from north
                        double phi = Math.asin(Math.max(-1.0, Math.min(1.0, d.y / len)));
                        su = 0.5 + theta / (2 * Math.PI);
                        sv = 0.5 - phi / Math.PI;
                    } else {
                        double ax = Math.abs(d.x), ay = Math.abs(d.y), az = Math.abs(d.z);
                        double hz, vt;
                        if (ay >= ax && ay >= az) {            // top / bottom
                            hz = d.x / ay;
                            vt = (d.y > 0 ? -d.z : d.z) / ay;
                        } else if (az >= ax) {                 // north / south
                            hz = (d.z < 0 ? d.x : -d.x) / az;
                            vt = d.y / az;
                        } else {                               // east / west
                            hz = (d.x > 0 ? d.z : -d.z) / ax;
                            vt = d.y / ax;
                        }
                        su = 0.5 + hz * 0.5;
                        sv = 0.5 - vt * 0.5;
                    }
                    if (mirror) su = 1.0 - su;
                    int rgb;
                    if (style == SkyModule.Style.PANORAMA) {
                        rgb = sample(px, w, h, su * w - 0.5, sv * h - 0.5, true);
                    } else {
                        int m2 = Math.min(w, h);                // centre-crop to a square
                        double cx = (w - m2) / 2.0 + su * m2 - 0.5, cy = (h - m2) / 2.0 + sv * m2 - 0.5;
                        rgb = sample(px, w, h, cx, cy, false);
                    }
                    out[(row * n + j) * (3 * n) + col * n + i] = rgb;
                }
            }
        }
        BufferedImage img = new BufferedImage(3 * n, 2 * n, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 3 * n, 2 * n, out, 0, 3 * n);
        return img;
    }

    private static int sample(int[] px, int w, int h, double x, double y, boolean wrapX) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        int xa = wrapX ? Math.floorMod(x0, w) : Math.max(0, Math.min(w - 1, x0));
        int xb = wrapX ? Math.floorMod(x0 + 1, w) : Math.max(0, Math.min(w - 1, x0 + 1));
        int ya = Math.max(0, Math.min(h - 1, y0)), yb = Math.max(0, Math.min(h - 1, y0 + 1));
        int p00 = px[ya * w + xa], p10 = px[ya * w + xb], p01 = px[yb * w + xa], p11 = px[yb * w + xb];
        int r = lerp(p00, p10, p01, p11, fx, fy, 16);
        int g = lerp(p00, p10, p01, p11, fx, fy, 8);
        int b = lerp(p00, p10, p01, p11, fx, fy, 0);
        return (r << 16) | (g << 8) | b;
    }

    private static int lerp(int p00, int p10, int p01, int p11, double fx, double fy, int shift) {
        double a = ((p00 >> shift) & 255) * (1 - fx) + ((p10 >> shift) & 255) * fx;
        double b = ((p01 >> shift) & 255) * (1 - fx) + ((p11 >> shift) & 255) * fx;
        return (int) Math.round(a * (1 - fy) + b * fy);
    }

    // ------------------------------------------------------------------ the cube mesh

    /** The same six quads as the game's End sky, but with UVs into the 3x2 atlas and plain white vertex colour. */
    private static GpuBuffer buildCube() {
        VertexFormat fmt = DefaultVertexFormat.POSITION_TEX_COLOR;
        float inset = 0.5f / FACE;
        try (ByteBufferBuilder bytes = ByteBufferBuilder.exactlySized(24 * fmt.getVertexSize())) {
            BufferBuilder b = new BufferBuilder(bytes, PrimitiveTopology.QUADS, fmt);
            for (int face = 0; face < 6; face++) {
                Matrix4f m = faceMatrix(face);
                float col = face % 3, row = face / 3;
                float u0 = (col + inset) / 3f, u1 = (col + 1 - inset) / 3f;
                float v0 = (row + inset) / 2f, v1 = (row + 1 - inset) / 2f;
                b.addVertex(m, -100f, -100f, -100f).setUv(u0, v0).setColor(-1);
                b.addVertex(m, -100f, -100f, 100f).setUv(u0, v1).setColor(-1);
                b.addVertex(m, 100f, -100f, 100f).setUv(u1, v1).setColor(-1);
                b.addVertex(m, 100f, -100f, -100f).setUv(u1, v0).setColor(-1);
            }
            try (MeshData mesh = b.buildOrThrow()) {
                return RenderSystem.getDevice().createBuffer(() -> "critclient custom sky", 40, mesh.vertexBuffer());
            }
        }
    }

    private CustomSky() {}
}
