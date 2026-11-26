package dev.crit.client.pages;

import dev.crit.client.CritScreen;
import dev.crit.client.FilePicker;
import dev.crit.client.Net;
import dev.crit.client.keys.OverlayEditorScreen;
import dev.crit.client.modules.BlockOutlineModule;
import dev.crit.client.modules.CrosshairModule;
import dev.crit.client.modules.Module;
import dev.crit.client.modules.Modules;
import dev.crit.client.modules.ScoreboardModule;
import dev.crit.client.modules.SkyModule;
import dev.crit.client.modules.ZoomModule;
import dev.crit.client.sky.CustomSky;
import dev.crit.client.ui.ColorPickerScreen;
import dev.crit.client.ui.Pixel;
import dev.crit.client.ui.Ui;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

/** The Modules tab: a card grid with toggles and settings editors. */
public final class ModulesPage extends Page {
    private enum View { GALLERY, SETTINGS }

    private static final int CW = 140, CH = 70, GAP = 8;
    private static final int SL_LIMIT = 0, SL_SPEED = 1, SL_THICK = 2, SL_OPAC = 3, SL_BLUR = 4,
            SL_SB_LINES = 5, SL_SB_SCALE = 6, SL_SB_X = 7, SL_SB_Y = 8, SL_SB_BG = 9;

    private View view = View.GALLERY;
    private Module sel;
    private double scroll, target;

    // crosshair settings
    private boolean editingTarget;   // false = the idle crosshair, true = the on-target one
    private boolean capturingKey;

    private String xhMsg = "";
    private boolean xhMsgErr;

    // custom sky settings
    private EditBox skyBox;
    private String skyPath = "";

    // zoom sliders (positions remembered so a drag can update them)
    private final int[] slX = new int[10], slW = new int[10];
    private int draggingSlider = -1;

    public ModulesPage(CritScreen s) {
        super(s);
    }

    @Override
    public void init() {
        skyBox = null;
        if (view == View.SETTINGS && sel == Modules.SKY && Modules.SKY.mode() == SkyModule.Mode.IMAGE && skyPathWidth() >= 20) {
            int top = settingsTop();
            skyBox = s.box(x, top + 28, skyPathWidth(), 18, "Path to an image (or drop one on the window)", skyPath, 512, v -> skyPath = v);
        }
    }

    /** Where a module's own controls start: below its title row and its wrapped description. */
    private int settingsTop() {
        return y + 24 + Ui.wrap(s.font(), sel.desc, w).size() * 11 + 10;
    }

    @Override
    public void filesDropped(List<Path> paths) {
        if (view != View.SETTINGS || sel != Modules.SKY || paths.isEmpty()) return;
        Path p = paths.get(0);
        skyPath = p.toString();
        if (skyBox != null) skyBox.setValue(skyPath);
        CustomSky.importImage(p);
    }

    // ------------------------------------------------------------------ layout

    private int cols() { return Math.max(1, (w + GAP) / (CW + GAP)); }

    private double maxScroll() {
        int rows = (Modules.all().size() + cols() - 1) / cols();
        return Math.max(0, rows * (CH + GAP) - (h - 24));
    }

    @Override
    public void shown() {
        view = View.GALLERY;
        sel = null;
        capturingKey = false;
        draggingSlider = -1;
    }

    @Override
    public void hidden() {
        view = View.GALLERY;
        capturingKey = false;
        draggingSlider = -1;
    }

    @Override
    public boolean scroll(double mx, double my, double dy) {
        if (view != View.GALLERY) return false;
        target = Math.max(0, Math.min(target - dy * 40, maxScroll()));
        return true;
    }

    @Override
    public boolean drag(MouseButtonEvent event, double dx, double dy) {
        if (view == View.SETTINGS && draggingSlider >= 0) {
            applySlider(draggingSlider, event.x());
            return true;
        }
        return false;
    }

    @Override
    public boolean release(MouseButtonEvent event) {
        if (draggingSlider >= 0) {
            draggingSlider = -1;
            return true;
        }
        return false;
    }

    public boolean isCapturingKey() {
        return view == View.SETTINGS && sel == Modules.ZOOM && capturingKey;
    }

