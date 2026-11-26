package dev.crit.client.pages;

import dev.crit.client.CritScreen;
import dev.crit.client.Net;
import dev.crit.client.skins.AuthState;
import dev.crit.client.skins.CatalogApi;
import dev.crit.client.skins.OfflineSkin;
import dev.crit.client.skins.SkinApi;
import dev.crit.client.skins.SkinFront;
import dev.crit.client.skins.SkinLibrary;
import dev.crit.client.skins.SkinOverride;
import dev.crit.client.skins.Thumbs;
import dev.crit.client.ui.Ui;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import net.minecraft.client.User;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;

/** Skins: My Skins / Catalog gallery of front renders, click to apply. "Create new" steals a skin or imports a PNG. */
public final class SkinsPage extends Page {
    private static final int TW = 62, TH = 96, GAP = 6, SCALE = 2;

    private enum Mode { GALLERY, CREATE }
    private enum TileKind { CREATE, LOCAL, CATALOG }
    private record Tile(TileKind kind, String key, String label, Object ref) {}

    private Mode mode = Mode.GALLERY;
    private boolean catalogTab;
    private String searchText = "";
    private double scroll, scrollTarget;
    private EditBox searchBox;

    private volatile String status = "";
    private volatile int statusColor = Ui.MUTED;
    private volatile boolean busy;

    // catalog state (filled from background threads)
    private volatile List<String> letters = List.of();
    private volatile boolean lettersLoading;
    private volatile String catalogError;
    private final Map<String, List<CatalogApi.Item>> loaded = new ConcurrentHashMap<>();
    private final Set<String> inflight = ConcurrentHashMap.newKeySet();

    // create form
    private EditBox pathBox;
    private String userText = "", pathText = "", nameText = "";
    private int model = 0; // 0 auto, 1 classic, 2 slim

    public SkinsPage(CritScreen s) {
        super(s);
    }

    private void flash(String msg, int color) {
        status = msg;
        statusColor = color;
    }

    @Override
    public void init() {
        searchBox = null;
        pathBox = null;
        if (mode == Mode.GALLERY) {
            searchBox = s.box(x + 144, y, Math.min(w - 144, 170), 16, "Search skins...", searchText, 40, v -> {
                searchText = v;
                scrollTarget = 0;
            });
            if (catalogTab && letters.isEmpty()) loadLetters();
        } else {
            int fy = y + 40;
            s.box(x, fy, Math.min(w - 84, 220), 18, "Minecraft username", userText, 16, v -> userText = v);
            int uy = fy + 66;
            pathBox = s.box(x, uy, Math.min(w, 320), 18, "Path to a .png (or drop one on the window)", pathText, 512, v -> pathText = v);
            s.box(x, uy + 24, 140, 18, "Skin name", nameText, 24, v -> nameText = v);
        }
    }

    @Override
    public void hidden() {
        mode = Mode.GALLERY;
    }

    // ------------------------------------------------------------------ catalog

    private void loadLetters() {
        if (lettersLoading) return;
        lettersLoading = true;
        Net.POOL.execute(() -> {
            try {
                List<String> l = CatalogApi.letters();
                letters = l;
                catalogError = null;
                requestAll();
            } catch (Exception e) {
                catalogError = "Couldn't load the catalog: " + Net.msg(e);
            } finally {
                lettersLoading = false;
            }
        });
    }

    private void requestAll() {
        for (String l : letters) requestLetter(l);
    }

    private void requestLetter(String l) {
        if (loaded.containsKey(l) || !inflight.add(l)) return;
        Net.POOL.execute(() -> {
            try {
                loaded.put(l, CatalogApi.list(l));
                catalogError = null;
            } catch (Exception e) {
                catalogError = "Couldn't load \"" + l + "\": " + Net.msg(e);
            } finally {
                inflight.remove(l);
            }
        });
    }

    private void refreshCatalog() {
        CatalogApi.invalidate();
        loaded.clear();
        catalogError = null;
        letters = List.of();
        lettersLoading = false;
        loadLetters();
    }

    private void setCatalog(boolean catalog) {
        if (catalogTab == catalog) return;
        catalogTab = catalog;
        scroll = scrollTarget = 0;
        if (catalog) {
            if (letters.isEmpty()) loadLetters(); else requestAll();
        }
    }

