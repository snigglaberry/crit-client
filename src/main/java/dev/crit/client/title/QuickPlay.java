package dev.crit.client.title;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.crit.client.CritClient;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import com.mojang.blaze3d.platform.NativeImage;
import dev.crit.client.Icons;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Remembers the last multiplayer server and the last singleplayer world you joined (in config/skinstudio/quickplay.json)
 * and joins them again from the title screen's Quick Play buttons.
 */
public final class QuickPlay {
    private static final class Data {
        String serverName = "", serverIp = "", worldId = "", worldName = "";
        long serverTime, worldTime;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();

    private static Path file() { return CritClient.DIR.resolve("quickplay.json"); }

    public static void load() {
        try {
            if (Files.exists(file())) {
                Data d = GSON.fromJson(Files.readString(file()), Data.class);
                if (d != null) data = d;
            }
        } catch (Exception e) {
            CritClient.LOG.warn("Could not read quickplay.json", e);
        }
    }

    private static void save() {
        try {
            Files.writeString(file(), GSON.toJson(data));
        } catch (Exception e) {
            CritClient.LOG.warn("Could not save quickplay.json", e);
        }
    }

    public static boolean hasServer() { return !data.serverIp.isEmpty(); }
    public static String serverName() { return data.serverName.isEmpty() ? data.serverIp : data.serverName; }
    public static String serverIp() { return data.serverIp; }

    public static boolean hasWorld() {
        if (data.worldId.isEmpty()) return false;
        return Files.isDirectory(FabricLoader.getInstance().getGameDir().resolve("saves").resolve(data.worldId));
    }
    public static String worldName() { return data.worldName.isEmpty() ? data.worldId : data.worldName; }

    public static long serverTime() { return data.serverTime; }
    public static long worldTime() { return data.worldTime; }

    /** "Today, 3:45 PM", "Yesterday, 3:45 PM", or the date for anything older. Empty if unknown. */
    public static String when(long millis) {
        if (millis <= 0) return "";
        ZonedDateTime t = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault());
        LocalDate today = LocalDate.now(), day = t.toLocalDate();
        String time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(t);
        if (day.equals(today)) return "Today, " + time;
        if (day.equals(today.minusDays(1))) return "Yesterday, " + time;
        String pattern = day.getYear() == today.getYear() ? "MMM d" : "MMM d, yyyy";
        return DateTimeFormatter.ofPattern(pattern).format(t);
    }

    // ------------------------------------------------------------------ icons

    /** A texture to draw and its size in pixels. */
    public record Icon(Identifier id, int w, int h) {}

    private static final Icon FALLBACK = new Icon(Identifier.withDefaultNamespace("textures/misc/unknown_server.png"), 64, 64);

    private static final class Slot {
        final String path;
        Identifier id;
        int w = 64, h = 64;
        long stamp = -2, checked;
        Slot(String path) { this.path = path; }
    }

    private static final Slot SERVER_ICON = new Slot("quickplay/server"), WORLD_ICON = new Slot("quickplay/world");

    private static Path serverIconFile() { return CritClient.DIR.resolve("quickplay_server.png"); }
    private static Path worldIconFile() { return FabricLoader.getInstance().getGameDir().resolve("saves").resolve(data.worldId).resolve("icon.png"); }

    /** The last server's icon (saved when you joined it), or the game's default icon. */
    public static Icon serverIcon(Minecraft mc) { return icon(mc, SERVER_ICON, serverIconFile()); }

    /** The last world's icon (its icon.png), or the game's default icon. */
    public static Icon worldIcon(Minecraft mc) {
        return data.worldId.isEmpty() ? FALLBACK : icon(mc, WORLD_ICON, worldIconFile());
    }

    private static Icon icon(Minecraft mc, Slot slot, Path file) {
        long now = System.currentTimeMillis();
        if (now - slot.checked > 1500) {
            slot.checked = now;
            try {
                long stamp = Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() + file.toString().hashCode() : -1;
                if (stamp != slot.stamp) {
                    slot.stamp = stamp;
                    if (slot.id != null) {
                        mc.getTextureManager().release(slot.id);
                        slot.id = null;
                    }
                    if (stamp >= 0) {
                        try (java.io.InputStream in = Files.newInputStream(file)) {
                            NativeImage img = NativeImage.read(in);
                            slot.w = img.getWidth();
                            slot.h = img.getHeight();
                            slot.id = Identifier.fromNamespaceAndPath("critclient", slot.path);
                            mc.getTextureManager().register(slot.id, Icons.texture(img));
                        }
                    }
                }
            } catch (Throwable t) {
                slot.id = null;
            }
        }
        return slot.id == null ? FALLBACK : new Icon(slot.id, slot.w, slot.h);
    }

    // ------------------------------------------------------------------ remembering