    @Override
    public boolean key(KeyEvent event) {
        if (isCapturingKey()) {
            if (event.key() != GLFW.GLFW_KEY_ESCAPE) Modules.ZOOM.key(event.key());
            capturingKey = false;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphicsExtractor g, int mx, int my) {
        if (view == View.SETTINGS && sel != null) settings(g, mx, my);
        else gallery(g, mx, my);
    }

    private void gallery(GuiGraphicsExtractor g, int mx, int my) {
        Ui.right(g, s.font(), "click a card to configure", x + w, y + 4, Ui.MUTED);

        target = Math.max(0, Math.min(target, maxScroll()));
        scroll += (target - scroll) * 0.35;
        if (Math.abs(target - scroll) < 0.1) scroll = target;

        int top = y + 24, bottom = y + h;
        int cols = cols();
        g.enableScissor(x, top, x + w, bottom);
        s.clip(top, bottom);
        List<Module> all = Modules.all();
        for (int i = 0; i < all.size(); i++) {
            int cx = x + (i % cols) * (CW + GAP);
            int cy = top + (i / cols) * (CH + GAP) - (int) Math.round(scroll);
            if (cy + CH < top || cy > bottom) continue;
            card(g, all.get(i), cx, cy, mx, my, top, bottom);
        }
        s.unclip();
        g.disableScissor();
    }

    private void card(GuiGraphicsExtractor g, Module m, int cx, int cy, int mx, int my, int top, int bottom) {
        boolean on = m.enabled();
        boolean hov = my >= top && my < bottom && Ui.in(mx, my, cx, cy, CW, CH);
        Ui.controlBox(g, cx, cy, CW, CH, hov ? Ui.TILE_HOVER : Ui.TILE, on ? Ui.ACCENT : hov ? Ui.EDGE_LIGHT : Ui.EDGE);

        Ui.controlBox(g, cx + 9, cy + 9, 30, 30, Ui.FIELD, Ui.EDGE);
        Pixel.drawIcon(g, m.id, cx + 15, cy + 15, 2, on ? Ui.ACCENT : Ui.MUTED);

        int tx = cx + 46, tw = CW - 54;
        Ui.text(g, s.font(), Ui.trim(s.font(), m.name, tw), tx, cy + 11, Ui.TEXT);
        List<String> lines = Ui.wrap(s.font(), m.desc, tw);
        int ly = cy + 25;
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            Ui.text(g, s.font(), Ui.trim(s.font(), lines.get(i), tw), tx, ly, Ui.DIM);
            ly += 10;
        }

        Ui.right(g, s.font(), ">", cx + CW - 10, cy + CH - 16, Ui.MUTED);

        // The card opens settings; registered first so the switch below sits on top of it.
        s.region(cx, cy, CW, CH, () -> openSettings(m));
        s.toggle(g, cx + 9, cy + CH - 18, on, mx, my, m::toggle);
        Ui.text(g, s.font(), on ? "On" : "Off", cx + 41, cy + CH - 16, on ? Ui.ACCENT : Ui.MUTED);
    }

    // ------------------------------------------------------------------ settings

    private void openSettings(Module m) {
        if (m == Modules.OVERLAY) {
            s.open(new OverlayEditorScreen(s, OverlayEditorScreen.Group.KEYS));
            return;
        }
        if (m == Modules.ARMOR) {
            s.open(new OverlayEditorScreen(s, OverlayEditorScreen.Group.ARMOR));
            return;
        }
        if (m == Modules.OVERLAYS) {
            s.open(new OverlayEditorScreen(s, OverlayEditorScreen.Group.HUD));
            return;
        }
        sel = m;
        editingTarget = false;
        capturingKey = false;
        view = View.SETTINGS;
        s.rebuild();
    }

    private void back() {
        view = View.GALLERY;
        sel = null;
        capturingKey = false;
        draggingSlider = -1;
        s.rebuild();
    }

