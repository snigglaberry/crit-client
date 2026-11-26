package dev.crit.client;

import dev.crit.client.pages.AccountsPage;
import dev.crit.client.pages.ModsPage;
import dev.crit.client.pages.ModulesPage;
import dev.crit.client.pages.Page;
import dev.crit.client.pages.SettingsPage;
import dev.crit.client.pages.SkinsPage;
import dev.crit.client.pages.StorePage;
import dev.crit.client.store.Kind;
import dev.crit.client.title.MenuBackground;
import dev.crit.client.ui.Pixel;
import dev.crit.client.ui.Theme;
import dev.crit.client.ui.Ui;
import dev.crit.client.ui.UiThemes;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;

/**
 * The Crit Client menu: a sidebar popup with shared pages and controls.
 * Clickable things register a region while drawing; mouseClicked runs the topmost one under the cursor.
 */
public class CritScreen extends Screen {
    public static final int TAB_MODS = 0, TAB_SKINS = 1, TAB_STORE = 2, TAB_MODULES = 3, TAB_SETTINGS = 4, TAB_ACCOUNTS = 5;
    private static final String[] TAB_NAMES = {"Mods", "Skins", "Store", "Modules", "Settings", "Accounts"};
    private static final int[] SIDEBAR_TABS = {TAB_MODS, TAB_SKINS, TAB_STORE, TAB_MODULES, TAB_SETTINGS};
    private static final int HDR = 30, SW = 100;
    private static int lastTab = TAB_MODS;

    private record Region(int x, int y, int w, int h, int clipTop, int clipBot, Runnable run) {}
    private record Field(int w, int h, EditBox box) {
        int x() { return box.getX() - 5; }
        int y() { return box.getY() - (h - 10) / 2; }
    }

    private final Screen parent;
    private int tab;
    private final Page[] pages = new Page[TAB_NAMES.length];
    private final List<Region> regions = new ArrayList<>();
    private final List<Region> rightRegions = new ArrayList<>();
    private final List<Field> fields = new ArrayList<>();
    private int clipTop = Integer.MIN_VALUE, clipBot = Integer.MAX_VALUE;
    private int px, py, pw, ph;

    public CritScreen(Screen parent) {
        this(parent, lastTab);
    }

    public CritScreen(Screen parent, int tab) {
        super(Component.literal("Crit Client"));
        this.parent = parent;
        this.tab = (tab < 0 || tab >= pages.length) ? TAB_MODS : tab;
        lastTab = this.tab;
        pages[TAB_MODS] = new ModsPage(this);
        pages[TAB_SKINS] = new SkinsPage(this);
        pages[TAB_STORE] = new StorePage(this);
        pages[TAB_MODULES] = new ModulesPage(this);
        pages[TAB_SETTINGS] = new SettingsPage(this);
        pages[TAB_ACCOUNTS] = new AccountsPage(this);
    }

    /** The popup opened straight on the store, on the given kind of addon. */
    public static CritScreen store(Screen parent, Kind kind) {
        CritScreen cs = new CritScreen(parent, TAB_STORE);
        ((StorePage) cs.pages[TAB_STORE]).setKind(kind);
        return cs;
    }

    // ------------------------------------------------------------------ helpers for pages

    public Font font() { return Ui.font(this.font); }
    public Minecraft mc() { return this.minecraft; }
    public void rebuild() { rebuildWidgets(); }
    public void open(Screen next) { this.minecraft.setScreenAndShow(next); }
    public void addControl(AbstractWidget control) { addRenderableWidget(control); }

    public boolean isCapturingKey() {
        return tab == TAB_MODULES && ((ModulesPage) pages[TAB_MODULES]).isCapturingKey();
    }

    public void setTab(int t) {
        if (t == tab) return;
        pages[tab].hidden();
        tab = t;
        lastTab = tab;
        pages[tab].shown();
        rebuildWidgets();
    }

    public void region(int x, int y, int w, int h, Runnable run) {
        regions.add(new Region(x, y, w, h, clipTop, clipBot, run));
    }

    /** Same as region(), but for a right click. */
    public void rregion(int x, int y, int w, int h, Runnable run) {
        rightRegions.add(new Region(x, y, w, h, clipTop, clipBot, run));
    }

    /** Clicks outside [top, bottom) are ignored for regions registered while a clip is set (scrolling lists). */
    public void clip(int top, int bottom) {
        clipTop = top;
        clipBot = bottom;
    }

