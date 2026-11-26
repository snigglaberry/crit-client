package dev.crit.client.pages;

import dev.crit.client.CritScreen;
import dev.crit.client.title.MenuBackground;
import dev.crit.client.title.MenuSettings;
import dev.crit.client.ui.Theme;
import dev.crit.client.ui.Ui;
import dev.crit.client.ui.UiThemes;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Appearance settings and the main menu background. The theme picker floats over the rest of the page. */
public final class SettingsPage extends Page {
    private static final int COLORWAY_H = 30, ACC_W = 25, ACC_H = 19, GAP = 6;
    private static final int MENU_H = 84;
    private double scroll;
    private boolean opacityDirty;
    private ThemeDropdown dropdown;

    public SettingsPage(CritScreen s) {
        super(s);
    }

    @Override
    public void init() {
        saveOpacity();
        ThemeDropdown previous = dropdown;
        dropdown = new ThemeDropdown(x, y + 16, Math.max(1, Math.min(208, w)));
        if (previous != null && previous.open) {
            // Resizing can rebuild the page while the picker is open.
            dropdown.highlighted = previous.highlighted;
            dropdown.first = previous.first;
            dropdown.revealHighlight();
            dropdown.open = true;
        }
        s.addControl(dropdown);
        s.addControl(new OpacitySlider(x, y + 46, Math.min(w, 230)));
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mx, int my) {
        g.text(s.font(), Ui.heading("Appearance"), x, y + 1, Ui.TEXT, false);
        int top = scrollTop(), bottom = scrollBottom();
        if (bottom > top) {
            double max = maxScroll();
            scroll = Math.min(scroll, max);
            int contentY = top + 2 - (int) scroll;
            Ui.rect(g, x, top - 5, w, 1, Ui.controlColor(Ui.EDGE));
            g.enableScissor(x, top, x + w, bottom);
            s.clip(top, bottom);
            int hoverY = !dropdown.open && my >= top && my < bottom ? my : Integer.MIN_VALUE;
            drawSwatches(g, mx, hoverY, contentY);
            drawMenuSettings(g, mx, hoverY, contentY + menuOffset());
            s.unclip();
            g.disableScissor();
            Ui.scrollbar(g, x + w, top, bottom - top, scroll, max);
        }
        dropdown.renderPopup(g, mx, my);
    }

    private void drawMenuSettings(GuiGraphicsExtractor g, int mx, int my, int top) {
        g.text(s.font(), Ui.heading("Main menu"), x, top, Ui.TEXT, false);
        boolean panorama = MenuSettings.panorama();
        toggle(g, mx, my, top + 14, "Crit panorama", panorama, () -> MenuSettings.setPanorama(!panorama));
        int statusX = x + 42 + s.font().width("Crit panorama");
        Ui.text(g, s.font(), Ui.trim(s.font(), panorama ? "On" : "Off: vanilla", x + w - statusX), statusX, top + 16, Ui.MUTED);
        boolean custom = MenuSettings.customBackground();
        toggle(g, mx, my, top + 32, "Custom background", custom, () -> MenuSettings.setCustomBackground(!custom));

        s.button(g, x, top + 50, Math.min(w, 112), 18, "Choose image...", mx, my, !MenuBackground.busy(), false,
                () -> MenuBackground.browse(s.mc()));
        Ui.text(g, s.font(), Ui.trim(s.font(), MenuBackground.status(), w), x, top + 72,
                MenuBackground.error() ? Ui.ERR : Ui.MUTED);
    }

    private void toggle(GuiGraphicsExtractor g, int mx, int my, int by, String label, boolean on, Runnable run) {
        s.toggle(g, x, by, on, mx, my, run);
        Ui.text(g, s.font(), label, x + 34, by + 2, Ui.TEXT);
        s.region(x + 26, by, 8 + s.font().width(label), 12, run);
    }