    private void settings(GuiGraphicsExtractor g, int mx, int my) {
        s.button(g, x, y, 62, 16, "< Modules", mx, my, true, false, this::back);
        int nameRoom = w - 74 - 36 - s.font().width("Enabled");
        Ui.text(g, s.font(), Ui.trim(s.font(), sel.name, nameRoom), x + 74, y + 4, Ui.TEXT);

        boolean on = sel.enabled();
        Ui.right(g, s.font(), "Enabled", x + w - 32, y + 4, on ? Ui.ACCENT : Ui.MUTED);
        s.toggle(g, x + w - 26, y + 2, on, mx, my, sel::toggle);

        if (sel == Modules.CROSSHAIR) {
            crosshair(g, mx, my);
            return;
        }

        int dy = y + 24;
        for (String line : Ui.wrap(s.font(), sel.desc, w)) {
            Ui.text(g, s.font(), line, x, dy, Ui.MUTED);
            dy += 11;
        }
        if (sel == Modules.ZOOM) zoom(g, mx, my, dy + 10);
        else if (sel == Modules.MOTION_BLUR) motionBlur(g, mx, my, dy + 14);
        else if (sel == Modules.BLOCK_OUTLINE) blockOutline(g, mx, my, dy + 10);
        else if (sel == Modules.SKY) sky(g, mx, my, dy + 10);
        else if (sel == Modules.SCOREBOARD) scoreboard(g, mx, my, dy + 14);
        else if (sel == Modules.SPRINT) sprint(g, dy + 6);
        else if (sel == Modules.FULLBRIGHT) Ui.text(g, s.font(), "Nothing to set: switch it on and the world is lit up.", x, dy + 8, Ui.MUTED);
        else if (sel == Modules.SUBWAY) Ui.text(g, s.font(), "Nothing to set: it fills the right edge of the screen from top to bottom.", x, dy + 8, Ui.MUTED);
        else Ui.text(g, s.font(), "Open the overlay editor to move each readout.", x, dy + 8, Ui.MUTED);
    }

    private void sprint(GuiGraphicsExtractor g, int dy) {
        Ui.text(g, s.font(), "The sprint key is held for you in the background.", x, dy, Ui.MUTED);
        Ui.text(g, s.font(), "The game still stops it when you are out of food, using an item, or swimming.", x, dy + 12, Ui.MUTED);
    }

    // ------------------------------------------------------------------ crosshair

