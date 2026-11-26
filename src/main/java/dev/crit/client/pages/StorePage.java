package dev.crit.client.pages;

import dev.crit.client.Browser;
import dev.crit.client.CritScreen;
import dev.crit.client.Icons;
import dev.crit.client.store.Installer;
import dev.crit.client.store.Kind;
import dev.crit.client.store.Modrinth;
import dev.crit.client.ui.Ui;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Modrinth browser: resource packs, mods and shaders as a gallery of cards. Click a card for its full page. */
public final class StorePage extends Page {
    private enum View { GALLERY, DETAIL }

    private static final int CW = 124, CH = 78, GAP = 6;
    private static final String[] SORTS = {"relevance", "downloads", "follows", "newest", "updated"};
    private static final String[] SORT_NAMES = {"Relevance", "Downloads", "Follows", "Newest", "Updated"};

    private enum State { IDLE, WORKING, DONE, FAILED }

    private Kind kind = Kind.MOD;
    private String query = "";
    private int sortIdx = 1;
    private boolean compat = true;

    private int pageIdx, total, lastLimit = -1, gen;
    private volatile boolean loading;
    private long searchAt = -1;
    private List<Modrinth.Hit> hits = List.of();
    private final Map<String, State> states = new HashMap<>();

    private String status = "";
    private int statusColor = Ui.MUTED;
    private boolean started;

    // detail popup
    private View view = View.GALLERY;
    private Modrinth.Hit sel;
    private Modrinth.Detail detail;
    private boolean detailLoading;
    private String detailError;
    private int detailGen;
    private double dScroll, dTarget;

    public StorePage(CritScreen s) {
        super(s);
    }

    public void setKind(Kind k) {
        kind = k;
        pageIdx = 0;
        query = "";
        hits = List.of();
        lastLimit = -1;
    }

    // ------------------------------------------------------------------ layout

    private int cols() { return Math.max(1, (w + GAP) / (CW + GAP)); }
    private int gridTop() { return y + 44; }
    private int gridBottom() { return y + h - 20; }
    private int rows() { return Math.max(1, (gridBottom() - gridTop() + GAP) / (CH + GAP)); }
    private int limit() { return Math.min(50, cols() * rows()); }

    @Override
    public void init() {
        if (view != View.GALLERY) return;
        int sw = Math.max(60, w - 2 * 98 - 8);
        EditBox box = s.box(x, y + 22, sw, 16, "Search " + kind.label.toLowerCase() + "...", query, 100, v -> {
            if (!v.equals(query)) {
                query = v;
                searchAt = System.currentTimeMillis() + 400;
            }
        });
        box.setMaxLength(100);
        if (limit() != lastLimit || !started) {
            started = true;
            pageIdx = 0;
            startSearch();
        }
    }

    @Override
    public void hidden() {
        gen++; // drop any search still in flight
        detailGen++; // and any detail fetch
        view = View.GALLERY;
        Icons.clearStore();
    }

    @Override
    public void shown() {
        view = View.GALLERY;
        lastLimit = -1; // reload when the tab is opened again
    }

    private String versionLabel() {
        String v = Installer.gameVersion();
        return "Version: " + (compat ? (v == null ? "current" : v) : "Any");
    }

    private void switchKind(Kind k) {
        if (k == kind) return;
        kind = k;
        pageIdx = 0;
        query = "";
        hits = List.of();
        lastLimit = -1; // makes init() run a fresh search
        say("", Ui.MUTED);
        s.rebuild();
    }

    // ------------------------------------------------------------------ searching

    private void startSearch() {
        searchAt = -1;
        final int g = ++gen;
        final int lim = limit();
        lastLimit = lim;
        loading = true;
        final Kind k = kind;
        final String q = query, sort = SORTS[sortIdx];
        final int off = pageIdx * lim;
        final String gv = compat ? Installer.gameVersion() : null;
        Modrinth.EXEC.submit(() -> {
            try {
                Modrinth.Page p = Modrinth.search(k, q, sort, off, lim, gv);
                s.mc().execute(() -> {
                    if (g != gen) return;
                    hits = p.hits();
                    total = p.total();
                    loading = false;
                    for (Modrinth.Hit h : hits) {
                        if (Installer.isInstalled(h, k)) states.putIfAbsent(h.id(), State.DONE);
                    }
                    say("", Ui.MUTED);
                });
            } catch (Exception e) {
                s.mc().execute(() -> {
                    if (g != gen) return;
                    loading = false;
                    hits = List.of();
                    total = 0;
                    say("Couldn't reach Modrinth: " + msg(e), Ui.ERR);
                });
            }
        });
    }

