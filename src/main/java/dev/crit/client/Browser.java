package dev.crit.client;

/** Opens a link in the player's default browser using the OS's own opener (no game API involved). */
public final class Browser {
    public static void open(String url) {
        if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) return;
        Thread t = new Thread(() -> {
            try {
                String os = System.getProperty("os.name", "").toLowerCase();
                ProcessBuilder pb;
                if (os.contains("win")) pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
                else if (os.contains("mac")) pb = new ProcessBuilder("open", url);
                else pb = new ProcessBuilder("xdg-open", url);
                pb.start();
            } catch (Exception e) {
                CritClient.LOG.warn("Couldn't open {}", url, e);
            }
        }, "CritClient-browser");
        t.setDaemon(true);
        t.start();
    }

    private Browser() {}
}