    private void crosshair(GuiGraphicsExtractor g, int mx, int my) {
        CrosshairModule c = Modules.CROSSHAIR;
        int box = 78, gap = 14;
        int boxTop = y + 26;
        int b1x = x, b2x = x + box + gap;

        previewBox(g, b1x, boxTop, box, false, mx, my);
        previewBox(g, b2x, boxTop, box, true, mx, my);
        Ui.text(g, s.font(), "Current", b1x, boxTop + box + 5, editingTarget ? Ui.DIM : Ui.TEXT);
        Ui.text(g, s.font(), "On target", b2x, boxTop + box + 5, editingTarget ? Ui.TEXT : Ui.DIM);
        int colX = Math.max(b2x + box + 20, x + w - 200), swX = x + w - 26;
        int labelRoom = swX - colX - 4;
        boolean inv = c.invert();
        Ui.text(g, s.font(), Ui.trim(s.font(), "Invert (see-through)", labelRoom), colX, boxTop + 4, Ui.TEXT);
        s.toggle(g, swX, boxTop + 2, inv, mx, my, () -> c.invert(!inv));
        boolean ind = c.attackIndicator();
        Ui.text(g, s.font(), Ui.trim(s.font(), "Hit indicator", labelRoom), colX, boxTop + 30, Ui.TEXT);
        s.toggle(g, swX, boxTop + 28, ind, mx, my, () -> c.attackIndicator(!ind));
        Ui.text(g, s.font(), "Click a box to edit it", colX, boxTop + 58, Ui.DIM);

        int optTop = boxTop + box + 26;
        int sw = 46, tgap = 8;
        int selected = c.texFor(editingTarget);
        int color = c.colorFor(editingTarget);

        Ui.text(g, s.font(), "Texture", x, optTop, Ui.MUTED);
        Ui.text(g, s.font(), Ui.trim(s.font(), "Right-click one you added to delete it", w - 52), x + 52, optTop, Ui.DIM);
        int total = CrosshairModule.count() + 1;                 // + the "Add" tile
        int perRow = Math.max(1, (w + tgap) / (sw + tgap));
        for (int i = 0; i < total; i++) {
            int tx = x + (i % perRow) * (sw + tgap), ty = optTop + 14 + (i / perRow) * (sw + tgap);
            boolean hov = Ui.in(mx, my, tx, ty, sw, sw);
            if (i == CrosshairModule.count()) {
                // the add button: opens File Explorer so you can pick your own picture
                Ui.controlBox(g, tx, ty, sw, sw, hov ? Ui.TILE_HOVER : Ui.TILE, hov ? Ui.ACCENT : Ui.EDGE_LIGHT);
                Ui.center(g, s.font(), "+", tx + sw / 2, ty + sw / 2 - 8, hov ? Ui.ACCENT : Ui.TEXT);
                Ui.center(g, s.font(), "Add", tx + sw / 2, ty + sw - 12, Ui.MUTED);
                s.region(tx, ty, sw, sw, this::addCrosshair);
                continue;
            }
            boolean on = selected == i;
            Ui.controlBox(g, tx, ty, sw, sw, on ? Ui.ACCENT_DARK : hov ? Ui.TILE_HOVER : Ui.TILE, on ? Ui.ACCENT : Ui.EDGE);
            int scale = fit(sw - 10, i);
            int tw = CrosshairModule.texW(i) * scale, th = CrosshairModule.texH(i) * scale;
            CrosshairModule.draw(g, i, c.invert() ? 0xFFFFFFFF : color, false, tx + (sw - tw) / 2, ty + (sw - th) / 2 - 4, scale);
            String nm = CrosshairModule.name(i);
            if (nm.length() > 7) nm = nm.substring(0, 6) + ".";
            Ui.center(g, s.font(), nm, tx + sw / 2, ty + sw - 12, on ? Ui.TEXT : Ui.MUTED);
            final int idx = i;
            s.region(tx, ty, sw, sw, () -> c.texFor(editingTarget, idx));
            s.rregion(tx, ty, sw, sw, () -> deleteCrosshair(idx));
        }
        int texRows = (total + perRow - 1) / perRow;

        int colorY = optTop + 14 + texRows * (sw + tgap) + 12;
        Ui.text(g, s.font(), "Colour", x, colorY, Ui.MUTED);
        boolean colorOn = !c.invert();
        int cw = 52, chh = 22;
        int cy = colorY + 14;
        boolean chov = colorOn && Ui.in(mx, my, x, cy, cw, chh);
        Ui.box(g, x, cy, cw, chh, colorOn ? color : Ui.mix(color, Ui.PANEL, 0.72f), chov ? Ui.EDGE_LIGHT : Ui.EDGE);
        Ui.text(g, s.font(), colorOn ? String.format("#%06X", color & 0xFFFFFF) : "colour off (inverted)",
                x + cw + 12, cy + 7, colorOn ? Ui.TEXT : Ui.DIM);
        if (colorOn) {
            s.region(x, cy, cw, chh, () -> s.open(new ColorPickerScreen(s,
                    editingTarget ? "On-target colour" : "Crosshair colour", color, col -> c.colorFor(editingTarget, col))));
        }
        if (!xhMsg.isEmpty()) Ui.text(g, s.font(), xhMsg, x, cy + chh + 10, xhMsgErr ? Ui.ERR : Ui.MUTED);
    }

    /** Opens File Explorer, and adds the picture you choose as a new crosshair. */
    private void addCrosshair() {
        xhMsg = "Choose an image in the window that opened...";
        xhMsgErr = false;
        Net.POOL.execute(() -> {
            Path p = FilePicker.pickImage("Choose a 16x16 crosshair image");
            Minecraft.getInstance().execute(() -> {
                if (p == null) {
                    xhMsg = "";
                    return;
                }
                int idx = CrosshairModule.addCustom(p);
                if (idx == -2) {
                    xhMsg = "Crosshairs must be exactly " + CrosshairModule.CUSTOM_SIZE + "x" + CrosshairModule.CUSTOM_SIZE
                            + " pixels (that one is " + CrosshairModule.lastRejectedSize + ").";
                    xhMsgErr = true;
                } else if (idx < 0) {
                    xhMsg = "That file isn't an image Crit Client can read.";
                    xhMsgErr = true;
                } else {
                    Modules.CROSSHAIR.texFor(editingTarget, idx);
                    xhMsg = "Added " + CrosshairModule.name(idx) + ". Right-click it to delete it.";
                }
            });
        });
    }

    /** Right click on a texture tile: deletes it if the player added it, otherwise says why not. */
    private void deleteCrosshair(int index) {
        if (!CrosshairModule.isCustom(index)) {
            xhMsg = "Only crosshairs you added can be deleted.";
            xhMsgErr = false;
            return;
        }
        String name = CrosshairModule.name(index);
        if (Modules.CROSSHAIR.removeCustom(index)) {
            xhMsg = "Deleted " + name + ".";
            xhMsgErr = false;
        }
    }

