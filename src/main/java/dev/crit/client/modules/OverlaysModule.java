package dev.crit.client.modules;

import com.google.gson.JsonObject;
import dev.crit.client.keys.KeyConfig;

/** The HUD readouts: CPS, sprint indicator, ping, FPS, TPS, speedometer, coordinates and compass. Clicking it opens the drag editor. */
public final class OverlaysModule extends Module {
    public OverlaysModule() {
        super("overlays", "Overlays", "CPS, ping, FPS, TPS, sprint indicator, speedometer, coordinates and a compass you can drag around the HUD.", false);
    }

    @Override
    public boolean enabled() {
        return KeyConfig.overlaysEnabled;
    }

    @Override
    public void setEnabled(boolean on) {
        if (KeyConfig.overlaysEnabled == on) return;
        KeyConfig.overlaysEnabled = on;
        KeyConfig.save();
        Modules.save();
    }

    @Override
    void read(JsonObject o) {
        KeyConfig.overlaysEnabled = o != null && o.has("enabled") && o.get("enabled").getAsBoolean();
    }

    @Override
    JsonObject write() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", KeyConfig.overlaysEnabled);
        return o;
    }

}
