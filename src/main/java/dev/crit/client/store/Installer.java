package dev.crit.client.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

/** Picks a compatible version, downloads it (plus required dependencies for mods) and remembers what we installed. */
public final class Installer {
    public static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("CritClient/store");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, String> installed; // projectId -> "<dir>/<filename>"

    public static String gameVersion() {
        return FabricLoader.getInstance().getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(null);
    }

    private static Path gameDir() {
        return FabricLoader.getInstance().getGameDir();
    }

    public static boolean isInstalled(Modrinth.Hit h, Kind kind) {
        String rel = registry().get(h.id());
        return rel != null && rel.startsWith(kind.dir + "/") && Files.exists(gameDir().resolve(rel));
    }

    /** Installs the project; returns a message for the person. Blocking, call off the render thread. */
    public static String install(Modrinth.Hit hit, Kind kind, boolean compatOnly) throws IOException {
        String gv = gameVersion();
        Modrinth.Version v = pick(hit.id(), kind, gv, compatOnly);
        Set<String> seen = new HashSet<>();
        seen.add(hit.id());
        StringBuilder extra = new StringBuilder();
        String file = installVersion(v, hit.id(), kind, seen, 0, gv, compatOnly, extra);
        if (file == null) return hit.title() + " is already installed.";
        return "Installed " + hit.title() + extra + tail(kind);
    }

    private static String tail(Kind kind) {
        return switch (kind) {
            case MOD -> ". Restart the game to load it.";
            case RESOURCEPACK -> ". Enable it in Resource Packs.";
            case SHADER -> ". Enable it in your shader mod (e.g. Iris) settings.";
        };
    }

    private static Modrinth.Version pick(String projectId, Kind kind, String gv, boolean compatOnly) throws IOException {
        List<Modrinth.Version> vs = Modrinth.versions(projectId, kind, gv);
        if (vs.isEmpty()) {
            if (compatOnly) throw new IOException("No version for Minecraft " + gv + " (switch Version to Any to override)");
            vs = Modrinth.versions(projectId, kind, null);
        }
        if (vs.isEmpty()) throw new IOException("No downloadable version found");
        for (Modrinth.Version v : vs) if ("release".equals(v.type())) return v;
        return vs.get(0);
    }

    /** @return the filename written, or null if it was already there */
    private static String installVersion(Modrinth.Version v, String projectId, Kind kind, Set<String> seen, int depth,
                                         String gv, boolean compatOnly, StringBuilder extra) throws IOException {
        Modrinth.FileInfo f = v.mainFile();
        if (f == null) throw new IOException("That version has no files");
        String name = Path.of(f.filename()).getFileName().toString();
        if (name.isEmpty() || name.startsWith(".")) throw new IOException("Bad filename from Modrinth");
        Path dir = gameDir().resolve(kind.dir);
        Files.createDirectories(dir);
        Path target = dir.resolve(name);
        boolean existed = Files.exists(target);
        if (!existed) Modrinth.download(f, target);
        remember(projectId, kind.dir + "/" + name);

        if (kind == Kind.MOD && depth < 3) {
            for (Modrinth.Dep d : v.deps()) {
                if (!"required".equals(d.type()) || d.projectId() == null || !seen.add(d.projectId())) continue;
                if (alreadyHave(d.projectId())) continue;
                Modrinth.Version dv = d.versionId() != null ? Modrinth.version(d.versionId()) : pick(d.projectId(), kind, gv, compatOnly);
                String got = installVersion(dv, d.projectId(), kind, seen, depth + 1, gv, compatOnly, extra);
                if (got != null) extra.append(" + ").append(got);
            }
        }
        return existed ? null : name;
    }

    private static boolean alreadyHave(String projectId) {
        String rel = registry().get(projectId);
        if (rel != null && Files.exists(gameDir().resolve(rel))) return true;
        try {
            String slug = Modrinth.slug(projectId);
            FabricLoader fl = FabricLoader.getInstance();
            return slug != null && (fl.isModLoaded(slug) || fl.isModLoaded(slug.replace('-', '_')));
        } catch (IOException e) {
            return false;
        }
    }

    /** Tell Minecraft to rescan resourcepacks/ so a new pack shows up. Reflection keeps this version-proof. */
    public static void reloadPacks() {
        try {
            Minecraft mc = Minecraft.getInstance();
            for (Method m : Minecraft.class.getMethods()) {
                if (m.getParameterCount() == 0 && m.getReturnType().getName().endsWith("PackRepository")) {
                    Object repo = m.invoke(mc);
                    repo.getClass().getMethod("reload").invoke(repo);
                    return;
                }
            }
        } catch (Exception e) {
            LOG.warn("Couldn't refresh the resource pack list", e);
        }
    }

    // ------------------------------------------------------------------ registry of what we installed

    private static Path regFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("chezaddons.json");
    }

    private static synchronized Map<String, String> registry() {
        if (installed == null) {
            installed = new HashMap<>();
            try {
                if (Files.exists(regFile())) {
                    Map<String, String> m = GSON.fromJson(Files.readString(regFile(), StandardCharsets.UTF_8),
                            new TypeToken<Map<String, String>>() {}.getType());
                    if (m != null) installed.putAll(m);
                }
            } catch (Exception e) {
                LOG.warn("Couldn't read chezaddons.json", e);
            }
        }
        return installed;
    }

    private static synchronized void remember(String projectId, String rel) {
        registry().put(projectId, rel);
        try {
            Files.createDirectories(regFile().getParent());
            Files.writeString(regFile(), GSON.toJson(installed), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warn("Couldn't save chezaddons.json", e);
        }
    }

    private Installer() {}
}