    public void unclip() {
        clipTop = Integer.MIN_VALUE;
        clipBot = Integer.MAX_VALUE;
    }

    /** A themed text field. Add it from Page#init(). */
    public EditBox box(int x, int y, int w, int h, String hint, String value, int max, Consumer<String> onChange) {
        EditBox e = new EditBox(font(), x + 5, y + (h - 10) / 2, w - 10, 10, Component.literal(hint));
        e.setBordered(false);
        e.setMaxLength(max);
        e.setHint(Component.literal(hint));
        e.setValue(value);
        e.setResponder(onChange);
        addRenderableWidget(e);
        fields.add(new Field(w, h, e));
        return e;
    }

    public void button(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int mx, int my,
                       boolean enabled, boolean primary, Runnable run) {
        boolean hov = enabled && Ui.in(mx, my, x, y, w, h) && my >= clipTop && my < clipBot;
        int fill, edge, text;
        if (!enabled) { fill = Ui.TILE; edge = Ui.EDGE; text = Ui.DIM; }
        else if (primary) {
            fill = hov ? Ui.ACCENT_HOVER : Ui.ACCENT;
            edge = fill;
            text = Theme.onColor(Ui.mix(Ui.PANEL, fill, ARGB.alpha(Ui.controlColor(fill)) / 255f));
        } else { fill = hov ? Ui.TILE_HOVER : Ui.TILE; edge = hov ? Ui.ACCENT_DARK : Ui.EDGE_LIGHT; text = Ui.TEXT; }
        Ui.controlBox(g, x, y, w, h, fill, edge);
        Font font = font();
        String caption = Ui.trim(font, label, w - 6);
        // The world can show through the tint at low opacity, so give light primary labels a shadow.
        boolean shadow = enabled && primary && text == 0xFFFFFFFF && Theme.panelOpacity() < 100;
        g.text(font, caption, x + w / 2 - font.width(caption) / 2, y + (h - 8) / 2, text, shadow);
        if (enabled) region(x, y, w, h, run);
    }

    /** A small tab-like toggle (sub tabs inside a page). */
    public void chip(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, boolean active, int mx, int my, Runnable run) {
        boolean hov = Ui.in(mx, my, x, y, w, h) && my >= clipTop && my < clipBot;
        Ui.controlBox(g, x, y, w, h, active ? Ui.ACCENT_DARK : hov ? Ui.TILE_HOVER : Ui.TILE, active ? Ui.ACCENT : Ui.EDGE);
        Font font = font();
        Ui.center(g, font, Ui.trim(font, label, w - 6), x + w / 2, y + (h - 8) / 2, active ? Ui.TEXT : Ui.MUTED);
        region(x, y, w, h, run);
    }

