package dev.crit.client.ui;

import java.util.List;

/** Built-in UI styles, independent of the user's colorway, accent and opacity. */
public final class UiThemes {
    public record Preset(String id, String name, String description, int radius, int panelRadius, boolean vanillaFont) {}

    private static final Preset MINECRAFT = new Preset("minecraft", "Minecraft", "Flat Crit layout with Minecraft's font", 0, 0, true);
    private static final Preset SMOOTH = new Preset("smooth", "Smooth", "Soft corners and clean outlines", 5, 10, false);
    private static final List<Preset> BUILT_INS = List.of(MINECRAFT, SMOOTH);

    private static Preset current = SMOOTH;

    public static Preset current() { return current; }
    public static List<Preset> available() { return BUILT_INS; }
    static boolean usesVanillaFont() { return current.vanillaFont(); }
    public static boolean rounded() { return current.radius() > 0; }

    public static void select(Preset preset) {
        Preset previous = current;
        restore(preset.id());
        if (current != previous) Theme.save();
    }

    /** Keep the old Basic selection working; removed and unknown themes fall back to Smooth. */
    static void restore(String id) {
        String resolved = "basic".equals(id) ? MINECRAFT.id() : id;
        current = BUILT_INS.stream().filter(preset -> preset.id().equals(resolved)).findFirst().orElse(SMOOTH);
    }

    private UiThemes() {}
}
