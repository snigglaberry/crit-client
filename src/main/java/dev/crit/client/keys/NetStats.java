package dev.crit.client.keys;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;

/**
 * Ping and server TPS as seen from the client, for any server (not just singleplayer).
 * <ul>
 *   <li>TPS: the server sends a time update every 20 ticks, so the real time between two of them tells how fast it ticks.</li>
 *   <li>Ping: we send a ping request about once a second and time the pong.</li>
 * </ul>
 * ClientPacketListenerMixin feeds the incoming packets in.
 */
public final class NetStats {
    private static long lastTime;      // nanoTime of the last time update
    private static double tpsAvg = -1;
    private static long lastSendMs, sentMs;
    private static double pingAvg = -1;
    private static long lastPongMs;

    // ---- TPS

    public static synchronized void onTimeUpdate() {
        long now = System.nanoTime();
        if (lastTime != 0) {
            double dt = (now - lastTime) / 1e9;
            if (dt > 6) {
                tpsAvg = -1;                           // a long gap: new world / server, start over
            } else if (dt >= 0.3) {                    // the extra update sent right after joining is shorter than that
                double t = Math.min(20.0, 20.0 / dt);
                tpsAvg = tpsAvg < 0 ? t : tpsAvg * 0.7 + t * 0.3;
            }
        }
        lastTime = now;
    }

    /** Whole ticks per second (max 20), or -1 when there is no recent data. */
    public static synchronized int tps() {
        if (lastTime == 0 || tpsAvg < 0 || System.nanoTime() - lastTime > 4_000_000_000L) return -1;
        return (int) Math.round(tpsAvg);
    }

    // ---- ping

    /** Call every frame while the ping overlay is on; sends a ping request about once a second. */
    public static void tickPing(Minecraft mc) {
        long now = System.currentTimeMillis();
        var conn = mc.getConnection();
        if (conn == null || now - lastSendMs < 1000) return;
        lastSendMs = now;
        synchronized (NetStats.class) { sentMs = now; }
        conn.send(new ServerboundPingRequestPacket(now));
    }

    public static synchronized void onPong(long echoed) {
        if (echoed != sentMs) return; // not ours (the F3 ping chart sends its own)
        double rtt = Math.max(0, System.currentTimeMillis() - echoed);
        pingAvg = pingAvg < 0 ? rtt : pingAvg * 0.6 + rtt * 0.4;
        lastPongMs = System.currentTimeMillis();
    }

    /** Round trip in ms, or -1 when there is no recent answer. */
    public static synchronized int ping() {
        if (pingAvg < 0 || System.currentTimeMillis() - lastPongMs > 6000) return -1;
        return (int) Math.round(pingAvg);
    }

    private NetStats() {}
}