    private int pages() {
        return Math.max(1, (total + Math.max(1, lastLimit) - 1) / Math.max(1, lastLimit));
    }

    private State stateOf(Modrinth.Hit h) {
        return states.getOrDefault(h.id(), State.IDLE);
    }

    private void install(Modrinth.Hit h) {
        if (stateOf(h) == State.WORKING || stateOf(h) == State.DONE) return;
        states.put(h.id(), State.WORKING);
        final Kind k = kind;
        final boolean c = compat;
        Modrinth.EXEC.submit(() -> {
            try {
                String m = Installer.install(h, k, c);
                s.mc().execute(() -> {
                    states.put(h.id(), State.DONE);
                    if (k == Kind.RESOURCEPACK) Installer.reloadPacks();
                    say(m, Ui.ACCENT);
                });
            } catch (Exception e) {
                s.mc().execute(() -> {
                    states.put(h.id(), State.FAILED);
                    say("Couldn't install " + h.title() + ": " + msg(e), Ui.ERR);
                });
            }
        });
    }

    // ------------------------------------------------------------------ detail

    private void openDetail(Modrinth.Hit h) {
        sel = h;
        view = View.DETAIL;
        detail = null;
        detailError = null;
        detailLoading = true;
        dScroll = dTarget = 0;
        final int g = ++detailGen;
        Modrinth.EXEC.submit(() -> {
            try {
                Modrinth.Detail d = Modrinth.detail(h.id());
                s.mc().execute(() -> {
                    if (g != detailGen) return;
                    detail = d;
                    detailLoading = false;
                });
            } catch (Exception e) {
                s.mc().execute(() -> {
                    if (g != detailGen) return;
                    detailError = "Couldn't load the description: " + msg(e);
                    detailLoading = false;
                });
            }
        });
        s.rebuild();
    }

    private void backToGallery() {
        view = View.GALLERY;
        detailGen++;
        s.rebuild();
    }

    private String bodyText() {
        if (detailError != null) return detailError;
        if (detail == null) return detailLoading ? "Loading..." : "";
        String body = detail.body();
        if (body == null || body.isBlank()) return "This project has no description.";
        return stripMarkdown(body);
    }

    private List<String> bodyLines() {
        return Ui.wrap(s.font(), bodyText(), w - 8);
    }

    private int bodyTop() { return y + 118; }

    private double bodyMax() {
        if (sel == null) return 0;
        return Math.max(0, bodyLines().size() * 11 - (y + h - bodyTop()));
    }

    private void detail(GuiGraphicsExtractor g, int mx, int my) {
        Modrinth.Hit hit = sel;
        s.button(g, x, y, 62, 16, "< Store", mx, my, true, false, this::backToGallery);

        Identifier icon = Icons.store(hit.iconUrl());
        if (icon != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y + 26, 0, 0, 48, 48, Icons.SIZE, Icons.SIZE, Icons.SIZE, Icons.SIZE);
        } else {
            Ui.controlBox(g, x, y + 26, 48, 48, Ui.FIELD, Ui.EDGE_LIGHT);
        }

        int tx = x + 60, tw = w - 66;
        g.text(s.font(), Ui.heading(Ui.trim(s.font(), hit.title() == null ? "?" : hit.title(), tw)),
                tx, y + 28, Ui.TEXT, false);
        Ui.text(g, s.font(), Ui.trim(s.font(), "by " + (hit.author() == null ? "unknown" : hit.author()), tw), tx, y + 41, Ui.MUTED);
        Ui.text(g, s.font(), Ui.trim(s.font(), compact(hit.downloads()) + " downloads"
                + (detail != null ? "   " + compact(detail.followers()) + " followers" : ""), tw), tx, y + 53, Ui.DIM);