    private void previewBox(GuiGraphicsExtractor g, int bx, int by, int size, boolean target, int mx, int my) {
        CrosshairModule c = Modules.CROSSHAIR;
        boolean selected = editingTarget == target;
        boolean hov = Ui.in(mx, my, bx, by, size, size);
        Ui.controlBox(g, bx, by, size, size, Ui.FIELD, selected ? Ui.ACCENT : hov ? Ui.EDGE_LIGHT : Ui.EDGE);
        if (selected) g.outline(bx - 1, by - 1, size + 2, size + 2, Ui.ACCENT);

        int texIndex = c.texFor(target);
        int color = c.colorFor(target);
        int scale = fit(size - 10, texIndex);
        int tw = CrosshairModule.texW(texIndex) * scale, th = CrosshairModule.texH(texIndex) * scale;
        boolean see = c.invert();
        CrosshairModule.draw(g, texIndex, see ? 0xFFFFFFFF : color, see,
                bx + (size - tw) / 2, by + (size - th) / 2, scale);

        s.region(bx, by, size, size, () -> editingTarget = target);
    }

    private static int fit(int max, int texIndex) {
        int longest = Math.max(1, Math.max(CrosshairModule.texW(texIndex), CrosshairModule.texH(texIndex)));
        return Math.max(1, Math.min(5, max / longest));
    }

    // ------------------------------------------------------------------ motion blur

    private void motionBlur(GuiGraphicsExtractor g, int mx, int my, int top) {
        double v = Modules.MOTION_BLUR.strength();
        slider(g, SL_BLUR, x, top + 12, 230, (v - 0.1) / 0.8, "Strength", Math.round(v * 100) + "%", mx, my);
        Ui.text(g, s.font(), "Higher means longer trails. The HUD stays sharp.", x, top + 48, Ui.DIM);
    }

    // ------------------------------------------------------------------ block outline

    private void blockOutline(GuiGraphicsExtractor g, int mx, int my, int top) {
        BlockOutlineModule b = Modules.BLOCK_OUTLINE;
        boolean rb = b.rainbow();
        int rgb = b.color();

        Ui.text(g, s.font(), "Colour", x, top, Ui.MUTED);
        boolean hov = Ui.in(mx, my, x, top + 12, 52, 22);
        Ui.box(g, x, top + 12, 52, 22, rb ? Ui.FIELD : 0xFF000000 | rgb, hov ? Ui.TEXT : Ui.EDGE_LIGHT);
        Ui.text(g, s.font(), rb ? "Rainbow" : String.format("#%06X", rgb), x + 60, top + 19, rb ? Ui.ACCENT : Ui.TEXT);
        if (!rb) {
            s.region(x, top + 12, 52, 22, () -> s.open(new ColorPickerScreen(s, "Outline colour", rgb, col -> b.color(col))));
        }

        slider(g, SL_THICK, x, top + 64, 230, (b.thickness() - 0.5) / 4.5, "Thickness", String.format("%.1fx", b.thickness()), mx, my);
        slider(g, SL_OPAC, x, top + 106, 230, (b.opacity() - 0.1) / 0.9, "Opacity", Math.round(b.opacity() * 100) + "%", mx, my);

        Ui.text(g, s.font(), "Rainbow", x, top + 128, Ui.TEXT);
        s.toggle(g, x + 110, top + 126, rb, mx, my, () -> b.rainbow(!rb));

        // a small preview of the outline around a block
        int px = x + 270, py = top + 8, size = 54;
        Ui.box(g, px, py, size, size, Ui.FIELD, Ui.EDGE);
        int argb = b.argb();
        int t = Math.max(1, Math.min(5, (int) Math.round(b.thickness() * 1.5)));
        int in = 10;
        for (int i = 0; i < t; i++) g.outline(px + in + i, py + in + i, size - 2 * in - 2 * i, size - 2 * in - 2 * i, argb);
    }

    // ------------------------------------------------------------------ custom sky

    private int skyPathWidth() { return Math.min(w - 150, 280); }

