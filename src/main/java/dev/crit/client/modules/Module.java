package dev.crit.client.modules;

import com.google.gson.JsonObject;

/**
 * One toggleable Crit Client feature. Modules are off until the player turns them on from the
 * Modules tab; each one persists its own settings through {@link Modules}.
 */
public abstract class Module {
    public final String id;
    public final String name;
    public final String desc;
    private boolean enabled;

    protected Module(String id, String name, String desc, boolean defaultOn) {
        this.id = id;
        this.name = name;
        this.desc = desc;
        this.enabled = defaultOn;
    }

    public boolean enabled() {
        return enabled;
    }

    /** Turns the module on or off and runs its side effects. Modules that store state elsewhere override the read/write pair. */
    public void setEnabled(boolean on) {
        if (enabled == on) return;
        enabled = on;
        if (on) onEnable();
        else onDisable();
        Modules.save();
    }

    public final void toggle() {
        setEnabled(!enabled());
    }

    public void onEnable() {}

    public void onDisable() {}

    /** Called once per client tick, only while the module is on. */
    public void tick(net.minecraft.client.Minecraft mc) {}

    void read(JsonObject o) {
        if (o != null && o.has("enabled")) enabled = o.get("enabled").getAsBoolean();
    }

    JsonObject write() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", enabled);
        return o;
    }

    protected final void setStored(boolean on) {
        enabled = on;
    }
}
