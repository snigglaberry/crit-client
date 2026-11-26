package dev.crit.client.keys;

import net.minecraft.client.Minecraft;

/** Horizontal speed of your player in blocks per second, measured between client ticks and smoothed a little. */
public final class SpeedTracker {
    private static double lastX, lastZ, speed;
    private static boolean has;

    /** Call once per client tick. */
    public static void tick(Minecraft mc) {
        var p = mc.player;
        if (p == null) {
            has = false;
            speed = 0;
            return;
        }
        double x = p.getX(), z = p.getZ();
        if (has) {
            double d = Math.hypot(x - lastX, z - lastZ);
            speed = d > 8 ? 0 : speed * 0.6 + d * 20.0 * 0.4; // a huge jump is a teleport, not speed
        }
        lastX = x;
        lastZ = z;
        has = true;
    }

    public static double blocksPerSecond() {
        return speed < 0.05 ? 0 : speed;
    }

    private SpeedTracker() {}
}
