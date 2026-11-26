package dev.crit.client.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.crit.client.CritClient;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** The shared UI palette and its saved preferences. {@link UiThemes} controls how it is drawn. */
public final class Theme {
    public enum Colorway {
        DARK("Dark", 0xFF1E1E1E), AMOLED("AMOLED", 0xFF000000),
        BLUE("Dark Blue", 0xFF14213D), GREEN("Dark Green", 0xFF12261C);

        private final String label;
        private final int swatch;

        Colorway(String label, int swatch) {
            this.label = label;
            this.swatch = swatch;
        }

        public String label() { return label; }
        public int swatch() { return swatch; }
    }

    // Fully opaque: a colour with a zero alpha channel draws as nothing, which silently blanks out
    // every accent fill, icon, link and switch knob in the popup.
    public static final int[] ACCENTS = {
            0xFF5DBB3A, 0xFF37B7C9, 0xFF4F86E0, 0xFF9A6BE0, 0xFFE0534B, 0xFFE0A23A, 0xFFE060A8, 0xFFC8C8C8};

    private static Colorway colorway = Colorway.DARK;
    private static int accent = 0;
    private static int panelOpacity = 100;

    public static Colorway colorway() { return colorway; }
    public static int accentIndex() { return accent; }
    public static int accentColor() { return ACCENTS[accent]; }
    public static int panelOpacity() { return panelOpacity; }

    /** Apply the slider immediately; the settings page saves once when dragging finishes. */
    public static void previewPanelOpacity(int percent) {
        panelOpacity = Math.clamp(percent, 0, 100);
    }

    public static void setColorway(Colorway next) {
        colorway = next;
        apply();
        save();
    }

    public static void setAccent(int i) {
        accent = Math.floorMod(i, ACCENTS.length);
        apply();
        save();
    }

    /** Recomputes every {@link Ui} colour from the current colorway and accent. */
    public static void apply() {
        int acc = ACCENTS[accent];
        int panel, header, sidebar, edge, edgeLight, tile, tileHover, field, text, muted, dim, backdrop;
        if (colorway == Colorway.BLUE) {
            panel = 0xFF121B2E;
            header = 0xFF0E1626;
            sidebar = 0xFF0F1829;
            edge = 0xFF22314D;
            edgeLight = 0xFF31456B;
            tile = 0xFF182440;
            tileHover = 0xFF1F2F52;
            field = 0xFF0B1220;
            text = 0xFFE3EAF8;
            muted = 0xFF93A3C2;
            dim = 0xFF8494B4;
            backdrop = 0xA0000008;
        } else if (colorway == Colorway.GREEN) {
            panel = 0xFF10211A;
            header = 0xFF0C1913;
            sidebar = 0xFF0D1B15;
            edge = 0xFF1F3A2E;
            edgeLight = 0xFF2D5342;
            tile = 0xFF163026;
            tileHover = 0xFF1E4034;
            field = 0xFF08140F;
            text = 0xFFE2F2E8;
            muted = 0xFF8FB09C;
            dim = 0xFF799783;
            backdrop = 0xA0000A04;
        } else if (colorway == Colorway.AMOLED) {
            panel = 0xFF000000;
            header = 0xFF000000;
            sidebar = 0xFF000000;
            edge = 0xFF202020;
            edgeLight = 0xFF303030;
            tile = 0xFF0B0B0B;
            tileHover = 0xFF181818;
            field = 0xFF060606;
            text = 0xFFE8E8E8;
            muted = 0xFF9A9A9A;
            dim = 0xFF828282;
            backdrop = 0xA0000000;
        } else {
            panel = 0xFF1E1E1E;
            header = 0xFF181818;
            sidebar = 0xFF191919;
            edge = 0xFF2E2E2E;
            edgeLight = 0xFF3C3C3C;
            tile = 0xFF262626;
            tileHover = 0xFF313131;
            field = 0xFF141414;
            text = 0xFFE6E6E6;
            muted = 0xFF9A9A9A;
            dim = 0xFF898989;
            backdrop = 0xA0000000;
        }
        Ui.BACKDROP = backdrop;
        Ui.PANEL = panel;
        Ui.HEADER = header;
        Ui.SIDEBAR = sidebar;
        Ui.EDGE = edge;
        Ui.EDGE_LIGHT = edgeLight;
        Ui.TILE = tile;
        Ui.TILE_HOVER = tileHover;
        Ui.FIELD = field;
        Ui.ACCENT = acc | 0xFF000000;
        Ui.ACCENT_HOVER = lighten(acc, 0.20f);
        Ui.ACCENT_DARK = darken(acc, 0.46f);
        Ui.TEXT = text;
        Ui.MUTED = muted;
        Ui.DIM = dim;
    }

    public static int lighten(int c, float f) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        r = Math.round(r + (255 - r) * f);
        g = Math.round(g + (255 - g) * f);
        b = Math.round(b + (255 - b) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static int darken(int c, float f) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        return 0xFF000000 | (Math.round(r * (1 - f)) << 16) | (Math.round(g * (1 - f)) << 8) | Math.round(b * (1 - f));
    }

    /** Picks the more legible text color for an opaque swatch or accent. */
    public static int onColor(int c) {
        double luminance = 0.2126 * linear(c >> 16) + 0.7152 * linear(c >> 8) + 0.0722 * linear(c);
        return luminance > 0.18 ? 0xFF080808 : 0xFFFFFFFF;
    }

    private static double linear(int channel) {
        double srgb = (channel & 0xFF) / 255.0;
        return srgb <= 0.04045 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("critclient-theme.json");
    }

    public static void load() {
        try {
            if (Files.exists(file())) {
                JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
                if (o.has("colorway") || o.has("mode")) {
                    try {
                        colorway = Colorway.valueOf(o.get(o.has("colorway") ? "colorway" : "mode").getAsString());
                    } catch (IllegalArgumentException ignored) {
                        colorway = Colorway.DARK; // Includes old configs that used Light mode.
                    }
                }
                if (o.has("accent")) accent = Math.floorMod(o.get("accent").getAsInt(), ACCENTS.length);
                if (o.has("panelOpacity")) previewPanelOpacity(o.get("panelOpacity").getAsInt());
                if (o.has("theme")) UiThemes.restore(o.get("theme").getAsString());
            }
        } catch (Exception e) {
            CritClient.LOG.warn("Could not read theme", e);
        }
        apply();
    }

    public static void save() {
        try {
            JsonObject o = new JsonObject();
            o.addProperty("theme", UiThemes.current().id());
            o.addProperty("colorway", colorway.name());
            o.addProperty("accent", accent);
            o.addProperty("panelOpacity", panelOpacity);
            Files.writeString(file(), o.toString());
        } catch (IOException e) {
            CritClient.LOG.warn("Could not save theme", e);
        }
    }

    private Theme() {}
}