    /** Called whenever you join a world or server. */
    public static void onJoin(Minecraft mc) {
        try {
            Object integrated = Refl.call(mc, "getSingleplayerServer");
            if (integrated != null) {
                Path root = (Path) Refl.call(integrated, "getWorldPath", LevelResource.ROOT);
                Path folder = root.normalize().getFileName();
                String id = folder == null ? "" : folder.toString();
                String name = "";
                try {
                    name = Refl.text(Refl.call(integrated, "getWorldData"), "levelName");
                } catch (Exception ignored) {
                }
                if (!id.isEmpty()) {
                    data.worldId = id;
                    data.worldName = name.isEmpty() ? id : name;
                    data.worldTime = System.currentTimeMillis();
                    save();
                }
                return;
            }
            Object server = Refl.call(mc, "getCurrentServer");
            if (server != null) {
                String ip = Refl.text(server, "ip");
                if (!ip.isEmpty()) {
                    data.serverIp = ip;
                    data.serverName = Refl.text(server, "name");
                    data.serverTime = System.currentTimeMillis();
                    saveServerIcon(server);
                    save();
                }
            }
        } catch (Exception e) {
            CritClient.LOG.warn("Quick Play couldn't record where you joined", e);
        }
    }

    /** Keeps the server's icon (when the game has it) so the title screen can show it later. */
    private static void saveServerIcon(Object server) {
        try {
            Object bytes = Refl.call(server, "getIconBytes");
            if (bytes instanceof byte[] png && png.length > 0) {
                Files.write(serverIconFile(), png);
            } else {
                Files.deleteIfExists(serverIconFile());
            }
        } catch (Exception ignored) {
        }
    }

    // ------------------------------------------------------------------ joining

    public static void joinWorld(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Object flows = Refl.call(mc, "createWorldOpenFlows");
            Refl.call(flows, "openWorld", data.worldId, (Runnable) () -> {});
        } catch (Exception e) {
            CritClient.LOG.warn("Quick Play couldn't open the world directly, showing the world list", e);
            mc.setScreenAndShow(new SelectWorldScreen(parent));
        }
    }

    public static void joinServer(Screen parent) {
        joinAddress(parent, data.serverName.isEmpty() ? data.serverIp : data.serverName, data.serverIp);
    }

    /** Connects to a multiplayer server by address. */
    public static void joinAddress(Screen parent, String name, String ip) {
        Minecraft mc = Minecraft.getInstance();
        try {
            ServerAddress address = ServerAddress.parseString(ip);
            Object serverData = newServerData(name, ip);
            for (Method m : ConnectScreen.class.getDeclaredMethods()) {
                if (!m.getName().equals("startConnecting") || !Modifier.isStatic(m.getModifiers())) continue;
                Class<?>[] types = m.getParameterTypes();
                Object[] args = new Object[types.length];
                boolean ok = true;
                for (int i = 0; i < types.length; i++) {
                    Class<?> t = types[i];
                    if (t.isAssignableFrom(parent.getClass()) && Screen.class.isAssignableFrom(t)) args[i] = parent;
                    else if (t == Minecraft.class) args[i] = mc;
                    else if (t == ServerAddress.class) args[i] = address;
                    else if (t.getSimpleName().equals("ServerData")) args[i] = serverData;
                    else if (t == boolean.class) args[i] = false;
                    else if (t.isPrimitive()) ok = false;
                    else args[i] = null;
                }
                if (!ok) continue;
                m.setAccessible(true);
                m.invoke(null, args);
                return;
            }
            throw new NoSuchMethodException("ConnectScreen.startConnecting");
        } catch (Exception e) {
            CritClient.LOG.warn("Quick Play couldn't connect directly, showing the server list", e);
            mc.setScreenAndShow(new JoinMultiplayerScreen(parent));
        }
    }

    private static Object newServerData(String name, String ip) throws ReflectiveOperationException {
        Class<?> type = Class.forName("net.minecraft.client.multiplayer.ServerData");
        for (Constructor<?> c : type.getConstructors()) {
            Class<?>[] p = c.getParameterTypes();
            if (p.length < 2 || p[0] != String.class || p[1] != String.class) continue;
            Object[] args = new Object[p.length];
            args[0] = name;
            args[1] = ip;
            boolean ok = true;
            for (int i = 2; i < p.length; i++) {
                if (p[i].isEnum()) {
                    Object[] constants = p[i].getEnumConstants();
                    args[i] = constants[0];
                    for (Object k : constants) if (k.toString().equals("OTHER")) args[i] = k;
                } else if (p[i] == boolean.class) {
                    args[i] = false;
                } else {
                    ok = false;
                }
            }
            if (ok) return c.newInstance(args);
        }
        throw new NoSuchMethodException("ServerData constructor");
    }

    private QuickPlay() {}
}
