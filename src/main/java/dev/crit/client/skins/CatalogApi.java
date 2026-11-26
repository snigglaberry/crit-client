package dev.crit.client.skins;

import dev.crit.client.CritClient;
import dev.crit.client.Net;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Reads snigglaberry/critical-skin-api through the GitHub contents API.
 * Listings are cached on disk for 6h because the unauthenticated GitHub API only allows 60 calls/hour.
 * PNGs come from raw.githubusercontent.com, which isn't rate limited that way.
 */
public final class CatalogApi {
    private static final String REPO = "snigglaberry/critical-skin-api";
    private static final String API = "https://api.github.com/repos/" + REPO + "/contents/skin";
    private static final String RAW = "https://raw.githubusercontent.com/" + REPO + "/main/skin/";
    private static final long TTL_MS = Duration.ofHours(6).toMillis();

    public record Item(String letter, String name, String url) {}

    private static Path cacheDir() { return CritClient.DIR.resolve("cache"); }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String cachedJson(String cacheName, String url) throws IOException {
        Path f = cacheDir().resolve(cacheName);
        boolean exists = Files.exists(f);
        if (exists && System.currentTimeMillis() - Files.getLastModifiedTime(f).toMillis() < TTL_MS) {
            return Files.readString(f);
        }
        try {
            String body = Net.getText(url);
            Files.createDirectories(cacheDir());
            Files.writeString(f, body);
            Files.setLastModifiedTime(f, FileTime.fromMillis(System.currentTimeMillis()));
            return body;
        } catch (IOException e) {
            if (exists) return Files.readString(f); // stale beats nothing
            if (e instanceof Net.HttpStatusException h && (h.code == 403 || h.code == 429)) {
                throw new IOException("GitHub rate limit reached. Try again later.");
            }
            throw e;
        }
    }

    public static List<String> letters() throws IOException {
        JsonArray arr = JsonParser.parseString(cachedJson("index.json", API)).getAsJsonArray();
        List<String> out = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            if ("dir".equals(o.get("type").getAsString())) out.add(o.get("name").getAsString());
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    public static List<Item> list(String letter) throws IOException {
        JsonArray arr = JsonParser.parseString(cachedJson("list_" + safe(letter) + ".json", API + "/" + enc(letter))).getAsJsonArray();
        List<Item> out = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject o = el.getAsJsonObject();
            String file = o.get("name").getAsString();
            if (!"file".equals(o.get("type").getAsString()) || !file.toLowerCase().endsWith(".png")) continue;
            String url = o.has("download_url") && !o.get("download_url").isJsonNull()
                    ? o.get("download_url").getAsString() : RAW + enc(letter) + "/" + enc(file);
            out.add(new Item(letter, file.substring(0, file.length() - 4), url));
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    /** PNG bytes for a catalog skin, cached on disk. */
    public static byte[] png(Item it) throws IOException {
        Path f = cacheDir().resolve("png").resolve(safe(it.letter()) + "__" + safe(it.name()) + ".png");
        if (Files.exists(f)) return Files.readAllBytes(f);
        byte[] b = Net.getBytes(it.url());
        Files.createDirectories(f.getParent());
        Files.write(f, b);
        return b;
    }

    /** Forget cached listings so the next load hits GitHub again. */
    public static void invalidate() {
        try (Stream<Path> s = Files.list(cacheDir())) {
            s.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    private static String safe(String s) { return s.replaceAll("[^A-Za-z0-9._-]", "_"); }

    private CatalogApi() {}
}