    private void drawSwatches(GuiGraphicsExtractor g, int mx, int my, int top) {
        Font font = s.font();
        g.text(font, Ui.heading("Colorways"), x, top, Ui.TEXT, false);
        Theme.Colorway[] colorways = Theme.Colorway.values();
        int cols = colorColumns(), cardW = Math.min(86, (w - (cols - 1) * GAP) / cols);
        for (int i = 0; i < colorways.length; i++) {
            Theme.Colorway choice = colorways[i];
            int bx = x + (i % cols) * (cardW + GAP), by = top + 14 + (i / cols) * (COLORWAY_H + GAP);
            boolean on = Theme.colorway() == choice;
            boolean hov = Ui.in(mx, my, bx, by, cardW, COLORWAY_H);
            int color = choice.swatch(), rim = on ? Ui.ACCENT : hov ? Ui.TEXT : Ui.DIM;
            Ui.controlBox(g, bx, by, cardW, COLORWAY_H, color, rim);
            Ui.surface(g, bx + 7, by + 7, 11, 3, on ? Ui.ACCENT : Theme.lighten(color, 0.20f));
            Ui.text(g, font, Ui.trim(font, choice.label(), cardW - 14), bx + 7, by + 16, 0xFFF4F4F4);
            s.region(bx, by, cardW, COLORWAY_H, () -> Theme.setColorway(choice));
        }

        int accY = top + accentOffset(), accCols = columns(ACC_W);
        g.text(font, Ui.heading("Accent color"), x, accY, Ui.TEXT, false);
        for (int i = 0; i < Theme.ACCENTS.length; i++) {
            int bx = x + (i % accCols) * (ACC_W + GAP), by = accY + 14 + (i / accCols) * (ACC_H + GAP);
            boolean on = Theme.accentIndex() == i;
            boolean hov = Ui.in(mx, my, bx, by, ACC_W, ACC_H);
            int color = Theme.ACCENTS[i];
            Ui.box(g, bx, by, ACC_W, ACC_H, color, on ? Ui.TEXT : hov ? Ui.MUTED : Ui.mix(color, Ui.PANEL, 0.45f));
            if (on) Ui.surface(g, bx + 10, by + 7, 5, 5, Theme.onColor(color));
            final int idx = i;
            s.region(bx, by, ACC_W, ACC_H, () -> Theme.setAccent(idx));
        }
    }

    private int scrollTop() { return y + 80; }
    private int scrollBottom() { return y + h; }
    private int columns(int cellWidth) { return Math.max(1, (w + GAP) / (cellWidth + GAP)); }
    private int colorColumns() { return Math.min(Theme.Colorway.values().length, columns(70)); }
    private int accentOffset() { return 21 + Math.ceilDiv(Theme.Colorway.values().length, colorColumns()) * (COLORWAY_H + GAP); }
    private int menuOffset() { return accentOffset() + 22 + Math.ceilDiv(Theme.ACCENTS.length, columns(ACC_W)) * (ACC_H + GAP); }

    private double maxScroll() {
        int content = 2 + menuOffset() + MENU_H;
        return Math.max(0, content - (scrollBottom() - scrollTop()));
    }

    @Override
    public boolean click(MouseButtonEvent event) {
        return dropdown.open && dropdown.clickPopup(event);
    }

    @Override
    public boolean key(KeyEvent event) {
        if (!dropdown.open) return false;
        if (event.key() == GLFW.GLFW_KEY_TAB) {
            dropdown.open = false;
            return false;
        }
        return dropdown.keyPressed(event);
    }

    @Override
    public boolean scroll(double mx, double my, double dy) {
        if (dropdown.open) {
            if (Ui.in(mx, my, x, dropdown.popupY(), dropdown.getWidth(), dropdown.popupHeight()) && dy != 0) {
                dropdown.scroll(dy);
            }
            return true;
        }
        if (!Ui.in(mx, my, x, scrollTop(), w + 3, scrollBottom() - scrollTop())) return false;
        scroll = Math.clamp(scroll - dy * 28, 0, maxScroll());
        return true;
    }

    @Override
    public void filesDropped(List<Path> paths) {
        if (!paths.isEmpty()) MenuBackground.importImage(s.mc(), paths.getFirst());
    }

    @Override
    public void hidden() {
        if (dropdown != null) dropdown.open = false;
        saveOpacity();
    }

    private void saveOpacity() {
        if (!opacityDirty) return;
        Theme.save();
        opacityDirty = false;
    }

    private final class ThemeDropdown extends AbstractWidget {
        private static final int ROW_H = 25, PAD = 4;
        private final List<UiThemes.Preset> options = UiThemes.available();
        private boolean open;
        private int first, highlighted;

        ThemeDropdown(int x, int y, int w) {
            super(x, y, w, 22, Component.literal("Theme: " + UiThemes.current().name()));
        }

        private int popupY() { return getY() + getHeight() + 3; }
        private int rows() { return Math.clamp((scrollBottom() - popupY() - 2 * PAD) / ROW_H, 1, options.size()); }
        private int popupHeight() { return 2 * PAD + rows() * ROW_H; }
        private int maxFirst() { return Math.max(0, options.size() - rows()); }

        private void show() {
            highlighted = options.indexOf(UiThemes.current());
            first = 0;
            revealHighlight();
            open = true;
        }

        private void revealHighlight() {
            if (highlighted < first) first = highlighted;
            if (highlighted >= first + rows()) first = highlighted - rows() + 1;
            first = Math.clamp(first, 0, maxFirst());
        }

        private void scroll(double dy) {
            first = Math.clamp(first + (dy < 0 ? 1 : -1), 0, maxFirst());
            highlighted = Math.clamp(highlighted, first, first + rows() - 1);
        }