    // ------------------------------------------------------------------ tiles

    private List<Tile> tiles() {
        List<Tile> out = new ArrayList<>();
        out.add(new Tile(TileKind.CREATE, "create", "Create new", null));
        String q = searchText.trim().toLowerCase();
        if (!catalogTab) {
            for (SkinLibrary.Entry e : SkinLibrary.all()) {
                if (q.isEmpty() || e.name.toLowerCase().contains(q)) out.add(new Tile(TileKind.LOCAL, "L:" + e.id, e.name, e));
            }
        } else {
            for (String l : letters) {
                List<CatalogApi.Item> items = loaded.get(l);
                if (items == null) continue;
                for (CatalogApi.Item it : items) {
                    if (q.isEmpty() || it.name().toLowerCase().contains(q)) {
                        out.add(new Tile(TileKind.CATALOG, "C:" + it.letter() + "/" + it.name(), it.name(), it));
                    }
                }
            }
        }
        return out;
    }

    private int cols() { return Math.max(1, (w + GAP) / (TW + GAP)); }
    private int gridTop() { return y + 24; }
    private int gridBottom() { return y + h - 16; }

    private double maxScroll(int tileCount) {
        int rows = (tileCount + cols() - 1) / cols();
        return Math.max(0, rows * (TH + GAP) - (gridBottom() - gridTop()));
    }

    // ------------------------------------------------------------------ render

    private static boolean offlineSession() { return AuthState.status() == AuthState.Status.OFFLINE; }

    @Override
    public void render(GuiGraphicsExtractor g, int mx, int my) {
        if (searchBox != null) searchBox.visible = mode == Mode.GALLERY;
        if (mode == Mode.CREATE) {
            create(g, mx, my);
            return;
        }
        gallery(g, mx, my);
    }

