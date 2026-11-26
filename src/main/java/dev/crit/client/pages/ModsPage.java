package dev.crit.client.pages;

import dev.crit.client.Browser;
import dev.crit.client.CritClient;
import dev.crit.client.CritScreen;
import dev.crit.client.Icons;
import dev.crit.client.keys.OverlayEditorScreen;
import dev.crit.client.ui.Pixel;
import dev.crit.client.ui.Ui;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.api.metadata.Person;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * First tab: a gallery of every mod you have installed (Crit Client's own features are not listed).
 * Click one for its page: description, website, and an Edit button that opens its settings.
 */
public final class ModsPage extends Page {
    private enum View { GALLERY, DETAIL }
    private enum Module { KEYS, SKINS, ACCOUNTS, STORE }

    private record Entry(String id, String name, String version, String description, String authors,
                          String website, String license, Module module, ModContainer container) {
        boolean crit() { return module != null; }
    }

    private static final int CW = 140, CH = 50, GAP = 6;

    private View view = View.GALLERY;
    private final List<Entry> all = new ArrayList<>();
    private boolean loaded;
    private String query = "";
    private Entry sel;
    private boolean canEdit;
    private double gScroll, gTarget, dScroll, dTarget;

    public ModsPage(CritScreen s) {
        super(s);
    }

    // ------------------------------------------------------------------ data

    private void load() {
        if (loaded) return;
        loaded = true;
        List<Entry> others = new ArrayList<>();
        for (ModContainer c : FabricLoader.getInstance().getAllMods()) {
            ModMetadata md = c.getMetadata();
            String id = md.getId();
            if (id.equals("minecraft") || id.equals("java") || id.equals("fabricloader") || id.equals("critclient")) continue;
            if ("builtin".equals(md.getType())) continue;
            if (c.getContainingMod().isPresent()) continue; // bundled inside another mod (Fabric API's parts, libraries)
            if (isLibrary(md)) continue;

            StringBuilder authors = new StringBuilder();
            for (Person p : md.getAuthors()) {
                if (authors.length() > 0) authors.append(", ");
                authors.append(p.getName());
            }
            String web = md.getContact().get("homepage").orElse(md.getContact().get("sources").orElse(null));
            String license = String.join(", ", md.getLicense());
            others.add(new Entry(id, md.getName(), md.getVersion().getFriendlyString(), md.getDescription(),
                    authors.toString(), web, license, null, c));
        }
        others.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        all.addAll(others);
    }

    private static boolean isLibrary(ModMetadata md) {
        try {
            if (!md.containsCustomValue("modmenu")) return false;
            CustomValue cv = md.getCustomValue("modmenu");
            if (cv.getType() != CustomValue.CvType.OBJECT) return false;
            CustomValue.CvObject o = cv.getAsObject();
            if (o.containsKey("parent")) return true;
            CustomValue b = o.get("badges");
            if (b != null && b.getType() == CustomValue.CvType.ARRAY) {
                for (CustomValue e : b.getAsArray()) {
                    if (e.getType() == CustomValue.CvType.STRING && e.getAsString().equals("library")) return true;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    private List<Entry> filtered() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return all;
        List<Entry> out = new ArrayList<>();
        for (Entry e : all) {
            if (e.name().toLowerCase(Locale.ROOT).contains(q) || e.id().toLowerCase(Locale.ROOT).contains(q)) out.add(e);
        }
        return out;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void init() {
        load();
        if (view == View.GALLERY) {
            s.box(x, y, Math.min(w - 70, 240), 16, "Search mods...", query, 40, v -> {
                query = v;
                gTarget = 0;
            });
        }
    }

    @Override
    public void shown() {
        view = View.GALLERY;
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mx, int my) {
        switch (view) {
            case GALLERY -> gallery(g, mx, my);
            case DETAIL -> detail(g, mx, my);
        }
    }

    @Override
    public boolean scroll(double mx, double my, double dy) {
        if (view == View.GALLERY) {
            gTarget = clamp(gTarget - dy * 40, galleryMax());
            return true;
        }
        if (view == View.DETAIL) {
            dTarget = clamp(dTarget - dy * 22, descMax());
            return true;
        }
        return false;
    }

    private static double clamp(double v, double max) {
        return Math.max(0, Math.min(v, max));
    }

    // ------------------------------------------------------------------ gallery

    private int cols() { return Math.max(1, (w + GAP) / (CW + GAP)); }

    private double galleryMax() {
        int rows = (filtered().size() + cols() - 1) / cols();
        return Math.max(0, rows * (CH + GAP) - (h - 24));
    }

    private void gallery(GuiGraphicsExtractor g, int mx, int my) {
        List<Entry> list = filtered();
        gTarget = clamp(gTarget, galleryMax());
        gScroll += (gTarget - gScroll) * 0.35;
        if (Math.abs(gTarget - gScroll) < 0.1) gScroll = gTarget;

        String count = list.size() + (list.size() == 1 ? " mod" : " mods") + "  -  right-click for settings";
        Ui.right(g, s.font(), count, x + w, y + 4, Ui.MUTED);

        int top = y + 24, bottom = y + h;
        int cols = cols();
        g.enableScissor(x, top, x + w, bottom);
        s.clip(top, bottom);
        for (int i = 0; i < list.size(); i++) {
            int cx = x + (i % cols) * (CW + GAP);
            int cy = top + (i / cols) * (CH + GAP) - (int) Math.round(gScroll);
            if (cy + CH < top || cy > bottom) continue;
            card(g, list.get(i), cx, cy, mx, my, top, bottom);
        }
        s.unclip();
        g.disableScissor();
        if (list.isEmpty()) Ui.center(g, s.font(), "No mods match your search.", x + w / 2, top + 30, Ui.MUTED);
        Ui.scrollbar(g, x + w + 3, top, bottom - top, gScroll, galleryMax());
    }

    private void card(GuiGraphicsExtractor g, Entry e, int cx, int cy, int mx, int my, int top, int bottom) {
        boolean hov = my >= top && my < bottom && Ui.in(mx, my, cx, cy, CW, CH);
        int edge = hov ? Ui.EDGE_LIGHT : Ui.EDGE;
        Ui.controlBox(g, cx, cy, CW, CH, hov ? Ui.TILE_HOVER : Ui.TILE, edge);
        icon(g, e, cx + 8, cy + 9, 32);
        int tx = cx + 46, tw = CW - 52;
        Ui.text(g, s.font(), Ui.trim(s.font(), e.name(), tw), tx, cy + 10, Ui.TEXT);
        Ui.text(g, s.font(), Ui.trim(s.font(), "v" + e.version(), tw), tx, cy + 22, Ui.MUTED);
        if (e.crit()) Ui.text(g, s.font(), "Crit Client", tx, cy + 34, Ui.DIM);
        else if (!e.authors().isEmpty()) Ui.text(g, s.font(), Ui.trim(s.font(), "by " + e.authors(), tw), tx, cy + 34, Ui.DIM);
        s.region(cx, cy, CW, CH, () -> openDetail(e));
        s.rregion(cx, cy, CW, CH, () -> quickEdit(e));
    }

    private void icon(GuiGraphicsExtractor g, Entry e, int ix, int iy, int size) {
        if (e.crit()) {
            Ui.controlBox(g, ix, iy, size, size, Ui.FIELD, Ui.EDGE);
            String[] art = switch (e.module()) {
                case KEYS -> Pixel.KEYS;
                case SKINS -> Pixel.SKINS;
                case ACCOUNTS -> Pixel.ACCOUNTS;
                case STORE -> Pixel.STORE;
            };
            int sc = Math.max(1, (size - 6) / 9);
            int off = (size - 9 * sc) / 2;
            Pixel.draw(g, art, ix + off, iy + off, sc, Ui.MUTED);
            return;
        }
        Identifier tex = null;
        if (e.container() != null) {
            ModContainer c = e.container();
            tex = Icons.local("mod:" + e.id(), () -> {
                Optional<String> p = c.getMetadata().getIconPath(64);
                if (p.isEmpty()) throw new java.io.IOException("no icon");
                Optional<Path> file = c.findPath(p.get());
                if (file.isEmpty()) throw new java.io.IOException("no icon file");
                return Files.readAllBytes(file.get());
            });
        }
        if (tex != null) {
            g.blit(RenderPipelines.GUI_TEXTURED, tex, ix, iy, 0, 0, size, size, Icons.SIZE, Icons.SIZE, Icons.SIZE, Icons.SIZE);
        } else {
            Ui.controlBox(g, ix, iy, size, size, Ui.FIELD, Ui.EDGE_LIGHT);
            String letter = e.name().isEmpty() ? "?" : e.name().substring(0, 1).toUpperCase(Locale.ROOT);
            Ui.center(g, s.font(), letter, ix + size / 2, iy + size / 2 - 4, Ui.MUTED);
        }
    }

    // ------------------------------------------------------------------ detail

    private void openDetail(Entry e) {
        sel = e;
        view = View.DETAIL;
        dScroll = dTarget = 0;
        canEdit = e.crit() || findConfigFactory(e.id()) != null;
        s.rebuild();
    }

    /** Right-click on a card: straight to its settings, or its page if it has none. */
    private void quickEdit(Entry e) {
        openDetail(e);
        if (canEdit) edit();
    }

    private void backToGallery() {
        view = View.GALLERY;
        s.rebuild();
    }

    private int descTop() { return y + 90; }

    private List<String> descLines() {
        String d = sel.description() == null || sel.description().isBlank() ? "This mod has no description." : sel.description();
        return Ui.wrap(s.font(), d, w - 8);
    }

    private double descMax() {
        if (sel == null) return 0;
        return Math.max(0, descLines().size() * 11 - (y + h - descTop()));
    }

    private void detail(GuiGraphicsExtractor g, int mx, int my) {
        s.button(g, x, y, 62, 16, "< Mods", mx, my, true, false, this::backToGallery);

        icon(g, sel, x, y + 26, 48);
        int tx = x + 60;
        g.text(s.font(), Ui.heading(Ui.trim(s.font(), sel.name(), w - 66)), tx, y + 28, Ui.TEXT, false);
        String meta = "v" + sel.version() + (sel.authors().isEmpty() ? "" : "  by " + sel.authors());
        Ui.text(g, s.font(), Ui.trim(s.font(), meta, w - 66), tx, y + 41, Ui.MUTED);
        if (sel.crit()) Ui.text(g, s.font(), "Built into Crit Client", tx, y + 53, Ui.DIM);
        else Ui.text(g, s.font(), Ui.trim(s.font(), sel.id() + (sel.license().isEmpty() ? "" : "  |  " + sel.license()), w - 66), tx, y + 53, Ui.DIM);

        boolean hasSite = sel.website() != null && !sel.website().isBlank();
        s.button(g, tx, y + 66, 76, 16, "Website", mx, my, hasSite, false, () -> Browser.open(sel.website()));
        s.button(g, tx + 82, y + 66, 76, 16, "Edit", mx, my, canEdit, true, this::edit);
        if (hasSite) Ui.text(g, s.font(), Ui.trim(s.font(), sel.website().replaceFirst("^https?://", ""), w - 60 - 170), tx + 166, y + 70, Ui.DIM);

        Ui.text(g, s.font(), "Description", x, y + 90, Ui.MUTED);
        int top = descTop() + 12, bottom = y + h;
        dTarget = clamp(dTarget, descMax());
        dScroll += (dTarget - dScroll) * 0.35;
        if (Math.abs(dTarget - dScroll) < 0.1) dScroll = dTarget;
        g.enableScissor(x, top, x + w, bottom);
        int ly = top - (int) Math.round(dScroll);
        for (String line : descLines()) {
            if (ly + 9 > top && ly < bottom) Ui.text(g, s.font(), line, x + 2, ly, Ui.TEXT);
            ly += 11;
        }
        g.disableScissor();
        Ui.scrollbar(g, x + w + 3, top, bottom - top, dScroll, Math.max(0, descMax() - 12));
    }

    private void edit() {
        if (sel.module() != null) {
            switch (sel.module()) {
                case KEYS -> s.open(new OverlayEditorScreen(s));
                case SKINS -> s.setTab(CritScreen.TAB_SKINS);
                case ACCOUNTS -> s.setTab(CritScreen.TAB_ACCOUNTS);
                case STORE -> s.setTab(CritScreen.TAB_STORE);
            }
            return;
        }
        Screen cfg = configScreen(sel.id(), s);
        if (cfg != null) s.open(cfg);
    }

    // ------------------------------------------------------------------ other mods' settings screens (Mod Menu API)

    /** The config screen factory a mod registered for Mod Menu, or null. Works by reflection, so Mod Menu isn't required to build. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object findConfigFactory(String modId) {
        try {
            Class api = Class.forName("com.terraformersmc.modmenu.api.ModMenuApi");
            List<EntrypointContainer> list = FabricLoader.getInstance().getEntrypointContainers("modmenu", api);
            for (EntrypointContainer ec : list) {
                if (!ec.getProvider().getMetadata().getId().equals(modId)) continue;
                Object factory = api.getMethod("getModConfigScreenFactory").invoke(ec.getEntrypoint());
                if (factory != null) return factory;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Screen configScreen(String modId, Screen parent) {
        try {
            Object factory = findConfigFactory(modId);
            if (factory == null) return null;
            Class<?> fc = Class.forName("com.terraformersmc.modmenu.api.ConfigScreenFactory");
            return (Screen) fc.getMethod("create", Screen.class).invoke(factory, parent);
        } catch (Throwable t) {
            CritClient.LOG.warn("Couldn't open settings for {}", modId, t);
            return null;
        }
    }
}
