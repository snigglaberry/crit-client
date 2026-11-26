package dev.crit.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Tiny HTTP + thread-pool helper shared by everything that talks to the network. */
public final class Net {
    public static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public static final ExecutorService POOL = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "CritClient-IO");
        t.setDaemon(true);
        return t;
    });

    public static final class HttpStatusException extends IOException {
        public final int code;
        public HttpStatusException(int code) {
            super("HTTP " + code);
            this.code = code;
        }
    }

    public static HttpRequest.Builder req(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "CritClient/" + CritClient.version() + " (Fabric mod; https://critlauncher.xyz)");
    }

    public static HttpResponse<byte[]> send(HttpRequest r) throws IOException {
        try {
            return HTTP.send(r, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted");
        }
    }

    public static byte[] getBytes(String url) throws IOException {
        HttpResponse<byte[]> r = send(req(url).GET().build());
        if (r.statusCode() / 100 != 2) throw new HttpStatusException(r.statusCode());
        return r.body();
    }

    public static String getText(String url) throws IOException {
        return new String(getBytes(url), StandardCharsets.UTF_8);
    }

    public static String msg(Throwable t) {
        String m = t.getMessage();
        return (m == null || m.isBlank()) ? t.getClass().getSimpleName() : m;
    }

    private Net() {}
}