    private void gallery(GuiGraphicsExtractor g, int mx, int my) {
        s.chip(g, x, y, 68, 16, "My Skins", !catalogTab, mx, my, () -> setCatalog(false));
        s.chip(g, x + 72, y, 68, 16, "Catalog", catalogTab, mx, my, () -> setCatalog(true));
        OfflineSkin.Preview localSkin = OfflineSkin.preview();
        if (localSkin != null) {
            s.button(g, x + w - 100, y + h - 14, 100, 14, "Reset local skin", mx, my, !busy, false, this::resetOffline);
        }

        List<Tile> tiles = tiles();
        String active = localSkin != null ? localSkin.clientKey()
                : AuthState.status() == AuthState.Status.ONLINE ? SkinLibrary.active() : "";
        scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll(tiles.size())));
        scroll += (scrollTarget - scroll) * 0.35;
        if (Math.abs(scrollTarget - scroll) < 0.1) scroll = scrollTarget;

        int top = gridTop(), bottom = gridBottom(), cols = cols();
        g.enableScissor(x, top, x + w, bottom);
        s.clip(top, bottom);
        for (int i = 0; i < tiles.size(); i++) {
            int tx = x + (i % cols) * (TW + GAP);
            int ty = top + (i / cols) * (TH + GAP) - (int) Math.round(scroll);
            if (ty + TH < top || ty > bottom) continue;
            drawTile(g, tiles.get(i), tx, ty, mx, my, top, bottom, active);
        }
        s.unclip();
        g.disableScissor();

        if (tiles.size() == 1) {
            String msg = null;
            if (catalogTab) {
                boolean loading = lettersLoading || !inflight.isEmpty();
                msg = catalogError != null ? catalogError : loading ? "Loading catalog..." : searchText.isBlank() ? null : "No skins match your search.";
            } else if (!searchText.isBlank()) {
                msg = "No saved skins match your search.";
            }
            if (msg != null) Ui.text(g, s.font(), Ui.trim(s.font(), msg, w - TW - 16), x + TW + GAP + 8, top + 40, catalogError != null && catalogTab ? Ui.ERR : Ui.MUTED);
        }
        Ui.scrollbar(g, x + w + 3, top, bottom - top, scroll, maxScroll(tiles.size()));

        String st = status;
        int sc = statusColor;
        if (st.isEmpty() && catalogTab && catalogError == null && (lettersLoading || !inflight.isEmpty())) st = "Loading...";
        if (st.isEmpty()) {
            st = switch (AuthState.status()) {
                case CHECKING -> "Verifying your account...";
                case OFFLINE -> localSkin != null && localSkin.clientKey().isEmpty()
                        ? "Using a local skin. Only you can see it."
                        : "Offline skin for " + AuthState.name() + ". Click a skin to apply it. Only you can see it.";
                case ELY -> "Ely.by skins come from your Ely.by profile.";
                case UNAVAILABLE -> AuthState.reason();
                case ONLINE -> "Signed in as " + AuthState.name() + ". Click a skin to apply it" + (catalogTab ? ", + saves it." : ".");
            };
        }
        int resetSpace = localSkin == null ? 0 : 108;
        Ui.text(g, s.font(), Ui.trim(s.font(), st, w - resetSpace - (catalogTab ? 100 : 0)), x, y + h - 10, sc);
        if (catalogTab) s.button(g, x + w - 92 - resetSpace, y + h - 14, 92, 14, "Refresh catalog", mx, my, true, false, this::refreshCatalog);
    }

    private void drawTile(GuiGraphicsExtractor g, Tile t, int tx, int ty, int mx, int my, int top, int bottom,
                          String active) {
        boolean hov = my >= top && my < bottom && Ui.in(mx, my, tx, ty, TW, TH);
        boolean isActive = !active.isEmpty() && (t.kind() == TileKind.LOCAL ? active.equals(((SkinLibrary.Entry) t.ref()).id)
                : t.kind() == TileKind.CATALOG && active.equals(t.key()));

        Ui.controlBox(g, tx, ty, TW, TH, hov ? Ui.TILE_HOVER : Ui.TILE, isActive ? Ui.ACCENT : hov ? Ui.EDGE_LIGHT : Ui.EDGE);

        int ox = tx + (TW - 16 * SCALE) / 2, oy = ty + 6;
        switch (t.kind()) {
            case CREATE -> {
                int cx = tx + TW / 2, cy = oy + 32;
                int c = hov ? Ui.ACCENT : Ui.DIM;
                g.fill(cx - 10, cy - 1, cx + 10, cy + 1, c);
                g.fill(cx - 1, cy - 10, cx + 1, cy + 10, c);
            }
            case LOCAL, CATALOG -> {
                Callable<byte[]> loader = t.kind() == TileKind.LOCAL
                        ? () -> SkinLibrary.bytes((SkinLibrary.Entry) t.ref())
                        : () -> CatalogApi.png((CatalogApi.Item) t.ref());
                Boolean slim = t.kind() == TileKind.LOCAL ? ((SkinLibrary.Entry) t.ref()).slim : null;
                Thumbs.Thumb th = Thumbs.get(t.key(), loader, slim);
                g.fill(ox + 4, oy + 32 * SCALE + 1, ox + 16 * SCALE - 4, oy + 32 * SCALE + 3, 0x55000000);
                List<int[]> runs = th.runs;
                if (runs != null) {
                    for (int[] r : runs) {
                        g.fill(ox + r[0] * SCALE, oy + r[1] * SCALE, ox + (r[0] + r[2]) * SCALE, oy + (r[1] + 1) * SCALE, r[3]);
                    }
                } else {
                    Ui.center(g, s.font(), th.failed ? "?" : "...", tx + TW / 2, oy + 28, Ui.MUTED);
                }
            }
        }
        Ui.center(g, s.font(), Ui.trim(s.font(), t.label(), TW - 8), tx + TW / 2, ty + TH - 13,
                t.kind() == TileKind.CREATE ? Ui.ACCENT : Ui.TEXT);

        s.region(tx, ty, TW, TH, () -> clickTile(t, false));
        if (hov && t.kind() == TileKind.LOCAL) {
            Ui.controlBox(g, tx + TW - 14, ty + 2, 12, 12, 0xFF5A1D1D, Ui.ERR);
            Ui.center(g, s.font(), "x", tx + TW - 8, ty + 4, Ui.TEXT);
            s.region(tx + TW - 14, ty + 2, 12, 12, () -> clickTile(t, true));
        } else if (hov && t.kind() == TileKind.CATALOG && !SkinLibrary.hasSource("catalog:" + t.key().substring(2))) {
            Ui.controlBox(g, tx + TW - 14, ty + 2, 12, 12, Ui.ACCENT_DARK, Ui.ACCENT);
            Ui.center(g, s.font(), "+", tx + TW - 8, ty + 4, Ui.TEXT);
            s.region(tx + TW - 14, ty + 2, 12, 12, () -> clickTile(t, true));
        }
    }

    private void clickTile(Tile t, boolean corner) {
        switch (t.kind()) {
            case CREATE -> {
                mode = Mode.CREATE;
                status = "";
                s.rebuild();
            }
            case LOCAL -> {
                SkinLibrary.Entry e = (SkinLibrary.Entry) t.ref();
                if (corner) {
                    SkinLibrary.remove(e);
                    flash("Removed " + e.name + ".", Ui.MUTED);
                } else {
                    applySkin(e.name, () -> SkinLibrary.bytes(e), e.id, e.slim);
                }
            }
            case CATALOG -> {
                CatalogApi.Item it = (CatalogApi.Item) t.ref();
                String source = "catalog:" + t.key().substring(2);
                if (corner) {
                    if (SkinLibrary.hasSource(source)) return;
                    Net.POOL.execute(() -> {
                        try {
                            byte[] b = CatalogApi.png(it);
                            boolean slim = SkinFront.isSlim(ImageIO.read(new ByteArrayInputStream(b)));
                            SkinLibrary.add(it.name(), b, slim, source);
                            flash("Saved " + it.name() + " to My Skins.", Ui.ACCENT);
                        } catch (Exception e) {
                            flash("Couldn't save: " + Net.msg(e), Ui.ERR);
                        }
                    });
                } else {
                    applySkin(it.name(), () -> CatalogApi.png(it), t.key(), null);
                }
            }
        }
    }

    private void applySkin(String label, Callable<byte[]> bytes, String activeKey, Boolean slim) {
        if (busy) return;
        User user = AuthState.user();
        if (offlineSession()) {
            applyOffline(user, label, bytes, activeKey, slim);
            return;
        }
        if (!AuthState.isOnline(user)) {
            flash(switch (AuthState.status()) {
                case ELY -> "Change your skin on Ely.by, then relaunch Minecraft.";
                case UNAVAILABLE -> AuthState.reason();
                default -> "Still verifying your account...";
            }, Ui.ERR);
            return;
        }
        busy = true;
        flash("Applying " + label + "...", Ui.MUTED);
        Net.POOL.execute(() -> {
            try {
                byte[] b = bytes.call();
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(b));
                if (!SkinFront.validSize(img)) throw new IllegalStateException("That file isn't a valid 64x64 / 64x32 skin.");
                SkinApi.Applied r = SkinApi.apply(user, b, img.getHeight() == 64 && (slim != null ? slim : SkinFront.isSlim(img)));
                if (AuthState.user() == user) {
                    SkinOverride.set(user.getProfileId(), r.url(), r.slim());
                    SkinLibrary.setActive(activeKey);
                }
                flash("Applied " + label + ".", Ui.ACCENT);
            } catch (Exception e) {
                flash(Net.msg(e), Ui.ERR);
            } finally {
                busy = false;
            }
        });
    }

    /** Offline account: Crit Client shows the skin itself (see OfflineSkin), no other mod and no restart. */
    private void applyOffline(User user, String label, Callable<byte[]> bytes, String activeKey, Boolean slim) {
        busy = true;
        flash("Applying " + label + "...", Ui.MUTED);
        Net.POOL.execute(() -> {
            try {
                OfflineSkin.apply(user, bytes.call(), slim, activeKey).whenComplete((v, error) -> {
                    if (error == null) flash("Applied " + label + ". Only you can see it.", Ui.ACCENT);
                    else flash(Net.msg(error instanceof CompletionException e && e.getCause() != null ? e.getCause() : error), Ui.ERR);
                    busy = false;
                });
            } catch (Exception e) {
                flash(Net.msg(e), Ui.ERR);
                busy = false;
            }
        });
    }

    private void resetOffline() {
        if (busy) return;
        try {
            OfflineSkin.clear();
            flash("Local skin reset.", Ui.ACCENT);
        } catch (IOException e) {
            flash(Net.msg(e), Ui.ERR);
        }
    }

    // ------------------------------------------------------------------ create new

    private String modelLabel() {
        return "Model: " + (model == 0 ? "Auto" : model == 1 ? "Classic" : "Slim");
    }

    private void create(GuiGraphicsExtractor g, int mx, int my) {
        s.button(g, x, y, 62, 16, "< Back", mx, my, true, false, this::closeCreate);
        Ui.text(g, s.font(), "Create new skin", x + 74, y + 4, Ui.TEXT);

        Ui.text(g, s.font(), "Copy a player's skin", x, y + 28, Ui.MUTED);
        int fy = y + 40;
        s.button(g, x + Math.min(w - 84, 220) + 6, fy, 72, 18, "Copy", mx, my, !busy, true, this::steal);

        int uy = fy + 66;
        Ui.text(g, s.font(), "Import a PNG", x, uy - 14, Ui.MUTED);
        s.button(g, x + 146, uy + 24, 96, 18, modelLabel(), mx, my, true, false, () -> model = (model + 1) % 3);
        s.button(g, x + 248, uy + 24, 72, 18, "Import", mx, my, !busy, true, this::importFile);

        String st = status;
        int yy = uy + 52;
        for (String line : Ui.wrap(s.font(), st, Math.min(w, 330))) {
            Ui.text(g, s.font(), line, x, yy, statusColor);
            yy += 11;
        }
    }

    private void closeCreate() {
        mode = Mode.GALLERY;
        s.rebuild();
    }

    private void steal() {
        String name = userText.trim();
        if (name.isEmpty()) { flash("Type a username first.", Ui.ERR); return; }
        busy = true;
        flash("Looking up " + name + "...", Ui.MUTED);
        Net.POOL.execute(() -> {
            try {
                SkinApi.Stolen st = SkinApi.steal(name);
                SkinLibrary.add(st.name(), st.png(), st.slim(), "stolen:" + st.name());
                s.mc().execute(() -> {
                    userText = "";
                    flash("Saved " + st.name() + "'s skin to My Skins.", Ui.ACCENT);
                    closeCreate();
                });
            } catch (Exception e) {
                flash(Net.msg(e), Ui.ERR);
            } finally {
                busy = false;
            }
        });
    }

    private void importFile() {
        String raw = pathText.trim().replace("\"", "");
        if (raw.isEmpty()) { flash("Pick a file first: paste a path or drop a .png here.", Ui.ERR); return; }
        try {
            Path p = Path.of(raw);
            byte[] bytes = Files.readAllBytes(p);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (!SkinFront.validSize(img)) {
                flash("Skins must be 64x64 (or old-style 64x32) PNGs.", Ui.ERR);
                return;
            }
            boolean slim = model == 0 ? SkinFront.isSlim(img) : model == 2;
            String name = nameText.trim();
            if (name.isEmpty()) name = p.getFileName().toString().replaceAll("(?i)\\.png$", "");
            SkinLibrary.add(name, bytes, slim, "upload");
            pathText = nameText = "";
            flash("Imported " + name + ".", Ui.ACCENT);
            closeCreate();
        } catch (IOException | RuntimeException e) {
            flash("Couldn't read that file: " + Net.msg(e), Ui.ERR);
        }
    }

    @Override
    public void filesDropped(List<Path> paths) {
        if (mode != Mode.CREATE) return;
        for (Path p : paths) {
            if (p.getFileName().toString().toLowerCase().endsWith(".png")) {
                pathText = p.toString();
                nameText = p.getFileName().toString().replaceAll("(?i)\\.png$", "");
                if (pathBox != null) pathBox.setValue(pathText);
                flash("File loaded. Press Import.", Ui.ACCENT);
                s.rebuild();
                return;
            }
        }
        flash("That wasn't a .png file.", Ui.ERR);
    }

    @Override
    public boolean scroll(double mx, double my, double dy) {
        if (mode != Mode.GALLERY) return false;
        scrollTarget -= dy * 44;
        scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll(tiles().size())));
        return true;
    }
}
