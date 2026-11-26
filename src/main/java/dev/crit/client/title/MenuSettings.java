package dev.crit.client.title;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.crit.client.CritClient;
import java.nio.file.Files;
import java.nio.file.Path;

/** Main menu options saved in config/skinstudio/menu.json (set in the Settings tab). */
public final class MenuSettings {
    private static boolean panorama = true;
    private static boolean customBackground;

    public static void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
                if (o.has("panorama")) panorama = o.get("panorama").getAsBoolean();
                if (o.has("customBackground")) customBackground = o.get("customBackground").getAsBoolean();
            }
        } catch (Exception e) {
            CritClient.LOG.warn("Could not read main menu settings", e);
        }
    }

    private static Path file() { return CritClient.DIR.resolve("menu.json"); }

    /** Whether to use Crit's panorama instead of Minecraft's when no still image is shown. */
    public static boolean panorama() { return panorama; }
    public static boolean customBackground() { return customBackground; }

    public static void setPanorama(boolean on) {
        panorama = on;
        save();
    }

    public static void setCustomBackground(boolean on) {
        customBackground = on;
        save();
    }

    private static void save() {
        try {
            Path f = file();
            Files.createDirectories(f.getParent());
            JsonObject o = new JsonObject();
            o.addProperty("panorama", panorama);
            o.addProperty("customBackground", customBackground);
            Files.writeString(f, o.toString());
        } catch (Exception e) {
            CritClient.LOG.warn("Could not save main menu settings", e);
        }
    }

    private MenuSettings() {}
}