    public void toggle(GuiGraphicsExtractor g, int x, int y, boolean on, int mx, int my, Runnable run) {
        boolean hov = Ui.in(mx, my, x, y, 26, 12) && my >= clipTop && my < clipBot;
        Ui.switchControl(g, x, y, on, hov);
        region(x, y, 26, 12, run);
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void init() {
        fields.clear();
        regions.clear();
        rightRegions.clear();
        addRenderableOnly((g, mx, my, pt) -> drawBack(g, mx, my));
        pw = Math.min(width - 16, 560);
        ph = Math.min(height - 16, 340);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        pages[tab].layout(px + SW + 10, py + HDR + 8, pw - SW - 20, ph - HDR - 16);
        pages[tab].init();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float pt) {
        if (minecraft.level == null && (!(parent instanceof TitleScreen) || !MenuBackground.draw(g, width, height))) {
            extractPanorama(g, pt);
        }
        float opacity = Theme.panelOpacity() / 100f;
        int backdrop = ARGB.multiplyAlpha(Ui.BACKDROP, opacity);
        if (ARGB.alpha(backdrop) > 0) {
            // Only dim outside the panel, so a 50% panel really blends at 50% with the background.
            g.fill(0, 0, width, py, backdrop);
            g.fill(0, py + ph, width, height, backdrop);
            g.fill(0, py, px, py + ph, backdrop);
            g.fill(px + pw, py, width, py + ph, backdrop);
            Ui.panelCutout(g, px, py, pw, ph, backdrop);
        }
        minecraft.gui.hud.extractDeferredSubtitles();
    }

    /** Panel, navigation and text-field backgrounds: everything that sits behind the widgets. */
    private void drawBack(GuiGraphicsExtractor g, int mx, int my) {
        regions.clear();
        rightRegions.clear();
        unclip();
        drawPopup(g, mx, my);
        for (Field f : fields) {
            if (!f.box().visible) continue;
            Ui.controlBox(g, f.x(), f.y(), f.w(), f.h(), Ui.FIELD, f.box().isFocused() ? Ui.ACCENT : Ui.EDGE_LIGHT);
        }
    }

    private void drawPopup(GuiGraphicsExtractor g, int mx, int my) {
        Font font = font();
        float opacity = Theme.panelOpacity() / 100f;
        int edge = ARGB.multiplyAlpha(Ui.EDGE, opacity);
        boolean smooth = UiThemes.rounded();
        if (opacity > 0) {
            int rim = ARGB.multiplyAlpha(Ui.EDGE_LIGHT, opacity);
            if (smooth) {
                int top = Theme.colorway() == Theme.Colorway.AMOLED ? Ui.PANEL : Ui.mix(Ui.PANEL, Ui.TILE, 0.65f);
                Ui.panel(g, px, py, pw, ph, ARGB.multiplyAlpha(top, opacity), ARGB.multiplyAlpha(Ui.PANEL, opacity), rim);
            } else {
                // Fill disjoint sections so the header and sidebar don't get an extra layer of opacity.
                g.outline(px, py, pw, ph, rim);
                g.fill(px + 1, py + 1, px + pw - 1, py + HDR, ARGB.multiplyAlpha(Ui.HEADER, opacity));
                g.fill(px + 1, py + HDR, px + pw - 1, py + HDR + 1, edge);
                g.fill(px + 1, py + HDR + 1, px + SW, py + ph - 1, ARGB.multiplyAlpha(Ui.SIDEBAR, opacity));
                g.fill(px + SW, py + HDR + 1, px + SW + 1, py + ph - 1, edge);
                g.fill(px + SW + 1, py + HDR + 1, px + pw - 1, py + ph - 1, ARGB.multiplyAlpha(Ui.PANEL, opacity));
            }
        }

        drawSidebar(g, mx, my, font, smooth, opacity, edge);
    }

    private void drawSidebar(GuiGraphicsExtractor g, int mx, int my, Font font, boolean smooth, float opacity, int edge) {
        int step = Math.clamp((ph - HDR - 50) / SIDEBAR_TABS.length, 16, 24), tabH = step - 4;
        for (int i = 0; i < SIDEBAR_TABS.length; i++) {
            int ty = py + HDR + 10 + i * step;
            int tx = px + 6, tw = SW - 12;
            int idx = SIDEBAR_TABS[i];
            boolean on = idx == tab, hov = Ui.in(mx, my, tx, ty, tw, tabH);
            if (on || hov) {
                int fill = on ? (smooth ? Ui.mix(Ui.TILE_HOVER, Ui.ACCENT, 0.13f) : Ui.TILE_HOVER) : Ui.TILE;
                Ui.surface(g, tx, ty, tw, tabH, Ui.controlColor(fill));
            }
            if (on) Ui.surface(g, tx + (smooth ? 3 : 0), ty + 4, 2, tabH - 8, Ui.ACCENT);
            int textY = ty + (tabH - 8) / 2;
            if (on && smooth) g.text(font, Ui.heading(TAB_NAMES[idx]), tx + 10, textY, Ui.TEXT, false);
            else Ui.text(g, font, TAB_NAMES[idx], tx + 10, textY, on ? Ui.TEXT : Ui.MUTED);
            region(tx, ty, tw, tabH, () -> setTab(idx));
        }
        // account, bottom of the sidebar
        int fy = py + ph - 40;
        if (!smooth && opacity > 0) g.fill(px + 1, fy - 1, px + SW, fy, edge);
        boolean accHov = Ui.in(mx, my, px + 1, fy, SW - 1, 39);
        if (accHov || tab == TAB_ACCOUNTS) {
            if (smooth) Ui.controlBox(g, px + 6, fy, SW - 12, 34, Ui.TILE, tab == TAB_ACCOUNTS ? Ui.ACCENT_DARK : Ui.EDGE);
            else g.fill(px + 1, fy, px + SW, py + ph - 1, Ui.controlColor(Ui.TILE));
        }
        if (tab == TAB_ACCOUNTS && !smooth) g.fill(px + 1, fy + 6, px + 3, fy + 33, Ui.ACCENT);
        drawFace(g, px + 10, fy + 11);
        String who = Faces.name().isEmpty() ? "Account" : Faces.name();
        Ui.text(g, font, Ui.trim(font, who, SW - 42), px + 32, fy + 11, Ui.TEXT);
        Ui.text(g, font, "Accounts", px + 32, fy + 22, Ui.MUTED);
        region(px + 1, fy, SW - 1, 39, () -> setTab(TAB_ACCOUNTS));
    }

    private void drawFace(GuiGraphicsExtractor g, int fx, int fy) {
        int[] f = Faces.face();
        if (f == null) {
            g.fill(fx, fy, fx + 16, fy + 16, Ui.EDGE_LIGHT);
            g.fill(fx + 4, fy + 6, fx + 6, fy + 8, Ui.MUTED);
            g.fill(fx + 10, fy + 6, fx + 12, fy + 8, Ui.MUTED);
            return;
        }
        for (int i = 0; i < 64; i++) {
            int cx = fx + (i % 8) * 2, cy = fy + (i / 8) * 2;
            g.fill(cx, cy, cx + 2, cy + 2, f[i]);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        drawPopupHeader(g, mx, my);
        pages[tab].render(g, mx, my);
    }

    private void drawPopupHeader(GuiGraphicsExtractor g, int mx, int my) {
        Font font = font();
        Pixel.logo(g, px + 10, py + 6, 2);
        Component title = Ui.heading("Crit Client");
        g.text(font, title, px + 34, py + 11, Ui.TEXT, false);
        int versionX = px + 34 + font.width(title) + 6;
        String version = "v" + CritClient.version();
        Ui.text(g, font, version, versionX, py + 11, Ui.MUTED);

        int closeX = px + pw - 22, closeY = py + 7;
        boolean closeHov = Ui.in(mx, my, closeX, closeY, 16, 16);
        Ui.controlBox(g, closeX, closeY, 16, 16, closeHov ? Ui.TILE_HOVER : Ui.TILE, closeHov ? Ui.ERR : Ui.EDGE);
        Ui.center(g, font, "×", closeX + 8, closeY + 4, closeHov ? Ui.ERR : Ui.TEXT);
        region(closeX, closeY, 16, 16, this::onClose);

        String site = "critlauncher.xyz";
        int siteW = font.width(site), siteX = closeX - 10 - siteW;
        if (siteX > versionX + font.width(version) + 8) {
            boolean siteHov = Ui.in(mx, my, siteX - 2, py + 8, siteW + 4, 14);
            Ui.text(g, font, site, siteX, py + 11, siteHov ? Ui.ACCENT_HOVER : Ui.ACCENT);
            region(siteX - 2, py + 8, siteW + 4, 14, () -> Browser.open(CritClient.SITE));
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        if (pages[tab].click(event)) return true;
        if (super.mouseClicked(event, dbl)) return true;
        double mx = event.x(), my = event.y();
        if (event.button() == 0) {
            // the text box widgets are only 10px tall; clicking anywhere in the drawn field should focus it
            for (Field f : fields) {
                if (f.box().visible && Ui.in(mx, my, f.x(), f.y(), f.w(), f.h())) {
                    setFocused(f.box());
                    f.box().setFocused(true);
                    return true;
                }
            }
        }
        List<Region> list = event.button() == 0 ? regions : event.button() == 1 ? rightRegions : null;
        if (list == null) return false;
        for (int i = list.size() - 1; i >= 0; i--) {
            Region r = list.get(i);
            if (Ui.in(mx, my, r.x(), r.y(), r.w(), r.h()) && my >= r.clipTop() && my < r.clipBot()) {
                r.run().run();
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (pages[tab].scroll(mx, my, sy)) return true;
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (pages[tab].drag(event, dx, dy)) return true;
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (pages[tab].release(event)) return true;
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (pages[tab].key(event)) return true;
        return super.keyPressed(event);
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        pages[tab].filesDropped(paths);
    }

    @Override
    public void onClose() {
        for (Page p : pages) if (p != null) p.hidden();
        minecraft.gui.setScreen(parent);
    }
}
