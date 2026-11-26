package dev.crit.client.modules;

import com.google.gson.JsonObject;
import dev.crit.client.keys.KeyConfig;

/** The armour HUD: the armour you wear with each piece's icon and durability. Clicking it opens the drag editor. */
public final class ArmorModule extends Module {
    public ArmorModule() {
        super("armor", "Armour HUD", "The armour you wear with its item icons and durability as a bar or a number. Stack it up and down or sideways.", false);
    }

    @Override
    public boolean enabled() {
        return KeyConfig.armorEnabled;
    }

    @Override
    public void setEnabled(boolean on) {
        if (KeyConfig.armorEnabled == on) return;
        KeyConfig.armorEnabled = on;
        KeyConfig.save();
        Modules.save();
    }

    @Override
    void read(JsonObject o) {
        KeyConfig.armorEnabled = o != null && o.has("enabled") && o.get("enabled").getAsBoolean();
    }

    @Override
    JsonObject write() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", KeyConfig.armorEnabled);
        return o;
    }

}
