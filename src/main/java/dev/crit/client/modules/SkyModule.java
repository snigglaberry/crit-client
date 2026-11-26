package dev.crit.client.modules;

import com.google.gson.JsonObject;
import dev.crit.client.sky.CustomSky;

/**
 * Replaces the overworld sky with a colour of your choice or with an image (see SkyRendererMixin and
 * {@link CustomSky}). The image can be a 360 degree panorama or the same picture on every side.
 */
public final class SkyModule extends Module {
    public enum Mode { COLOR, IMAGE }
    public enum Style { PANORAMA, SIDES }

    private Mode mode = Mode.COLOR;
    private Style style = Style.PANORAMA;
    private int color = 0x78A7FF;     // RGB
    private boolean mirror = false;
    private String imageName = "";

    public SkyModule() {
        super("sky", "Custom Sky", "Change the sky to a colour you pick or to an image of your own.", false);
    }


    public Mode mode() { return mode; }
    public void mode(Mode m) { this.mode = m; Modules.save(); }

    public Style style() { return style; }
    public void style(Style s) {
        this.style = s;
        Modules.save();
        CustomSky.reload();
    }

    public int color() { return color; }
    public void color(int rgb) { this.color = rgb & 0xFFFFFF; Modules.save(); }

    public boolean mirror() { return mirror; }
    public void mirror(boolean b) {
        this.mirror = b;
        Modules.save();
        CustomSky.reload();
    }

    public String imageName() { return imageName; }
    public void imageName(String n) { this.imageName = n == null ? "" : n; Modules.save(); }

    @Override
    void read(JsonObject o) {
        super.read(o);
        if (o == null) return;
        try {
            if (o.has("mode")) mode = Mode.valueOf(o.get("mode").getAsString());
            if (o.has("style")) style = Style.valueOf(o.get("style").getAsString());
        } catch (IllegalArgumentException ignored) {
            // keep the defaults
        }
        if (o.has("color")) color = o.get("color").getAsInt() & 0xFFFFFF;
        if (o.has("mirror")) mirror = o.get("mirror").getAsBoolean();
        if (o.has("imageName")) imageName = o.get("imageName").getAsString();
    }

    @Override
    JsonObject write() {
        JsonObject o = super.write();
        o.addProperty("mode", mode.name());
        o.addProperty("style", style.name());
        o.addProperty("color", color);
        o.addProperty("mirror", mirror);
        o.addProperty("imageName", imageName);
        return o;
    }
}
