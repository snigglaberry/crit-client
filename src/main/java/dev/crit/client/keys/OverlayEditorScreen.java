package dev.crit.client.keys;

import dev.crit.client.ui.Theme;
import dev.crit.client.ui.Ui;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Full-screen editor for the HUD overlays (they have to be moved over the real screen, so this one can't live inside the popup).
 * It edits one group at a time: the Key Overlay (WASD, space, mouse) or the Overlays readouts (CPS, ping, FPS, ...).
 * Right-click and drag an overlay to move it, hold right-click on it and scroll to resize it, Esc / Done saves.
 */
public class OverlayEditorScreen extends Screen {
    /** Which module's overlays this editor shows. */
    public enum Group { KEYS, HUD, ARMOR }

    private record Row(String label, BooleanSupplier on, java.util.function.Supplier<String> value, Runnable flip) {
        Row(String label, BooleanSupplier on, Runnable flip) { this(label, on, null, flip); }
    }

    private static final int PX = 8, PY = 52, PW = 158, ROWH = 22;

    private final Screen parent;
    private final Group group;
    private final List<Row> rows = new ArrayList<>();
    private final KeyConfig.Overlay[] overlays;
    private KeyConfig.Overlay dragging;
    private double offX, offY;

    public OverlayEditorScreen(Screen parent) {
        this(parent, Group.KEYS);
    }

    public OverlayEditorScreen(Screen parent, Group group) {
        super(Component.literal(group == Group.KEYS ? "Key Overlay" : group == Group.ARMOR ? "Armour HUD" : "Overlays"));
        this.parent = parent;
        this.group = group;
        if (group == Group.KEYS) {
            overlays = KeyConfig.keys();
            rows.add(new Row("WASD", () -> KeyConfig.wasd.enabled, () -> KeyConfig.wasd.enabled = !KeyConfig.wasd.enabled));
            rows.add(new Row("Space bar", () -> KeyConfig.showSpace, () -> KeyConfig.showSpace = !KeyConfig.showSpace));
            rows.add(new Row("Mouse", () -> KeyConfig.mouse.enabled, () -> KeyConfig.mouse.enabled = !KeyConfig.mouse.enabled));
        } else if (group == Group.ARMOR) {
            overlays = new KeyConfig.Overlay[]{KeyConfig.armor};
            rows.add(new Row("Layout", () -> true, () -> KeyConfig.armorHorizontal ? "Sideways" : "Stacked",
                    () -> KeyConfig.armorHorizontal = !KeyConfig.armorHorizontal));
            rows.add(new Row("Durability", () -> true, () -> new String[]{"Bar", "Number", "Off"}[KeyConfig.armorDurability],
                    () -> KeyConfig.armorDurability = (KeyConfig.armorDurability + 1) % 3));
        } else {
            overlays = KeyConfig.hud();
            rows.add(new Row("CPS", () -> KeyConfig.cps.enabled, () -> KeyConfig.cps.enabled = !KeyConfig.cps.enabled));
            rows.add(new Row("Sprint indicator", () -> KeyConfig.status.enabled, () -> KeyConfig.status.enabled = !KeyConfig.status.enabled));
            rows.add(new Row("Ping", () -> KeyConfig.ping.enabled, () -> KeyConfig.ping.enabled = !KeyConfig.ping.enabled));
            rows.add(new Row("FPS", () -> KeyConfig.fps.enabled, () -> KeyConfig.fps.enabled = !KeyConfig.fps.enabled));
            rows.add(new Row("Server TPS", () -> KeyConfig.tps.enabled, () -> KeyConfig.tps.enabled = !KeyConfig.tps.enabled));
            rows.add(new Row("Speedometer", () -> KeyConfig.speed.enabled, () -> KeyConfig.speed.enabled = !KeyConfig.speed.enabled));
            rows.add(new Row("Coordinates", () -> KeyConfig.coords.enabled, () -> KeyConfig.coords.enabled = !KeyConfig.coords.enabled));
            rows.add(new Row("Compass", () -> KeyConfig.compass.enabled, () -> KeyConfig.compass.enabled = !KeyConfig.compass.enabled));
        }
    }

    private static net.minecraft.world.item.ItemStack[] sample;

    /** A made-up set of armour (worn to different degrees) for previewing the armour HUD. */
    private static net.minecraft.world.item.ItemStack[] sampleArmor() {
        if (sample == null) {
            var items = new net.minecraft.world.item.Item[]{net.minecraft.world.item.Items.NETHERITE_HELMET,
                    net.minecraft.world.item.Items.DIAMOND_CHESTPLATE, net.minecraft.world.item.Items.IRON_LEGGINGS,
                    net.minecraft.world.item.Items.GOLDEN_BOOTS};
            int[] worn = {40, 200, 150, 60};
            sample = new net.minecraft.world.item.ItemStack[4];
            for (int i = 0; i < 4; i++) {
                sample[i] = new net.minecraft.world.item.ItemStack(items[i]);
                sample[i].setDamageValue(worn[i]);
            }
        }
        return sample;
    }

    private void clampAll() {
        for (KeyConfig.Overlay o : overlays) o.clamp(width, height);
    }

    @Override
    protected void init() {
        clampAll();
    }

    private void shadowCenter(GuiGraphicsExtractor g, String text, int y, int color) {
        Font font = Ui.font(this.font);
        String label = Ui.trim(font, text, width - 12);
        g.text(font, label, width / 2 - font.width(label) / 2, y, color, true);
    }

    private int ox(KeyConfig.Overlay o) { return Math.round(o.x * width); }
    private int oy(KeyConfig.Overlay o) { return Math.round(o.y * height); }

    private boolean over(KeyConfig.Overlay o, double mx, double my) {
        return o.enabled && Ui.in(mx, my, ox(o), oy(o), o.w(), o.h());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float pt) {
        super.extractRenderState(g, mx, my, pt);
        g.fill(0, 0, width, height, 0x80040605);

        shadowCenter(g, getTitle().getString(), 14, Ui.TEXT);
        shadowCenter(g, "Right-click and drag to move  |  hold right-click and scroll to resize  |  Esc saves", 28, 0xFFC8C8C8);

        for (int i = overlays.length - 1; i >= 0; i--) drawEditable(g, overlays[i], mx, my);

        panel(g, mx, my);
        btn(g, width / 2 - 86, height - 28, 80, 18, "Reset", mx, my, false);
        btn(g, width / 2 + 6, height - 28, 80, 18, "Done", mx, my, true);
    }

    private void panel(GuiGraphicsExtractor g, int mx, int my) {
        Font font = Ui.font(this.font);
        Ui.panel(g, PX, PY, PW, 20 + rows.size() * ROWH + 6, Ui.TILE, Ui.PANEL, Ui.EDGE_LIGHT);
        Ui.text(g, font, group == Group.KEYS ? "Keys" : group == Group.ARMOR ? "Armour" : "Readouts", PX + 8, PY + 7, Ui.MUTED);
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            boolean on = r.on().getAsBoolean();
            int ry = PY + 20 + i * ROWH;
            Ui.text(g, font, r.label(), PX + 8, ry + 7, Ui.TEXT);
            if (r.value() != null) {
                int bx = PX + PW - 66, by = ry + 3;
                boolean vh = Ui.in(mx, my, bx, by, 58, 16);
                Ui.box(g, bx, by, 58, 16, vh ? Ui.TILE_HOVER : Ui.TILE, vh ? Ui.ACCENT_DARK : Ui.EDGE_LIGHT);
                Ui.center(g, font, r.value().get(), bx + 29, by + 4, Ui.TEXT);
                continue;
            }
            int tx = PX + PW - 34, ty = ry + 5;
            boolean hov = Ui.in(mx, my, tx, ty, 26, 12);
            Ui.switchControl(g, tx, ty, on, hov);
        }
    }

    private void btn(GuiGraphicsExtractor g, int x, int y, int w, int h, String label, int mx, int my, boolean primary) {
        boolean hov = Ui.in(mx, my, x, y, w, h);
        int fill = primary ? (hov ? Ui.ACCENT_HOVER : Ui.ACCENT) : (hov ? Ui.TILE_HOVER : Ui.TILE);
        Ui.box(g, x, y, w, h, fill, primary ? fill : (hov ? Ui.ACCENT_DARK : Ui.EDGE_LIGHT));
        Ui.center(g, Ui.font(font), label, x + w / 2, y + (h - 8) / 2, primary ? Theme.onColor(fill) : Ui.TEXT);
    }

    private void drawEditable(GuiGraphicsExtractor g, KeyConfig.Overlay o, int mx, int my) {
        if (!o.enabled) return;
        int x = ox(o), y = oy(o);
        boolean hot = dragging == o || (dragging == null && over(o, mx, my));
        g.outline(x - 4, y - 4, o.w() + 8, o.h() + 8, hot ? Ui.ACCENT : Ui.ACCENT_DARK);
        switch (o.kind) {
            case WASD -> OverlayRenderer.drawWasd(g, x, y, o.scale, false, false, false, false, KeyConfig.showSpace, false);
            case MOUSE -> OverlayRenderer.drawMouse(g, x, y, o.scale, false, false, false, false, false);
            case CPS -> OverlayRenderer.drawCps(g, x, y, o.scale, 0);
            case STATUS -> OverlayRenderer.drawStatus(g, x, y, o.scale, "SPRINTING");
            case PING -> OverlayRenderer.drawPing(g, x, y, o.scale, 42);
            case FPS -> OverlayRenderer.drawFps(g, x, y, o.scale, 144);
            case TPS -> OverlayRenderer.drawTps(g, x, y, o.scale, 20);
            case SPEED -> OverlayRenderer.drawSpeed(g, x, y, o.scale, 4.3);
            case COORDS -> OverlayRenderer.drawCoords(g, x, y, o.scale, 128, 64, -256);
            case COMPASS -> OverlayRenderer.drawCompass(g, x, y, o.scale, 204f);
            case ARMOR -> OverlayRenderer.drawArmor(g, x, y, o.scale, sampleArmor());
        }
        Ui.text(g, Ui.font(font), "Size " + Math.round(o.scale * 100) + "%", x, y + o.h() + 8, Ui.MUTED);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean dbl) {
        double mx = event.x(), my = event.y();
        if (event.button() == 0) {
            for (int i = 0; i < rows.size(); i++) {
                if (Ui.in(mx, my, PX, PY + 20 + i * ROWH, PW, ROWH)) {
                    rows.get(i).flip().run();
                    clampAll();
                    KeyConfig.save();
                    return true;
                }
            }
            if (Ui.in(mx, my, width / 2 + 6, height - 28, 80, 18)) { onClose(); return true; }
            if (Ui.in(mx, my, width / 2 - 86, height - 28, 80, 18)) {
                for (KeyConfig.Overlay o : overlays) o.reset();
                clampAll();
                return true;
            }
        }
        if (event.button() == 1) {
            for (KeyConfig.Overlay o : overlays) {
                if (over(o, mx, my)) {
                    dragging = o;
                    offX = mx - ox(o);
                    offY = my - oy(o);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, dbl);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dragging != null && event.button() == 1) {
            dragging.x = (float) ((event.x() - offX) / width);
            dragging.y = (float) ((event.y() - offY) / height);
            dragging.clamp(width, height);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 1 && dragging != null) {
            dragging = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (dragging != null) {
            dragging.scale = (float) Math.max(KeyConfig.MIN_SCALE, Math.min(KeyConfig.MAX_SCALE, dragging.scale + sy * 0.1));
            dragging.clamp(width, height);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public void onClose() {
        KeyConfig.save();
        minecraft.setScreenAndShow(parent);
    }
}
