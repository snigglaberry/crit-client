package dev.crit.client.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.crit.client.CritClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/** The module registry and its config file. Every module is off until the player turns it on. */
public final class Modules {
    public static final OverlayModule OVERLAY = new OverlayModule();
    public static final SprintModule SPRINT = new SprintModule();
    public static final CrosshairModule CROSSHAIR = new CrosshairModule();
    public static final ZoomModule ZOOM = new ZoomModule();
    public static final OverlaysModule OVERLAYS = new OverlaysModule();
    public static final ArmorModule ARMOR = new ArmorModule();
    public static final FullbrightModule FULLBRIGHT = new FullbrightModule();
    public static final BlockOutlineModule BLOCK_OUTLINE = new BlockOutlineModule();
    public static final SkyModule SKY = new SkyModule();
    public static final MotionBlurModule MOTION_BLUR = new MotionBlurModule();
    public static final SubwayModule SUBWAY = new SubwayModule();
    public static final ScoreboardModule SCOREBOARD = new ScoreboardModule();

    private static final List<Module> ALL = List.of(OVERLAY, OVERLAYS, ARMOR, SPRINT, CROSSHAIR, ZOOM, FULLBRIGHT, MOTION_BLUR, BLOCK_OUTLINE, SKY, SUBWAY, SCOREBOARD);

    private static boolean dirty;

    public static List<Module> all() {
        return ALL;
    }

    public static Module byId(String id) {
        for (Module m : ALL) if (m.id.equals(id)) return m;
        return null;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("critclient-modules.json");
    }

    public static void load() {
        if (!Files.exists(file())) return;
        try {
            JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
            for (Module m : ALL) {
                if (o.has(m.id)) m.read(o.getAsJsonObject(m.id));
            }
        } catch (Exception e) {
            CritClient.LOG.warn("Could not read module config", e);
        }
    }

    public static void save() {
        dirty = true;
    }

    /** Writes the config if anything changed since the last tick. Called once per client tick. */
    public static void tick() {
        if (!dirty) return;
        dirty = false;
        try {
            JsonObject o = new JsonObject();
            for (Module m : ALL) o.add(m.id, m.write());
            Files.writeString(file(), o.toString());
        } catch (IOException e) {
            CritClient.LOG.warn("Could not save module config", e);
        }
    }

    private Modules() {}
}
