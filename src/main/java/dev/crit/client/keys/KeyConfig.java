package dev.crit.client.keys;

import dev.crit.client.CritClient;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Settings for all overlays. Positions are fractions of the screen so they survive resolution changes. */
public final class KeyConfig {
    public enum Kind { WASD, MOUSE, CPS, STATUS, PING, FPS, TPS, SPEED, COORDS, COMPASS, ARMOR }

    public static final float MIN_SCALE = 0.5f, MAX_SCALE = 5.0f;

    /** Space bar under WASD. */
    public static boolean showSpace = true;

    /** Master switch for the key overlay (WASD, space, mouse), owned by the Key Overlay module. Off until switched on. */
    public static boolean overlayEnabled = false;

    /** Master switch for the readouts (CPS, ping, FPS, TPS, sprint, speed, coordinates), owned by the Overlays module. */
    public static boolean overlaysEnabled = false;

    public static final class Overlay {
        public final Kind kind;
        private final float defX, defY, defScale;
        public float x, y, scale = 1.0f;
        public boolean enabled = true;

        Overlay(Kind kind, float defX, float defY, float defScale) {
            this(kind, defX, defY, defScale, true);
        }

        Overlay(Kind kind, float defX, float defY, float defScale, boolean on) {
            this.enabled = on;
            this.kind = kind;
            this.defX = defX;
            this.defY = defY;
            this.defScale = defScale;
            this.scale = defScale;
            this.x = defX;
            this.y = defY;
        }

        public int w() { return OverlayRenderer.width(kind, scale); }
        public int h() { return OverlayRenderer.height(kind, scale, showSpace); }

        public void reset() {
            x = defX;
            y = defY;
            scale = defScale;
        }