    private void sky(GuiGraphicsExtractor g, int mx, int my, int top) {
        SkyModule m = Modules.SKY;
        boolean image = m.mode() == SkyModule.Mode.IMAGE;
        s.chip(g, x, top, 70, 16, "Colour", !image, mx, my, () -> setSkyMode(SkyModule.Mode.COLOR));
        s.chip(g, x + 76, top, 70, 16, "Image", image, mx, my, () -> setSkyMode(SkyModule.Mode.IMAGE));

        if (!image) {
            int rgb = m.color();
            Ui.text(g, s.font(), "Sky colour", x, top + 28, Ui.MUTED);
            boolean hov = Ui.in(mx, my, x, top + 40, 52, 22);
            Ui.box(g, x, top + 40, 52, 22, 0xFF000000 | rgb, hov ? Ui.TEXT : Ui.EDGE_LIGHT);
            Ui.text(g, s.font(), String.format("#%06X", rgb), x + 60, top + 47, Ui.TEXT);
            s.region(x, top + 40, 52, 22, () -> s.open(new ColorPickerScreen(s, "Sky colour", rgb, col -> m.color(col))));
            Ui.text(g, s.font(), "The sun, moon and stars stay. Overworld only.", x, top + 72, Ui.DIM);
            return;
        }

        int buttonsX = x + skyPathWidth() + 6;
        int buttonsY = top + 28;
        s.button(g, buttonsX, buttonsY, 70, 18, "Browse...", mx, my, true, false, this::browseSky);
        s.button(g, buttonsX + 76, buttonsY, 56, 18, "Load", mx, my, true, true, () -> {
            String raw = skyPath.trim().replace("\"", "");
            if (raw.isEmpty()) return;
            try {
                CustomSky.importImage(Path.of(raw));
            } catch (RuntimeException e) {
                // an invalid path: the status line below says so through importImage's own error
            }
        });

        String st = CustomSky.status();
        if (st.isEmpty() && CustomSky.hasImage() && !m.imageName().isEmpty()) st = "Using " + m.imageName();
        if (st.isEmpty()) st = "No image yet.";
        Ui.text(g, s.font(), st, x, top + 54, CustomSky.statusError() ? Ui.ERR : Ui.MUTED);

        boolean pano = m.style() == SkyModule.Style.PANORAMA;
        s.button(g, x, top + 72, 170, 18, pano ? "Style: 360 panorama" : "Style: same on each side", mx, my, true, false,
                () -> m.style(pano ? SkyModule.Style.SIDES : SkyModule.Style.PANORAMA));

        boolean mir = m.mirror();
        Ui.text(g, s.font(), "Mirror image", x, top + 100, Ui.TEXT);
        s.toggle(g, x + 110, top + 98, mir, mx, my, () -> m.mirror(!mir));
        Ui.text(g, s.font(), "Flip it if the picture looks backwards.", x + 150, top + 100, Ui.DIM);

        Ui.text(g, s.font(), "360 panoramas (2:1) look best. JPG or PNG. Drop a file on the window.", x, top + 120, Ui.DIM);
    }

    /** Opens File Explorer for the sky image and loads whatever is picked. */
    private void browseSky() {
        Net.POOL.execute(() -> {
            Path p = FilePicker.pickImage("Choose a sky image");
            if (p == null) return;
            Minecraft.getInstance().execute(() -> {
                skyPath = p.toString();
                if (skyBox != null) skyBox.setValue(skyPath);
                CustomSky.importImage(p);
            });
        });
    }

    private void setSkyMode(SkyModule.Mode mode) {
        Modules.SKY.mode(mode);
        s.rebuild();
    }

    // ------------------------------------------------------------------ zoom

    private void zoom(GuiGraphicsExtractor g, int mx, int my, int top) {
        ZoomModule z = Modules.ZOOM;
        s.button(g, x, top, 150, 18, capturingKey ? "Press a key..." : "Key: " + z.keyName(), mx, my, true, false,
                () -> capturingKey = true);
        Ui.text(g, s.font(), capturingKey ? "Esc cancels" : "Hold it to zoom in", x + 160, top + 6, Ui.DIM);

        slider(g, SL_LIMIT, x, top + 50, 230, (z.limit() - 1.5) / 8.5, "Limit", String.format("%.1fx", z.limit()), mx, my);
        slider(g, SL_SPEED, x, top + 92, 230, (z.speed() - 1) / 9.0, "Speed", String.format("%.0f", z.speed()), mx, my);

        boolean sm = z.smooth();
        Ui.text(g, s.font(), "Smooth zoom", x, top + 124, Ui.TEXT);
        s.toggle(g, x + 110, top + 122, sm, mx, my, () -> z.smooth(!sm));
        Ui.text(g, s.font(), sm ? "eases in fast and settles slow" : "snaps straight to the limit", x + 150, top + 124, Ui.DIM);
    }

