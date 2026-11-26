package dev.crit.client.modules;

import com.google.gson.JsonObject;
import dev.crit.client.CritClient;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;

/**
 * Scoreboard Tweaks: how many lines the sidebar scoreboard shows, how big it is, where it sits and how dark its
 * background is. Size and position wrap the game's own scoreboard element (see {@link #wrap}); the line limit and the
 * background are small hooks in ScoreboardMixin. Every default is the vanilla value, so switching the module on
 * changes nothing until a setting is moved.
 */
public final class ScoreboardModule extends Module {
    public static final int VANILLA_LINES = 15, MAX_LINES = 30;

    private int maxLines = VANILLA_LINES;   // 1 .. 30
    private double scale = 1.0;             // 0.5 .. 2.0
    private int offsetX = 0;                // GUI pixels, -200 .. 200 (positive = right)
    private int offsetY = 0;                // GUI pixels, -200 .. 200 (positive = down)
    private double background = 1.0;        // 0 .. 2, times the vanilla background darkness

    public ScoreboardModule() {
        super("scoreboard", "Scoreboard Tweaks", "Changes how many lines the sidebar scoreboard shows, how big it is, where it sits and how dark its background is.", false);
    }

    public int maxLines() { return maxLines; }
    public void maxLines(int v) { this.maxLines = Math.max(1, Math.min(MAX_LINES, v)); Modules.save(); }

    public double scale() { return scale; }
    public void scale(double v) { this.scale = Math.max(0.5, Math.min(2.0, v)); Modules.save(); }

    public int offsetX() { return offsetX; }
    public void offsetX(int v) { this.offsetX = Math.max(-200, Math.min(200, v)); Modules.save(); }

    public int offsetY() { return offsetY; }
    public void offsetY(int v) { this.offsetY = Math.max(-200, Math.min(200, v)); Modules.save(); }

    public double background() { return background; }
    public void background(double v) { this.background = Math.max(0.0, Math.min(2.0, v)); Modules.save(); }

    /** Puts every setting back to what the game does on its own. */
    public void reset() {
        maxLines = VANILLA_LINES;
        scale = 1.0;
        offsetX = 0;
        offsetY = 0;
        background = 1.0;
        Modules.save();
    }

    /**
     * Wraps the game's scoreboard HUD element: when the module is on, the scoreboard is drawn scaled and moved. It is
     * scaled around the spot where the game anchors it (right edge, middle of the screen), so it grows and shrinks in
     * place. If the element can't be wrapped for any reason, the game's own element is returned untouched.
     */
    public static HudElement wrap(HudElement vanilla) {
        Method render = null;
        for (Method m : HudElement.class.getMethods()) {
            if (Modifier.isAbstract(m.getModifiers()) && m.getParameterCount() == 2) render = m;
        }
        if (render == null) {
            CritClient.LOG.warn("Scoreboard Tweaks: couldn't find the HUD element's draw method, size and position are off");
            return vanilla;
        }
        final Method draw = render;
        return (g, delta) -> {
            ScoreboardModule m = Modules.SCOREBOARD;
            boolean moved = m.enabled() && (m.scale != 1.0 || m.offsetX != 0 || m.offsetY != 0);
            if (moved) {
                float ax = g.guiWidth(), ay = g.guiHeight() / 2f;
                g.pose().pushMatrix();
                g.pose().translate(ax + m.offsetX, ay + m.offsetY);
                g.pose().scale((float) m.scale, (float) m.scale);
                g.pose().translate(-ax, -ay);
            }
            try {
                draw.invoke(vanilla, g, delta);
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException re) throw re;
                throw new RuntimeException(e.getCause());
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } finally {
                if (moved) g.pose().popMatrix();
            }
        };
    }

    @Override
    void read(JsonObject o) {
        super.read(o);
        if (o == null) return;
        if (o.has("maxLines")) maxLines = Math.max(1, Math.min(MAX_LINES, o.get("maxLines").getAsInt()));
        if (o.has("scale")) scale = Math.max(0.5, Math.min(2.0, o.get("scale").getAsDouble()));
        if (o.has("offsetX")) offsetX = Math.max(-200, Math.min(200, o.get("offsetX").getAsInt()));
        if (o.has("offsetY")) offsetY = Math.max(-200, Math.min(200, o.get("offsetY").getAsInt()));
        if (o.has("background")) background = Math.max(0.0, Math.min(2.0, o.get("background").getAsDouble()));
    }

    @Override
    JsonObject write() {
        JsonObject o = super.write();
        o.addProperty("maxLines", maxLines);
        o.addProperty("scale", scale);
        o.addProperty("offsetX", offsetX);
        o.addProperty("offsetY", offsetY);
        o.addProperty("background", background);
        return o;
    }
}