        String chips = null;
        if (detail != null) {
            List<String> bits = new ArrayList<>();
            if (!detail.loaders().isEmpty()) bits.add(String.join(", ", detail.loaders()));
            if (!detail.categories().isEmpty()) bits.addAll(detail.categories());
            if (!bits.isEmpty()) chips = String.join("  ·  ", bits);
        }
        if (chips != null) Ui.text(g, s.font(), Ui.trim(s.font(), chips, tw), tx, y + 65, Ui.DIM);

        // links
        int lx = x;
        String source = detail == null ? null : detail.sourceUrl();
        String wiki = detail == null ? null : detail.wikiUrl();
        String discord = detail == null ? null : detail.discordUrl();
        if (source != null && !source.isBlank()) { s.button(g, lx, y + 78, 62, 16, "Source", mx, my, true, false, () -> Browser.open(source)); lx += 68; }
        if (wiki != null && !wiki.isBlank()) { s.button(g, lx, y + 78, 62, 16, "Wiki", mx, my, true, false, () -> Browser.open(wiki)); lx += 68; }
        if (discord != null && !discord.isBlank()) { s.button(g, lx, y + 78, 62, 16, "Discord", mx, my, true, false, () -> Browser.open(discord)); lx += 68; }

        // install
        State st = stateOf(hit);
        String label = switch (st) {
            case IDLE -> "Install";
            case WORKING -> "Installing...";
            case DONE -> "Installed";
            case FAILED -> "Failed - retry";
        };
        s.button(g, x + w - 96, y + 78, 96, 16, label, mx, my,
                st != State.WORKING && st != State.DONE, st == State.IDLE || st == State.FAILED, () -> install(hit));

