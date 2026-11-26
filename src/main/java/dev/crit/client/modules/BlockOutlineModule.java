package dev.crit.client.modules;

import com.google.gson.JsonObject;

/** Recolours and resizes the outline drawn around the block you are looking at (see LevelRendererMixin). */
public final class BlockOutlineModule extends Module {
    private int color = 0x5DBB3A;      // RGB
    private double opacity = 1.0;      // 0.1 .. 1
    private double thickness = 1.5;    // multiplier on the game's own line width, 0.5 .. 5
    private boolean rainbow = false;

    public BlockOutlineModule() {
        super("blockOutline", "Block Outline", "Pick the colour, thickness and opacity of the outline around the block you look at.", false);
    }


    public int color() { return color; }
    public void color(int rgb) { this.color = rgb & 0xFFFFFF; Modules.save(); }

    public double opacity() { return opacity; }
    public void opacity(double v) { this.opacity = Math.max(0.1, Math.min(1.0, v)); Modules.save(); }

    public double thickness() { return thickness; }
    public void thickness(double v) { this.thickness = Math.max(0.5, Math.min(5.0, v)); Modules.save(); }

    public boolean rainbow() { return rainbow; }
    public void rainbow(boolean b) { this.rainbow = b; Modules.save(); }

    /** The outline colour for this frame as ARGB. */
    public int argb() {
        int rgb = color;
        if (rainbow) {
            float hue = (System.currentTimeMillis() % 6000L) / 6000f;
            rgb = java.awt.Color.HSBtoRGB(hue, 0.85f, 1f) & 0xFFFFFF;
        }
        int a = (int) Math.round(opacity * 255.0);
        return (a << 24) | rgb;
    }

    @Override
    void read(JsonObject o) {
        super.read(o);
        if (o == null) return;
        if (o.has("color")) color = o.get("color").getAsInt() & 0xFFFFFF;
        if (o.has("opacity")) opacity = Math.max(0.1, Math.min(1.0, o.get("opacity").getAsDouble()));
        if (o.has("thickness")) thickness = Math.max(0.5, Math.min(5.0, o.get("thickness").getAsDouble()));
        if (o.has("rainbow")) rainbow = o.get("rainbow").getAsBoolean();
    }

    @Override
    JsonObject write() {
        JsonObject o = super.write();
        o.addProperty("color", color);
        o.addProperty("opacity", opacity);
        o.addProperty("thickness", thickness);
        o.addProperty("rainbow", rainbow);
        return o;
    }
}
