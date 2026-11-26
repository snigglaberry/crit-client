package dev.crit.client.store;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.crit.client.CritClient;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Minimal client for the Modrinth v2 API (https://docs.modrinth.com). */
public final class Modrinth {
    public static final String API = System.getProperty("critclient.modrinth", "https://api.modrinth.com/v2");
    private static final String UA = "critlauncher/crit-client/" + CritClient.version() + " (https://critlauncher.xyz)";
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    public static final ExecutorService EXEC = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "chezaddons-net");
        t.setDaemon(true);
        return t;
    });

    public record Hit(String id, String slug, String title, String author, String description, String iconUrl, long downloads) {}
    public record Page(List<Hit> hits, int total) {}
    /** Everything the store's detail popup needs for one project. {@code body} is the long markdown description. */
    public record Detail(String body, String sourceUrl, String issuesUrl, String wikiUrl, String discordUrl,
                         List<String> categories, List<String> loaders, List<String> gameVersions, long followers) {}
    public record FileInfo(String url, String filename, String sha1, boolean primary) {}
    public record Dep(String projectId, String versionId, String type) {}
    public record Version(String id, String projectId, String name, String type, List<FileInfo> files, List<Dep> deps) {
        public FileInfo mainFile() {
            for (FileInfo f : files) if (f.primary()) return f;
            return files.isEmpty() ? null : files.get(0);
        }
    }

    // ------------------------------------------------------------------ queries

    /** sort: relevance | downloads | follows | newest | updated. gameVersion null = any version. */
    public static Page search(Kind kind, String query, String sort, int offset, int limit, String gameVersion) throws IOException {
        JsonArray facets = new JsonArray();
        facets.add(group("project_type:" + kind.type));
        if (kind.loader != null) facets.add(group("categories:" + kind.loader));
        if (gameVersion != null) facets.add(group("versions:" + gameVersion));
        String url = API + "/search?query=" + enc(query) + "&facets=" + enc(facets.toString())
                + "&index=" + enc(sort) + "&offset=" + offset + "&limit=" + limit;
        JsonObject o = JsonParser.parseString(get(url)).getAsJsonObject();
        List<Hit> hits = new ArrayList<>();
        for (JsonElement e : o.getAsJsonArray("hits")) {
            JsonObject h = e.getAsJsonObject();
            hits.add(new Hit(str(h, "project_id"), str(h, "slug"), str(h, "title"), str(h, "author"),
                    str(h, "description"), str(h, "icon_url"), h.has("downloads") ? h.get("downloads").getAsLong() : 0));
        }
        return new Page(hits, o.has("total_hits") ? o.get("total_hits").getAsInt() : hits.size());
    }

    /** Versions of a project, newest first. gameVersion null = any version. */
    public static List<Version> versions(String projectId, Kind kind, String gameVersion) throws IOException {
        StringBuilder url = new StringBuilder(API + "/project/" + enc(projectId) + "/version?");
        if (kind.loader != null) url.append("loaders=").append(enc("[\"" + kind.loader + "\"]")).append('&');
        if (gameVersion != null) url.append("game_versions=").append(enc("[\"" + gameVersion + "\"]")).append('&');
        List<Version> out = new ArrayList<>();
        for (JsonElement e : JsonParser.parseString(get(url.toString())).getAsJsonArray()) out.add(parseVersion(e.getAsJsonObject()));
        return out;
    }

    public static Version version(String versionId) throws IOException {
        return parseVersion(JsonParser.parseString(get(API + "/version/" + enc(versionId))).getAsJsonObject());
    }

    /** Returns the project's slug. */
    public static String slug(String projectId) throws IOException {
        return str(JsonParser.parseString(get(API + "/project/" + enc(projectId))).getAsJsonObject(), "slug");
    }

    /** The full project record, including the long markdown {@code body}. */
    public static Detail detail(String projectId) throws IOException {
        JsonObject o = JsonParser.parseString(get(API + "/project/" + enc(projectId))).getAsJsonObject();
        return new Detail(str(o, "body"), str(o, "source_url"), str(o, "issues_url"), str(o, "wiki_url"),
                str(o, "discord_url"), strList(o, "categories"), strList(o, "loaders"), strList(o, "game_versions"),
                o.has("followers") ? o.get("followers").getAsLong() : 0);
    }

    private static Version parseVersion(JsonObject v) {
        List<FileInfo> files = new ArrayList<>();
        for (JsonElement e : v.getAsJsonArray("files")) {
            JsonObject f = e.getAsJsonObject();
            String sha1 = f.has("hashes") && f.getAsJsonObject("hashes").has("sha1")
                    ? f.getAsJsonObject("hashes").get("sha1").getAsString() : null;
            files.add(new FileInfo(str(f, "url"), str(f, "filename"), sha1, f.has("primary") && f.get("primary").getAsBoolean()));
        }
        List<Dep> deps = new ArrayList<>();
        if (v.has("dependencies")) {
            for (JsonElement e : v.getAsJsonArray("dependencies")) {
                JsonObject d = e.getAsJsonObject();
                deps.add(new Dep(str(d, "project_id"), str(d, "version_id"), str(d, "dependency_type")));
            }
        }
        return new Version(str(v, "id"), str(v, "project_id"), str(v, "name"), str(v, "version_type"), files, deps);
    }

    // ------------------------------------------------------------------ transport

    public static String get(String url) throws IOException {
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                    .header("User-Agent", UA).header("Accept", "application/json").GET().build();
            HttpResponse<String> res = HTTP.send(r, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            check(res.statusCode());
            return res.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        }
    }

    public static byte[] getBytes(String url) throws IOException {
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                    .header("User-Agent", UA).GET().build();
            HttpResponse<byte[]> res = HTTP.send(r, HttpResponse.BodyHandlers.ofByteArray());
            check(res.statusCode());
            return res.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        }
    }

    /** Downloads to target (via a .part file) and verifies the SHA-1 Modrinth lists for it. */
    public static void download(FileInfo f, Path target) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".part");
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(f.url())).timeout(Duration.ofMinutes(10))
                    .header("User-Agent", UA).GET().build();
            HttpResponse<InputStream> res = HTTP.send(r, HttpResponse.BodyHandlers.ofInputStream());
            check(res.statusCode());
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (InputStream in = new DigestInputStream(res.body(), md)) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            if (f.sha1() != null && !hex(md.digest()).equalsIgnoreCase(f.sha1())) {
                throw new IOException("Download was corrupted (checksum mismatch)");
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static void check(int status) throws IOException {
        if (status == 429) throw new IOException("Modrinth is rate limiting us, wait a moment");
        if (status / 100 != 2) throw new IOException("Modrinth answered HTTP " + status);
    }

    // ------------------------------------------------------------------ helpers

    private static JsonArray group(String facet) {
        JsonArray a = new JsonArray();
        a.add(facet);
        return a;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static List<String> strList(JsonObject o, String k) {
        List<String> out = new ArrayList<>();
        JsonElement e = o.get(k);
        if (e != null && e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) if (x.isJsonPrimitive()) out.add(x.getAsString());
        }
        return out;
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    private Modrinth() {}
}