        Ui.text(g, s.font(), "About", x, y + 102, Ui.MUTED);
        int top = bodyTop(), bottom = y + h;
        dTarget = Math.max(0, Math.min(dTarget, bodyMax()));
        dScroll += (dTarget - dScroll) * 0.35;
        if (Math.abs(dTarget - dScroll) < 0.1) dScroll = dTarget;
        g.enableScissor(x, top, x + w, bottom);
        int ly = top - (int) Math.round(dScroll);
        for (String line : bodyLines()) {
            if (ly + 9 > top && ly < bottom) Ui.text(g, s.font(), line, x + 2, ly, detailError != null ? Ui.ERR : Ui.TEXT);
            ly += 11;
        }
        g.disableScissor();
        Ui.scrollbar(g, x + w + 3, top, bottom - top, dScroll, Math.max(0, bodyMax() - 12));
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphicsExtractor g, int mx, int my) {
        if (view == View.DETAIL && sel != null) {
            detail(g, mx, my);
            return;
        }

        if (searchAt > 0 && System.currentTimeMillis() >= searchAt) {
            pageIdx = 0;
            startSearch();
        }

        int cx = x;
        for (Kind k : Kind.values()) {
            s.chip(g, cx, y, 92, 16, k.label, k == kind, mx, my, () -> switchKind(k));
            cx += 96;
        }

        int sw = Math.max(60, w - 2 * 98 - 8);
        s.button(g, x + sw + 6, y + 22, 92, 16, "Sort: " + SORT_NAMES[sortIdx], mx, my, true, false, () -> {
            sortIdx = (sortIdx + 1) % SORTS.length;
            pageIdx = 0;
            startSearch();
        });
        s.button(g, x + sw + 104, y + 22, 92, 16, versionLabel(), mx, my, true, false, () -> {
            compat = !compat;
            pageIdx = 0;
            startSearch();
        });

        int cols = cols(), top = gridTop();
        int gridW = cols * CW + (cols - 1) * GAP;
        int gx = x + (w - gridW) / 2;
        for (int i = 0; i < hits.size(); i++) {
            drawCard(g, hits.get(i), gx + (i % cols) * (CW + GAP), top + (i / cols) * (CH + GAP), mx, my);
        }

        String line;
        int color = Ui.MUTED;
        if (!status.isEmpty()) {
            line = status;
            color = statusColor;
        } else if (loading) {
            line = "Loading...";
        } else if (hits.isEmpty()) {
            line = "Nothing found.";
        } else {
            line = "Page " + (pageIdx + 1) + " / " + pages() + "   (" + total + " results)";
        }
        Ui.text(g, s.font(), Ui.trim(s.font(), line, w - 90), x, y + h - 10, color);

        boolean canPrev = pageIdx > 0 && !loading, canNext = pageIdx < pages() - 1 && !loading;
        s.button(g, x + w - 62, y + h - 14, 28, 14, "<", mx, my, canPrev, false, () -> {
            pageIdx--;
            startSearch();
        });
        s.button(g, x + w - 30, y + h - 14, 28, 14, ">", mx, my, canNext, false, () -> {
            pageIdx++;
            startSearch();
        });
    }

    private void drawCard(GuiGraphicsExtractor g, Modrinth.Hit h, int cx, int cy, int mx, int my) {
        boolean hov = Ui.in(mx, my, cx, cy, CW, CH);
        Ui.controlBox(g, cx, cy, CW, CH, hov ? Ui.TILE_HOVER : Ui.TILE, hov ? Ui.EDGE_LIGHT : Ui.EDGE);

        Identifier icon = Icons.store(h.iconUrl());
        if (icon != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, icon, cx + 6, cy + 6, 0, 0, 32, 32, Icons.SIZE, Icons.SIZE, Icons.SIZE, Icons.SIZE);
        } else {
            Ui.controlBox(g, cx + 6, cy + 6, 32, 32, Ui.FIELD, Ui.EDGE_LIGHT);
        }
        int tx = cx + 44, tw = CW - 50;
        Ui.text(g, s.font(), Ui.trim(s.font(), h.title() == null ? "?" : h.title(), tw), tx, cy + 8, Ui.TEXT);
        Ui.text(g, s.font(), Ui.trim(s.font(), "by " + (h.author() == null ? "unknown" : h.author()), tw), tx, cy + 19, Ui.MUTED);
        Ui.text(g, s.font(), Ui.trim(s.font(), compact(h.downloads()) + " downloads", tw), tx, cy + 30, Ui.DIM);
        if (h.description() != null) Ui.text(g, s.font(), Ui.trim(s.font(), h.description(), CW - 12), cx + 6, cy + 41, Ui.DIM);

        State st = stateOf(h);
        String label = switch (st) {
            case IDLE -> "Install";
            case WORKING -> "Installing...";
            case DONE -> "Installed";
            case FAILED -> "Failed - retry";
        };
        // The card opens the detail popup; registered first so the Install button sits on top of it.
        s.region(cx, cy, CW, CH, () -> openDetail(h));
        s.button(g, cx + 6, cy + CH - 20, CW - 12, 14, label, mx, my, st != State.WORKING && st != State.DONE, st == State.IDLE || st == State.FAILED, () -> install(h));
    }

    private static String compact(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000) return String.format("%.1fk", n / 1_000.0);
        return Long.toString(n);
    }

    /** Modrinth descriptions are markdown; render them as plain wrapped text. */
    private static String stripMarkdown(String md) {
        String s = md;
        s = s.replaceAll("(?s)!\\[[^\\]]*\\]\\([^)]*\\)", "");        // images
        s = s.replaceAll("\\[([^\\]]*)\\]\\(([^)]*)\\)", "$1");       // [text](url) -> text
        s = s.replaceAll("(?s)```.*?```", "");                        // code fences
        s = s.replaceAll("<[^>]+>", "");                              // html tags
        s = s.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "");              // headings
        s = s.replaceAll("(?m)^\\s*>\\s?", "");                       // quotes
        s = s.replaceAll("(?m)^\\s*[-*+]\\s+", "- ");                 // bullets
        s = s.replaceAll("`", "");                                    // inline code
        s = s.replaceAll("(?<![A-Za-z0-9])(\\*{1,3}|_{1,3})", "");    // emphasis
        s = s.replaceAll("\\|", " ");                                 // table pipes
        s = s.replaceAll("(?m)[ \\t]+$", "");
        s = s.replaceAll("\\n{3,}", "\n\n");
        return s.trim();
    }

    private void say(String t, int color) {
        status = t;
        statusColor = color;
    }

    private static String msg(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }

    @Override
    public boolean scroll(double mx, double my, double dy) {
        if (view == View.DETAIL) {
            dTarget -= dy * 24;
            return true;
        }
        return false;
    }
}
