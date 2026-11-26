package dev.crit.client.title;

import dev.crit.client.Net;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.concurrent.Callable;
import javax.imageio.ImageIO;

/**
 * A small software 3D renderer for the title screen's player: the standard Minecraft player model (head, body, arms,
 * legs plus the second skin layer) is ray-cast pixel by pixel from your skin, walking in place and turning a little
 * towards the mouse. It needs no world or entity, so it works on the main menu, and it only touches the skin PNG.
 */
final class DollRenderer {
    // ------------------------------------------------------------------ skin loading

    record Skin(byte[] png, boolean slim) {}
    private record Texture(int[] pixels, int width, int height, boolean slim, boolean legacy) {}

    private static volatile Texture skin;
    private static volatile boolean failed;
    private static String loadedKey = "";
    private static int request;

    static boolean failed() { return failed; }

    /** Offline skins are already decoded at boot. Display them without waiting for an I/O worker. */
    static synchronized void useLocalSkin(String key, int[] pixels, boolean slim) {
        if (key.equals(loadedKey)) return;
        select(key);
        skin = new Texture(pixels, 64, 64, slim, false);
    }

    /** Starts loading the skin for this key (once per key). */
    static synchronized void ensureSkin(String key, Callable<Skin> loader) {
        if (key.equals(loadedKey)) return;
        int id = select(key);
        Net.POOL.execute(() -> {
            try {
                Skin source = loader.call();
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(source.png()));
                if (img == null) throw new IllegalStateException("not an image");
                int w = img.getWidth(), h = img.getHeight();
                int[] px = img.getRGB(0, 0, w, h, null, 0, w);
                boolean leg = h * 2 == w;
                finish(id, new Texture(px, w, h, !leg && source.slim(), leg));
            } catch (Exception e) {
                finish(id, null);
            }
        });
    }

    private static int select(String key) {
        loadedKey = key;
        skin = null;
        ready = null;
        failed = false;
        return ++request;
    }

    private static synchronized void finish(int id, Texture texture) {
        if (id != request) return;
        skin = texture;
        failed = texture == null;
    }

    /** Forgets the loaded key so the next ensureSkin tries again (used after a failure). */
    static synchronized void retry() { select(""); }

    // ------------------------------------------------------------------ the model

    private static final class Box {
        double w, h, d;           // size in the skin (before inflating)
        int u0, v0;               // where its faces start in the skin
        boolean mirror;
        double inflate;
        double pivX, pivY, pivZ;  // what it rotates around (model space)
        double relX, relY, relZ;  // its centre relative to the pivot
        double factor;            // swing amount (radians) for the limb, 0 for none
        // per frame
        double hx, hy, hz, cx, cy, cz, c, s, dlx, dly, dlz;
        int bx0, bx1, by0, by1;   // where the box can appear on screen
        final double[] light = new double[6];
    }

    private static final Box[] BOXES = new Box[14];
    static {
        for (int i = 0; i < BOXES.length; i++) BOXES[i] = new Box();
    }

    private static void part(int i, double w, double h, double d, int u0, int v0, boolean mirror, double inflate,
                             double px, double py, double pz, double rx, double ry, double rz, double factor) {
        Box b = BOXES[i];
        b.w = w; b.h = h; b.d = d; b.u0 = u0; b.v0 = v0; b.mirror = mirror; b.inflate = inflate;
        b.pivX = px; b.pivY = py; b.pivZ = pz; b.relX = rx; b.relY = ry; b.relZ = rz; b.factor = factor;
    }

    /** Lays out the 7 body parts, then their second layers (the hat always, the rest on modern skins). Returns the count. */
    private static int layout(boolean slimArms, boolean old) {
        double aw = slimArms ? 3 : 4;
        double ax = 4 + aw / 2;
        int n = 0;
        part(n++, 8, 8, 8, 0, 0, false, 0, 0, 28, 0, 0, 0, 0, 0);                               // head
        part(n++, 8, 12, 4, 16, 16, false, 0, 0, 18, 0, 0, 0, 0, 0);                            // body
        part(n++, aw, 12, 4, 40, 16, false, 0, -ax, 22, 0, 0, -4, 0, -0.75);                    // right arm
        part(n++, aw, 12, 4, old ? 40 : 32, old ? 16 : 48, old, 0, ax, 22, 0, 0, -4, 0, 0.75);  // left arm
        part(n++, 4, 12, 4, 0, 16, false, 0, -2, 12, 0, 0, -6, 0, 0.85);                        // right leg
        part(n++, 4, 12, 4, old ? 0 : 16, old ? 16 : 48, old, 0, 2, 12, 0, 0, -6, 0, -0.85);    // left leg
        part(n++, 8, 8, 8, 32, 0, false, 0.5, 0, 28, 0, 0, 0, 0, 0);                            // hat
        if (!old) {
            part(n++, 8, 12, 4, 16, 32, false, 0.25, 0, 18, 0, 0, 0, 0, 0);                     // jacket
            part(n++, aw, 12, 4, 40, 32, false, 0.25, -ax, 22, 0, 0, -4, 0, -0.75);             // right sleeve
            part(n++, aw, 12, 4, 48, 48, false, 0.25, ax, 22, 0, 0, -4, 0, 0.75);               // left sleeve
            part(n++, 4, 12, 4, 0, 32, false, 0.25, -2, 12, 0, 0, -6, 0, 0.85);                 // right pants
            part(n++, 4, 12, 4, 0, 48, false, 0.25, 2, 12, 0, 0, -6, 0, -0.85);                 // left pants
        }
        return n;
    }

    // ------------------------------------------------------------------ rendering

    private static int[] buf = new int[0];           // only touched by the render thread
    private static long lastRender;
    private static double yaw = 0.35;

    private record Result(Texture texture, int w, int h, int[] rects) {}

    private static volatile Result ready;
    private static volatile boolean busy;

    private static final double PITCH = 0.2;                       // looking slightly down at the player
    private static final double LX = -0.40, LY = 0.70, LZ = 0.60;  // light direction (towards the light)

    /**
     * The latest picture of the walking player as solid rectangles (x0, y0, x1, y1, ARGB repeated), for a w by h pixel
     * area, or null while the skin loads. The picture is rendered on a background thread about 33 times a second, so
     * the menu never waits for it; until a new one is ready the previous one is returned.
     *
     * @param follow -1..1, how far the mouse is to the left or right of the player
     * @param rs     how many pixels per GUI pixel the area is drawn at (1 = chunky, 3 = fine); only sizes the margins
     */
    static int[] frame(int w, int h, double follow, int rs) {
        Texture s = skin;
        if (s == null || w < 8 || h < 24) return null;
        Result r = ready;
        long now = System.nanoTime();
        boolean current = r != null && r.texture == s && r.w == w && r.h == h;
        boolean stale = !current || now - lastRender >= 30_000_000L;
        if (!busy && stale) {
            busy = true;
            long prev = lastRender;
            lastRender = now;
            Net.POOL.execute(() -> {
                try {
                    double dt = prev == 0 ? 0.016 : Math.min(0.1, (now - prev) / 1e9);
                    if (buf.length != w * h) buf = new int[w * h];
                    else Arrays.fill(buf, 0);
                    yaw += (0.35 + follow * 0.6 - yaw) * Math.min(1.0, dt * 8);
                    render(s.pixels(), s.width(), s.height(), s.slim(), s.legacy(), w, h, rs, now / 1e9);
                    int[][] out = {new int[1024]};
                    int[] count = {0};
                    rects(buf, w, h, (x0, y0, x1, y1, c) -> {
                        if (count[0] + 5 > out[0].length) out[0] = Arrays.copyOf(out[0], out[0].length * 2);
                        int[] o = out[0];
                        int i = count[0];
                        o[i] = x0; o[i + 1] = y0; o[i + 2] = x1; o[i + 3] = y1; o[i + 4] = c;
                        count[0] += 5;
                    });
                    if (s == skin) ready = new Result(s, w, h, Arrays.copyOf(out[0], count[0]));
                } catch (Throwable t) {
                    if (s == skin) failed = true;
                } finally {
                    busy = false;
                }
            });
        }
        return current ? r.rects : null;
    }

    private static void render(int[] tex, int sw, int sh, boolean slimArms, boolean old, int w, int h, int rs, double t) {
        int n = layout(slimArms, old);
        double swing = Math.sin(t * Math.PI * 2 * 1.1);
        double bob = 0.45 * (1 - Math.abs(swing));
        double cy_ = Math.cos(yaw), sy_ = Math.sin(yaw), cp = Math.cos(PITCH), sp = Math.sin(PITCH);
        double k = sw / 64.0;
        double vox = (h - 12.0 * rs) / 33.0;
        double floorRow = h - 6.0 * rs;

        // model -> world direction for lighting: Rx(pitch) * Ry(yaw)
        double ln = Math.sqrt(LX * LX + LY * LY + LZ * LZ), lx = LX / ln, ly = LY / ln, lz = LZ / ln;

        // world -> model for the viewing direction (0, 0, -1)
        double[] dm = inverse(0, 0, -1, cy_, sy_, cp, sp);

        for (int i = 0; i < n; i++) {
            Box b = BOXES[i];
            double th = b.factor * swing;
            b.c = Math.cos(th);
            b.s = Math.sin(th);
            b.hx = b.w / 2 + b.inflate;
            b.hy = b.h / 2 + b.inflate;
            b.hz = b.d / 2 + b.inflate;
            b.cx = b.pivX + b.relX;
            b.cy = b.pivY + (b.relY * b.c - b.relZ * b.s) + bob;
            b.cz = b.pivZ + (b.relY * b.s + b.relZ * b.c);
            // view direction in the box's own space: Rx(-th) * dm
            b.dlx = dm[0];
            b.dly = dm[1] * b.c + dm[2] * b.s;
            b.dlz = -dm[1] * b.s + dm[2] * b.c;
            // light per face: face normal -> model (Rx(th)) -> world (Rx(pitch) Ry(yaw))
            for (int f = 0; f < 6; f++) {
                double nx = f == 3 ? 1 : f == 2 ? -1 : 0, ny = f == 4 ? 1 : f == 5 ? -1 : 0, nz = f == 0 ? 1 : f == 1 ? -1 : 0;
                double my = ny * b.c - nz * b.s, mz = ny * b.s + nz * b.c;       // Rx(th)
                double wx = nx * cy_ + mz * sy_, wz = -nx * sy_ + mz * cy_;       // Ry(yaw)
                double wy = my * cp - wz * sp, wz2 = my * sp + wz * cp;           // Rx(pitch)
                b.light[f] = 0.50 + 0.55 * Math.max(0, wx * lx + wy * ly + wz2 * lz);
            }
            // screen rectangle of the box (its 8 corners), so a pixel only tests the boxes that can cover it
            double minX = 1e30, maxX = -1e30, minY = 1e30, maxY = -1e30;
            for (int q = 0; q < 8; q++) {
                double qx = (q & 1) == 0 ? -b.hx : b.hx, qy = (q & 2) == 0 ? -b.hy : b.hy, qz = (q & 4) == 0 ? -b.hz : b.hz;
                double my = qy * b.c - qz * b.s, mz = qy * b.s + qz * b.c;
                double px = b.cx + qx, py = b.cy + my, pz = b.cz + mz;
                double wx = px * cy_ + pz * sy_, wz = -px * sy_ + pz * cy_;
                double wy = py * cp - wz * sp;
                double scrX = w / 2.0 + wx * vox, scrY = floorRow - wy * vox;
                minX = Math.min(minX, scrX); maxX = Math.max(maxX, scrX);
                minY = Math.min(minY, scrY); maxY = Math.max(maxY, scrY);
            }
            b.bx0 = (int) Math.floor(minX) - 1;
            b.bx1 = (int) Math.ceil(maxX) + 1;
            b.by0 = (int) Math.floor(minY) - 1;
            b.by1 = (int) Math.ceil(maxY) + 1;
        }

        int x0 = Math.max(0, (int) (w / 2.0 - 11 * vox)), x1 = Math.min(w, (int) (w / 2.0 + 11 * vox) + 1);
        int[] rowBoxes = new int[n];
        for (int py = 0; py < h; py++) {
            double yw = (floorRow - (py + 0.5)) / vox;
            if (yw < -4 || yw > 38) continue;
            int rb = 0;
            for (int i = 0; i < n; i++) if (py >= BOXES[i].by0 && py <= BOXES[i].by1) rowBoxes[rb++] = i;
            if (rb == 0) continue;
            // world -> model for this row: Rx(-pitch) then Ry(-yaw), with the ray starting at z = 100
            double y1 = yw * cp + 100 * sp, z1 = -yw * sp + 100 * cp;
            double omy = y1;
            for (int px = x0; px < x1; px++) {
                double xw = (px + 0.5 - w / 2.0) / vox;
                double omx = xw * cy_ - z1 * sy_, omz = xw * sy_ + z1 * cy_;
                double best = Double.MAX_VALUE;
                int bestRgb = 0;
                for (int r = 0; r < rb; r++) {
                    Box b = BOXES[rowBoxes[r]];
                    if (px < b.bx0 || px > b.bx1) continue;
                    double dx = omx - b.cx, dy = omy - b.cy, dz = omz - b.cz;
                    double ox = dx, oy = dy * b.c + dz * b.s, oz = -dy * b.s + dz * b.c;

                    double tn = -1e30, tf = 1e30;
                    int axis = -1;
                    if (Math.abs(b.dlx) < 1e-12) {
                        if (Math.abs(ox) > b.hx) continue;
                    } else {
                        double t1 = (-b.hx - ox) / b.dlx, t2 = (b.hx - ox) / b.dlx;
                        double a = Math.min(t1, t2), c2 = Math.max(t1, t2);
                        if (a > tn) { tn = a; axis = 0; }
                        if (c2 < tf) tf = c2;
                    }
                    if (Math.abs(b.dly) < 1e-12) {
                        if (Math.abs(oy) > b.hy) continue;
                    } else {
                        double t1 = (-b.hy - oy) / b.dly, t2 = (b.hy - oy) / b.dly;
                        double a = Math.min(t1, t2), c2 = Math.max(t1, t2);
                        if (a > tn) { tn = a; axis = 1; }
                        if (c2 < tf) tf = c2;
                    }
                    if (Math.abs(b.dlz) < 1e-12) {
                        if (Math.abs(oz) > b.hz) continue;
                    } else {
                        double t1 = (-b.hz - oz) / b.dlz, t2 = (b.hz - oz) / b.dlz;
                        double a = Math.min(t1, t2), c2 = Math.max(t1, t2);
                        if (a > tn) { tn = a; axis = 2; }
                        if (c2 < tf) tf = c2;
                    }
                    if (axis < 0 || tn > tf || tf < 0 || tn >= best) continue;

                    double hxp = ox + tn * b.dlx, hyp = oy + tn * b.dly, hzp = oz + tn * b.dlz;
                    int face;                                   // 0 front +z, 1 back -z, 2 right -x, 3 left +x, 4 top, 5 bottom
                    if (axis == 0) face = b.dlx < 0 ? 3 : 2;
                    else if (axis == 1) face = b.dly < 0 ? 4 : 5;
                    else face = b.dlz < 0 ? 0 : 1;

                    if (b.mirror) hxp = -hxp;
                    int tf2 = face;
                    if (b.mirror && (face == 2 || face == 3)) tf2 = face == 2 ? 3 : 2;

                    double fx = (hxp + b.hx) / (2 * b.hx) * b.w;
                    double fy = (b.hy - hyp) / (2 * b.hy) * b.h;
                    double fz = (hzp + b.hz) / (2 * b.hz) * b.d;
                    double u, v;
                    switch (tf2) {
                        case 0 -> { u = b.u0 + b.d + fx; v = b.v0 + b.d + fy; }
                        case 1 -> { u = b.u0 + 2 * b.d + b.w + (b.w - fx); v = b.v0 + b.d + fy; }
                        case 2 -> { u = b.u0 + fz; v = b.v0 + b.d + fy; }
                        case 3 -> { u = b.u0 + b.d + b.w + (b.d - fz); v = b.v0 + b.d + fy; }
                        case 4 -> { u = b.u0 + b.d + fx; v = b.v0 + fz; }
                        default -> { u = b.u0 + b.d + b.w + fx; v = b.v0 + (b.d - fz); }
                    }
                    int tu = Math.max(0, Math.min(sw - 1, (int) (u * k)));
                    int tv = Math.max(0, Math.min(sh - 1, (int) (v * k)));
                    int p = tex[tv * sw + tu];
                    if ((p >>> 24) < 16) continue;

                    double lit = b.light[face];
                    int rr = Math.min(255, (int) (((p >> 16) & 255) * lit));
                    int gg = Math.min(255, (int) (((p >> 8) & 255) * lit));
                    int bl = Math.min(255, (int) ((p & 255) * lit));
                    best = tn;
                    bestRgb = 0xFF000000 | (rr << 16) | (gg << 8) | bl;
                }
                if (best != Double.MAX_VALUE) buf[py * w + px] = bestRgb;
            }
        }
    }

    // ------------------------------------------------------------------ drawing helper

    interface Sink {
        void rect(int x0, int y0, int x1, int y1, int argb);
    }

    /**
     * Turns the image into as few solid rectangles as possible (equal-coloured runs that repeat on the next row are
     * joined into one taller rectangle), so drawing it with fills stays cheap even at full resolution.
     */
    static void rects(int[] px, int w, int h, Sink sink) {
        int[] ox0 = new int[w], ox1 = new int[w], oc = new int[w], oy = new int[w];
        int[] nx0 = new int[w], nx1 = new int[w], nc = new int[w], ny = new int[w];
        int on = 0;
        for (int row = 0; row <= h; row++) {
            int nn = 0;
            if (row < h) {
                int i = row * w, col = 0;
                while (col < w) {
                    int c = px[i + col];
                    if ((c >>> 24) == 0) {
                        col++;
                        continue;
                    }
                    int start = col;
                    while (col < w && px[i + col] == c) col++;
                    nx0[nn] = start; nx1[nn] = col; nc[nn] = c; ny[nn] = row;
                    nn++;
                }
            }
            // runs that continue an open rectangle keep its start row; the others start here
            int j = 0;
            for (int k2 = 0; k2 < on; k2++) {
                while (j < nn && nx1[j] <= ox0[k2]) j++;
                if (j < nn && nx0[j] == ox0[k2] && nx1[j] == ox1[k2] && nc[j] == oc[k2]) {
                    ny[j] = oy[k2];
                } else {
                    sink.rect(ox0[k2], oy[k2], ox1[k2], row, oc[k2]);
                }
            }
            on = nn;
            System.arraycopy(nx0, 0, ox0, 0, nn);
            System.arraycopy(nx1, 0, ox1, 0, nn);
            System.arraycopy(nc, 0, oc, 0, nn);
            System.arraycopy(ny, 0, oy, 0, nn);
        }
    }

    /** World -> model space: Rx(-pitch) then Ry(-yaw). */
    private static double[] inverse(double x, double y, double z, double cyw, double syw, double cp, double sp) {
        double y1 = y * cp + z * sp, z1 = -y * sp + z * cp;
        return new double[]{x * cyw - z1 * syw, y1, x * syw + z1 * cyw};
    }

    private DollRenderer() {}
}