        /** Keeps the whole overlay on screen for the given gui size. */
        public void clamp(int sw, int sh) {
            scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));
            x = Math.max(0f, Math.min(x, Math.max(0, sw - w()) / (float) sw));
            y = Math.max(0f, Math.min(y, Math.max(0, sh - h()) / (float) sh));
        }

        void read(JsonObject o) {
            if (o.has("x")) x = o.get("x").getAsFloat();
            if (o.has("y")) y = o.get("y").getAsFloat();
            if (o.has("scale")) scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, o.get("scale").getAsFloat()));
            if (o.has("enabled")) enabled = o.get("enabled").getAsBoolean();
        }

        JsonObject write() {
            JsonObject o = new JsonObject();
            o.addProperty("x", x);
            o.addProperty("y", y);
            o.addProperty("scale", scale);
            o.addProperty("enabled", enabled);
            return o;
        }
    }

    public static final Overlay wasd = new Overlay(Kind.WASD, 0.02f, 0.66f, 1.0f);
    public static final Overlay mouse = new Overlay(Kind.MOUSE, 0.20f, 0.62f, 1.0f);
    public static final Overlay cps = new Overlay(Kind.CPS, 0.30f, 0.66f, 0.75f);
    public static final Overlay status = new Overlay(Kind.STATUS, 0.80f, 0.22f, 0.75f, false);

    public static final Overlay ping = new Overlay(Kind.PING, 0.80f, 0.04f, 0.75f, false);
    public static final Overlay fps = new Overlay(Kind.FPS, 0.80f, 0.10f, 0.75f, false);
    public static final Overlay tps = new Overlay(Kind.TPS, 0.80f, 0.16f, 0.75f, false);

    public static final Overlay speed = new Overlay(Kind.SPEED, 0.80f, 0.28f, 0.75f, false);
    public static final Overlay coords = new Overlay(Kind.COORDS, 0.02f, 0.04f, 0.75f, false);

    public static final Overlay compass = new Overlay(Kind.COMPASS, 0.30f, 0.04f, 1.0f, false);
    public static final Overlay armor = new Overlay(Kind.ARMOR, 0.02f, 0.30f, 1.0f, true);

    /** Master switch for the armour HUD, owned by the Armour HUD module. Off until switched on. */
    public static boolean armorEnabled = false;

    /** Armour HUD: pieces stacked sideways instead of top to bottom. */
    public static boolean armorHorizontal = false;
    /** Armour HUD durability: 0 = bar under each piece, 1 = number, 2 = off. */
    public static int armorDurability = 0;

    public static Overlay[] all() { return new Overlay[]{wasd, mouse, cps, status, ping, fps, tps, speed, coords, compass, armor}; }

    /** What the Key Overlay module owns. */
    public static Overlay[] keys() { return new Overlay[]{wasd, mouse}; }

    /** What the Overlays module owns. */
    public static Overlay[] hud() { return new Overlay[]{cps, status, ping, fps, tps, speed, coords, compass}; }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("chezkeyoverlay.json");
    }

    public static void load() {
        try {
            if (!Files.exists(file())) return;
            JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
            if (o.has("wasd")) wasd.read(o.getAsJsonObject("wasd"));
            else wasd.read(o); // older single-overlay file
            if (o.has("mouse")) mouse.read(o.getAsJsonObject("mouse"));
            if (o.has("cps")) cps.read(o.getAsJsonObject("cps"));
            if (o.has("status")) status.read(o.getAsJsonObject("status"));
            if (o.has("ping")) ping.read(o.getAsJsonObject("ping"));
            if (o.has("fps")) fps.read(o.getAsJsonObject("fps"));
            if (o.has("tps")) tps.read(o.getAsJsonObject("tps"));
            if (o.has("speed")) speed.read(o.getAsJsonObject("speed"));
            if (o.has("coords")) coords.read(o.getAsJsonObject("coords"));
            if (o.has("compass")) compass.read(o.getAsJsonObject("compass"));
            if (o.has("armor")) armor.read(o.getAsJsonObject("armor"));
            armor.enabled = true; // the module's own switch decides now, never this flag
            if (o.has("armorEnabled")) armorEnabled = o.get("armorEnabled").getAsBoolean();
            if (o.has("armorHorizontal")) armorHorizontal = o.get("armorHorizontal").getAsBoolean();
            if (o.has("armorDurability")) armorDurability = Math.max(0, Math.min(2, o.get("armorDurability").getAsInt()));
            // files saved before the extra overlays were "off by default" have them switched on; reset those once
            if (!o.has("version")) {
                status.enabled = false;
                ping.enabled = false;
                fps.enabled = false;
                tps.enabled = false;
            }
            if (o.has("showSpace")) showSpace = o.get("showSpace").getAsBoolean();
            if (o.has("overlayEnabled")) overlayEnabled = o.get("overlayEnabled").getAsBoolean();
            if (o.has("overlaysEnabled")) overlaysEnabled = o.get("overlaysEnabled").getAsBoolean();
        } catch (Exception e) {
            CritClient.LOG.warn("Could not read key overlay config", e);
        }
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            o.add("wasd", wasd.write());
            o.add("mouse", mouse.write());
            o.add("cps", cps.write());
            o.add("status", status.write());
            o.add("ping", ping.write());
            o.add("fps", fps.write());
            o.add("tps", tps.write());
            o.add("speed", speed.write());
            o.add("coords", coords.write());
            o.add("compass", compass.write());
            o.add("armor", armor.write());
            o.addProperty("armorEnabled", armorEnabled);
            o.addProperty("armorHorizontal", armorHorizontal);
            o.addProperty("armorDurability", armorDurability);
            o.addProperty("version", 4);
            o.addProperty("showSpace", showSpace);
            o.addProperty("overlayEnabled", overlayEnabled);
            o.addProperty("overlaysEnabled", overlaysEnabled);
            Files.writeString(file(), o.toString());
        } catch (IOException e) {
            CritClient.LOG.warn("Could not save key overlay config", e);
        }
    }

    private KeyConfig() {}
}