        private void choose(int index) {
            open = false;
            UiThemes.Preset option = options.get(index);
            if (option == UiThemes.current()) return;
            boolean focus = isFocused();
            UiThemes.select(option);
            s.rebuild();
            if (focus) s.setFocused(dropdown);
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) { show(); }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (!open) {
                if (!isFocused() || !(event.isSelection() || event.isDown())) return false;
                show();
            } else if (event.isEscape()) {
                open = false;
            } else if (event.isUp() || event.isDown()) {
                highlighted = Math.clamp(highlighted + (event.isDown() ? 1 : -1), 0, options.size() - 1);
                revealHighlight();
            } else if (event.isSelection()) {
                choose(highlighted);
            } else return false;
            return true;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) { defaultButtonNarrationText(narration); }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
            Font font = s.font();
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hot = isHoveredOrFocused();
            Ui.controlBox(g, x, y, w, h, hot ? Ui.TILE_HOVER : Ui.TILE, open || hot ? Ui.ACCENT : Ui.EDGE_LIGHT);
            int nameX = x + 8, ty = y + (h - 8) / 2;
            if (w >= 106) {
                Ui.text(g, font, "Theme", nameX, ty, Ui.MUTED);
                nameX += font.width("Theme") + 10;
            }
            Ui.text(g, font, Ui.trim(font, UiThemes.current().name(), x + w - 23 - nameX), nameX, ty, Ui.TEXT);
            Ui.chevron(g, x + w - 12, y + h / 2, open, open ? Ui.ACCENT : Ui.TEXT);
            handleCursor(g);
        }

        private int optionAt(double mx, double my) {
            int top = popupY() + PAD;
            if (!Ui.in(mx, my, getX() + PAD, top, getWidth() - 2 * PAD, rows() * ROW_H)) return -1;
            return first + (int) ((my - top) / ROW_H);
        }

        private boolean clickPopup(MouseButtonEvent event) {
            int option = optionAt(event.x(), event.y());
            if (option >= 0 && event.button() == 0) choose(option);
            else open = false;
            return true; // Don't activate anything behind the dropdown, including on an outside click.
        }

        private void renderPopup(GuiGraphicsExtractor g, int mx, int my) {
            if (!open) return;
            g.nextStratum();
            Font font = s.font();
            int x = getX(), y = popupY(), w = getWidth(), h = popupHeight();
            if (UiThemes.rounded()) Ui.panel(g, x - 1, y + 2, w + 2, h + 1, 0x34000000, 0x55000000, 0x24000000);
            Ui.panel(g, x, y, w, h, Ui.TILE, Ui.PANEL, Ui.EDGE_LIGHT);
            int hover = optionAt(mx, my);
            for (int i = 0; i < rows(); i++) {
                int index = first + i, ry = y + PAD + i * ROW_H;
                UiThemes.Preset option = options.get(index);
                boolean selected = option == UiThemes.current();
                if (index == (hover >= 0 ? hover : highlighted)) {
                    Ui.surface(g, x + PAD, ry, w - 2 * PAD - 2, ROW_H - 1, Ui.TILE_HOVER);
                }
                if (selected) Ui.surface(g, x + 8, ry + 5, 3, 14, Ui.ACCENT);
                Ui.text(g, font, Ui.trim(font, option.name(), w - 26), x + 16, ry + 3, selected ? Ui.ACCENT_HOVER : Ui.TEXT);
                Ui.text(g, font, Ui.trim(font, option.description(), w - 26), x + 16, ry + 13, Ui.MUTED);
            }
            Ui.scrollbar(g, x + w - 5, y + PAD, rows() * ROW_H, first * ROW_H, maxFirst() * ROW_H);
        }
    }

    /** Uses Minecraft's pointer capture and focus handling, with the same styling as the other Crit sliders. */
    private final class OpacitySlider extends AbstractSliderButton {
        private boolean dragging;

        OpacitySlider(int x, int y, int width) {
            super(x, y, width, 24, Component.empty(), Theme.panelOpacity() / 100.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() { setMessage(Component.literal("Panel opacity: " + Theme.panelOpacity() + "%")); }

        @Override
        protected void applyValue() {
            int percent = (int) Math.round(value * 100);
            if (percent == Theme.panelOpacity()) return;
            Theme.previewPanelOpacity(percent);
            opacityDirty = true;
            if (!dragging) saveOpacity();
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            dragging = true;
            super.onClick(event, doubleClick);
        }

        @Override
        public void onRelease(MouseButtonEvent event) {
            super.onRelease(event);
            dragging = false;
            saveOpacity();
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (canChangeValue && (event.isLeft() || event.isRight())) {
                setValue((Theme.panelOpacity() + (event.isRight() ? 1 : -1)) / 100.0);
                return true;
            }
            return super.keyPressed(event);
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
            int x = getX(), y = getY(), width = getWidth();
            Ui.text(g, s.font(), "Panel opacity", x, y, Ui.TEXT);
            Ui.right(g, s.font(), Theme.panelOpacity() + "%", x + width, y, Ui.TEXT);
            Ui.slider(g, x, y + 14, width, HANDLE_WIDTH, value, isHoveredOrFocused());
            handleCursor(g);
        }
    }
}