    private void slider(GuiGraphicsExtractor g, int id, int x, int y, int w, double v01, String label, String value, int mx, int my) {
        slX[id] = x;
        slW[id] = w;
        v01 = Math.max(0, Math.min(1, v01));
        Ui.text(g, s.font(), label, x, y - 12, Ui.MUTED);
        Ui.right(g, s.font(), value, x + w, y - 12, Ui.TEXT);
        Ui.slider(g, x, y, w, 8, v01, draggingSlider == id || Ui.in(mx, my, x - 4, y - 6, w + 8, 18));
        s.region(x - 4, y - 6, w + 8, 18, () -> {
            draggingSlider = id;
            applySlider(id, mx);
        });
    }

    private void applySlider(int id, double mouseX) {
        int w = slW[id];
        double v = Math.max(0, Math.min(1, (mouseX - slX[id]) / Math.max(1, w - 1)));
        if (id == SL_LIMIT) Modules.ZOOM.limit(1.5 + v * 8.5);
        else if (id == SL_SPEED) Modules.ZOOM.speed(1 + v * 9.0);
        else if (id == SL_THICK) Modules.BLOCK_OUTLINE.thickness(0.5 + v * 4.5);
        else if (id == SL_BLUR) Modules.MOTION_BLUR.strength(0.1 + v * 0.8);
        else if (id == SL_OPAC) Modules.BLOCK_OUTLINE.opacity(0.1 + v * 0.9);
        else if (id == SL_SB_LINES) Modules.SCOREBOARD.maxLines(1 + (int) Math.round(v * (ScoreboardModule.MAX_LINES - 1)));
        else if (id == SL_SB_SCALE) Modules.SCOREBOARD.scale(Math.abs(v - 0.333) < 0.02 ? 1.0 : Math.round((0.5 + v * 1.5) * 20) / 20.0);
        else if (id == SL_SB_X) Modules.SCOREBOARD.offsetX(snapOffset(v));
        else if (id == SL_SB_Y) Modules.SCOREBOARD.offsetY(snapOffset(v));
        else if (id == SL_SB_BG) Modules.SCOREBOARD.background(Math.abs(v - 0.5) < 0.02 ? 1.0 : Math.round(v * 2.0 * 20) / 20.0);
    }

    /** A slider position (0..1) as a -200..200 pixel offset that snaps to 0 near the middle. */
    private static int snapOffset(double v) {
        int px = (int) Math.round(-200 + v * 400);
        return Math.abs(px) <= 4 ? 0 : px;
    }

    // ------------------------------------------------------------------ scoreboard

    private void scoreboard(GuiGraphicsExtractor g, int mx, int my, int top) {
        ScoreboardModule sb = Modules.SCOREBOARD;
        slider(g, SL_SB_LINES, x, top + 12, 230, (sb.maxLines() - 1) / (double) (ScoreboardModule.MAX_LINES - 1),
                "Max lines", sb.maxLines() + (sb.maxLines() == ScoreboardModule.VANILLA_LINES ? " (game default)" : ""), mx, my);
        slider(g, SL_SB_SCALE, x, top + 42, 230, (sb.scale() - 0.5) / 1.5, "Size", Math.round(sb.scale() * 100) + "%", mx, my);
        slider(g, SL_SB_X, x, top + 72, 230, (sb.offsetX() + 200) / 400.0, "Move left / right", sb.offsetX() + " px", mx, my);
        slider(g, SL_SB_Y, x, top + 102, 230, (sb.offsetY() + 200) / 400.0, "Move up / down", sb.offsetY() + " px", mx, my);
        slider(g, SL_SB_BG, x, top + 132, 230, sb.background() / 2.0, "Background darkness", Math.round(sb.background() * 100) + "%", mx, my);
        s.button(g, x + 246, top + 4, 90, 18, "Reset all", mx, my, true, false, sb::reset);
    }
}
