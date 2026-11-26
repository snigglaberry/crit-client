package dev.crit.client.modules;

import com.google.gson.JsonObject;

/** Smooths fast camera movement by blending the last few frames into the current one (see fx.MotionBlur). */
public final class MotionBlurModule extends Module {
    private double strength = 0.45;   // 0.1 .. 0.9

    public MotionBlurModule() {
        super("motionBlur", "Motion Blur", "Blends the last few frames into the current one so fast turning leaves a soft trail.", false);
    }


    public double strength() { return strength; }
    public void strength(double v) { this.strength = Math.max(0.1, Math.min(0.9, v)); Modules.save(); }

    @Override
    void read(JsonObject o) {
        super.read(o);
        if (o != null && o.has("strength")) strength = Math.max(0.1, Math.min(0.9, o.get("strength").getAsDouble()));
    }

    @Override
    JsonObject write() {
        JsonObject o = super.write();
        o.addProperty("strength", strength);
        return o;
    }
}
