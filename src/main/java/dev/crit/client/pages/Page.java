package dev.crit.client.pages;

import dev.crit.client.CritScreen;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

/** One tab of the Crit Client popup. Lives as long as the popup does, so its state survives rebuilds. */
public abstract class Page {
    protected final CritScreen s;
    protected int x, y, w, h;

    protected Page(CritScreen s) {
        this.s = s;
    }

    public void layout(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    /** Add text fields here (after layout). Called on open, tab change and window resize. */
    public void init() {}

    public abstract void render(GuiGraphicsExtractor g, int mx, int my);

    /** Overlay controls can consume clicks before the widgets or page regions underneath them. */
    public boolean click(MouseButtonEvent event) { return false; }

    public boolean scroll(double mx, double my, double dy) { return false; }

    /** Mouse dragged while a button is held. Return true if the page handled it. */
    public boolean drag(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) { return false; }

    /** Mouse button released. Return true if the page handled it. */
    public boolean release(net.minecraft.client.input.MouseButtonEvent event) { return false; }

    /** A key was pressed. Used by pages that capture a keybind. Return true if the page consumed it. */
    public boolean key(net.minecraft.client.input.KeyEvent event) { return false; }

    public void filesDropped(List<Path> paths) {}

    /** The tab was switched to. */
    public void shown() {}

    /** The tab was switched away from, or the popup closed. */
    public void hidden() {}
}
