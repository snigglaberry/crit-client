package dev.crit.client.skins;

import dev.crit.client.CritClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** "My Skins": PNGs in config/skinstudio/skins plus an index in library.json. */
public final class SkinLibrary {
    public static final class Entry {
        public String id, name, source;
        public boolean slim;
    }

    private static final class Data {
        String active = "";
        List<Entry> skins = new ArrayList<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();

    private static Path index() { return CritClient.DIR.resolve("library.json"); }
    private static Path file(Entry e) { return CritClient.DIR.resolve("skins").resolve(e.id + ".png"); }

    public static synchronized void load() {
        try {
            if (Files.exists(index())) {
                Data d = GSON.fromJson(Files.readString(index()), Data.class);
                if (d != null && d.skins != null) data = d;
            }
        } catch (Exception e) {
            CritClient.LOG.warn("Could not read skin library", e);
        }
    }

    private static void save() {
        try {
            Files.writeString(index(), GSON.toJson(data));
        } catch (IOException e) {
            CritClient.LOG.warn("Could not save skin library", e);
        }
    }

    public static synchronized List<Entry> all() { return new ArrayList<>(data.skins); }
    public static synchronized String active() { return data.active; }

    public static synchronized void setActive(String key) {
        data.active = key == null ? "" : key;
        save();
    }

    public static synchronized boolean hasSource(String source) {
        for (Entry e : data.skins) if (source.equals(e.source)) return true;
        return false;
    }

    public static synchronized Entry add(String name, byte[] png, boolean slim, String source) throws IOException {
        Entry e = new Entry();
        e.id = UUID.randomUUID().toString().substring(0, 8);
        e.name = name.length() > 24 ? name.substring(0, 24) : name;
        e.slim = slim;
        e.source = source;
        Files.write(file(e), png);
        data.skins.add(0, e);
        save();
        return e;
    }

    public static synchronized void remove(Entry e) {
        data.skins.removeIf(x -> x.id.equals(e.id));
        try { Files.deleteIfExists(file(e)); } catch (IOException ignored) {}
        if (e.id.equals(data.active)) data.active = "";
        Thumbs.drop("L:" + e.id);
        save();
    }

    public static byte[] bytes(Entry e) throws IOException { return Files.readAllBytes(file(e)); }

    private SkinLibrary() {}
}
