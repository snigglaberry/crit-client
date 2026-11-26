package dev.crit.client.modules;

import com.google.gson.JsonObject;
import net.minecraft.client.DeltaTracker;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

/**
 * Hold a key to zoom in. The zoom eases in fast and settles slower (or snaps straight to the limit if
 * smooth zooming is off). The key, the limit and the speed all live in the module's settings.
 */
public final class ZoomModule extends Module {
    private int key = GLFW.GLFW_KEY_Z;
    private double limit = 4.0;   // how far it zooms in (4 = a quarter of the FOV)
    private double speed = 6.0;   // 1..10, how fast the easing runs
    private boolean smooth = true;

    private double current = 1.0; // FOV multiplier: 1 = no zoom

    public ZoomModule() {
        super("zoom", "Zoom", "Hold a key to zoom in smoothly, up to a limit you choose.", false);
    }


    // ------------------------------------------------------------------ settings

    public int key() { return key; }
    public void key(int k) { this.key = k; Modules.save(); }

    public String keyName() {
        try {
            return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
        } catch (Exception e) {
            return "?";
        }
    }

    public double limit() { return limit; }
    public void limit(double v) { this.limit = Math.max(1.5, Math.min(10.0, v)); Modules.save(); }

    public double speed() { return speed; }
    public void speed(double v) { this.speed = Math.max(1.0, Math.min(10.0, v)); Modules.save(); }

    public boolean smooth() { return smooth; }
    public void smooth(boolean b) { this.smooth = b; Modules.save(); }

    // ------------------------------------------------------------------ zoom

    /** Advances the zoom animation. Called once per frame from the camera FOV hook. */
    public void advance(double dtSeconds) {
        if (!enabled()) {
            current = 1.0;
            return;
        }
        double target = held() ? 1.0 / limit : 1.0;
        if (!smooth) {
            current = target;
            return;
        }
        double rate = 3.0 + speed * 2.2;                 // per-second easing rate
        double k = 1.0 - Math.exp(-rate * Math.max(0.0, dtSeconds));
        current += (target - current) * k;
        if (Math.abs(target - current) < 0.002) current = target;
    }

    /** The FOV multiplier for the current frame. */
    public float factor() {
        return (float) current;
    }

    private boolean held() {
        if (dev.crit.client.CritClient.currentScreen() != null) return false; // never zoom while a menu is open
        long win = GLFW.glfwGetCurrentContext();
        if (win == 0L) return false;
        return GLFW.glfwGetKey(win, key) == GLFW.GLFW_PRESS;
    }

    @Override
    public void onDisable() {
        current = 1.0;
    }

    // ------------------------------------------------------------------ persistence

    @Override
    void read(JsonObject o) {
        super.read(o);
        if (o == null) return;
        if (o.has("key")) key = o.get("key").getAsInt();
        if (o.has("limit")) limit = o.get("limit").getAsDouble();
        if (o.has("speed")) speed = o.get("speed").getAsDouble();
        if (o.has("smooth")) smooth = o.get("smooth").getAsBoolean();
    }

    @Override
    JsonObject write() {
        JsonObject o = super.write();
        o.addProperty("key", key);
        o.addProperty("limit", limit);
        o.addProperty("speed", speed);
        o.addProperty("smooth", smooth);
        return o;
    }
}
