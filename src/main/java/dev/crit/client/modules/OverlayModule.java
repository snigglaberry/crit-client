package dev.crit.client.modules;

import com.google.gson.JsonObject;
import dev.crit.client.keys.KeyConfig;

/** The key overlay: WASD, the space bar and the mouse, drawn on the HUD. Clicking it opens the drag editor. */
public final class OverlayModule extends Module {
    public OverlayModule() {
        super("overlay", "Key Overlay", "WASD, space bar and mouse buttons you can drag around the HUD.", false);
    }

    @Override
    public boolean enabled() {
        return KeyConfig.overlayEnabled;
    }

    @Override
    public void setEnabled(boolean on) {
        if (KeyConfig.overlayEnabled == on) return;
        KeyConfig.overlayEnabled = on;
        KeyConfig.save();
        Modules.save();
    }

    @Override
    void read(JsonObject o) {
        KeyConfig.overlayEnabled = o != null && o.has("enabled") && o.get("enabled").getAsBoolean();
    }

    @Override
    JsonObject write() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", KeyConfig.overlayEnabled);
        return o;
    }

}
