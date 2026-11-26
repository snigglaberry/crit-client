package dev.crit.client.store;

/** The three things the store can browse. */
public enum Kind {
    RESOURCEPACK("resourcepack", "resourcepacks", "Resource Packs", null),
    MOD("mod", "mods", "Mods", "fabric"),
    SHADER("shader", "shaderpacks", "Shaders", null);

    /** Modrinth project_type */
    public final String type;
    /** Folder inside the game directory the files go to */
    public final String dir;
    public final String label;
    /** Modrinth loader filter, or null for none */
    public final String loader;

    Kind(String type, String dir, String label, String loader) {
        this.type = type;
        this.dir = dir;
        this.label = label;
        this.loader = loader;
    }
}
