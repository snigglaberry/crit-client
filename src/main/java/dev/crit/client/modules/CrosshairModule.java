package dev.crit.client.modules;

import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.AttackRange;

/**
 * Re-skins the crosshair. There are two looks: the idle one, and the "on target" one used while you
 * look at a living entity inside reach. Each has its own texture and colour. Inversion switches to
 * Minecraft's see-through crosshair blend (it inverts whatever is behind it) and disables colour.
 */
public final class CrosshairModule extends Module {
    // Built-in looks. New ones are added at the end so saved indexes keep pointing at the same texture.
    private static final String[] BUILTIN_IDS = {"box", "dot", "pvp", "x", "default", "logo"};
    private static final String[] BUILTIN_NAMES = {"Box", "Dot", "PVP", "X", "Default", "Crit"};
    public static final int BUILTIN = BUILTIN_IDS.length;
    /** Custom crosshairs must be exactly this many pixels wide and tall. */
    public static final int CUSTOM_SIZE = 16;
    /** The size ("WxH") of the last picture addCustom turned down for being the wrong size. */
    public static String lastRejectedSize = "";

    private static final Identifier FULL = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_full");
    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_background");
    private static final Identifier PROGRESS = Identifier.withDefaultNamespace("hud/crosshair_attack_indicator_progress");

    private record Tex(int w, int h, int[] px) {}

    private static final java.util.List<Tex> TEX = new java.util.ArrayList<>();
    private static final java.util.List<String> NAMES = new java.util.ArrayList<>();
    /** File names (inside the crosshairs folder) of the custom crosshairs, in the order they were added. */
    private static final java.util.List<String> CUSTOM_FILES = new java.util.ArrayList<>();

    static {
        for (int i = 0; i < BUILTIN; i++) {
            TEX.add(load(BUILTIN_IDS[i]));
            NAMES.add(BUILTIN_NAMES[i]);
        }
    }

    private static java.nio.file.Path customDir() { return dev.crit.client.CritClient.DIR.resolve("crosshairs"); }

    public static int count() { return TEX.size(); }

    /** The picture of crosshair number `index` as {width, height, then width*height ARGB pixels}, or null. */
    public static int[] pixelData(int index) {
        if (index < 0 || index >= TEX.size() || TEX.get(index) == null) return null;
        Tex t = TEX.get(index);
        int[] out = new int[2 + t.px().length];
        out[0] = t.w();
        out[1] = t.h();
        System.arraycopy(t.px(), 0, out, 2, t.px().length);
        return out;
    }

    /** Index of the built-in "X" crosshair. */
    public static final int X_CROSSHAIR = 3;
    public static String name(int i) { return NAMES.get(wrap(i)); }

    /**
     * Adds a picture as a new crosshair: it is copied into Crit Client's crosshairs folder and appears next to the
     * built-in looks. Only 16x16 pictures are accepted.
     *
     * @return its index, -1 if the file isn't an image, or -2 if it isn't exactly 16x16 (see lastRejectedSize)
     */
    public static int addCustom(java.nio.file.Path file) {
        try {
            BufferedImage img = ImageIO.read(file.toFile());
            if (img == null) return -1;
            int w = img.getWidth(), h = img.getHeight();
            if (w != CUSTOM_SIZE || h != CUSTOM_SIZE) {
                lastRejectedSize = w + "x" + h;
                return -2;
            }
            String base = file.getFileName().toString();
            int dot = base.lastIndexOf('.');
            if (dot > 0) base = base.substring(0, dot);
            base = base.replaceAll("[^A-Za-z0-9_-]", "_");
            if (base.isEmpty()) base = "crosshair";
            String fileName = base + ".png";
            for (int n = 2; CUSTOM_FILES.contains(fileName); n++) fileName = base + "_" + n + ".png";
            java.nio.file.Files.createDirectories(customDir());
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            out.getGraphics().drawImage(img, 0, 0, null);
            ImageIO.write(out, "png", customDir().resolve(fileName).toFile());
            TEX.add(new Tex(w, h, out.getRGB(0, 0, w, h, null, 0, w)));
            NAMES.add(base);
            CUSTOM_FILES.add(fileName);
            Modules.save();
            return TEX.size() - 1;
        } catch (Exception e) {
            return -1;
        }
    }

    /** True for a crosshair the player added (the built-in looks can't be deleted). */
    public static boolean isCustom(int index) {
        return index >= BUILTIN && index < TEX.size();
    }

    /**
     * Deletes a crosshair the player added, including its file. Built-in crosshairs are never deleted.
     * Anything that was using it falls back to its default look, and the crosshairs after it shift down by one.
     *
     * @return true if one was deleted
     */
    public boolean removeCustom(int index) {
        if (!isCustom(index)) return false;
        int c = index - BUILTIN;
        String fileName = CUSTOM_FILES.get(c);
        TEX.remove(index);
        NAMES.remove(index);
        CUSTOM_FILES.remove(c);
        try {
            java.nio.file.Files.deleteIfExists(customDir().resolve(fileName));
        } catch (Exception e) {
            dev.crit.client.CritClient.LOG.warn("Couldn't delete crosshair file {}", fileName, e);
        }
        idleTex = idleTex == index ? 0 : idleTex > index ? idleTex - 1 : idleTex;
        targetTex = targetTex == index ? 2 : targetTex > index ? targetTex - 1 : targetTex;
        Modules.save();
        return true;
    }

    private static void loadCustom(String fileName) {
        try {
            BufferedImage img = ImageIO.read(customDir().resolve(fileName).toFile());
            if (img == null) return;
            int w = img.getWidth(), h = img.getHeight();
            TEX.add(new Tex(w, h, img.getRGB(0, 0, w, h, null, 0, w)));
            String n = fileName.endsWith(".png") ? fileName.substring(0, fileName.length() - 4) : fileName;
            NAMES.add(n);
            CUSTOM_FILES.add(fileName);
        } catch (Exception ignored) {
            // a custom file that was deleted just disappears from the list
        }
    }

    private int idleTex = 0;                 // Box
    private int targetTex = 2;               // PVP
    private int idleColor = 0xFFFFFFFF;
    private int targetColor = 0xFF5DBB3A;
    private boolean invert;                  // Minecraft's see-through crosshair
    private boolean attackIndicator = true;

    public CrosshairModule() {
        super("crosshair", "Crosshair Tweaks",
                "Changes the crosshair's texture and colour when an entity is in reach, with an optional hit indicator.", false);
    }


    // ------------------------------------------------------------------ settings

    public int idleTex() { return idleTex; }
    public void idleTex(int i) { this.idleTex = wrap(i); Modules.save(); }

    public int targetTex() { return targetTex; }
    public void targetTex(int i) { this.targetTex = wrap(i); Modules.save(); }

    public int idleColor() { return idleColor; }
    public void idleColor(int c) { this.idleColor = c | 0xFF000000; Modules.save(); }

    public int targetColor() { return targetColor; }
    public void targetColor(int c) { this.targetColor = c | 0xFF000000; Modules.save(); }

    public int texFor(boolean target) { return target ? targetTex : idleTex; }
    public int colorFor(boolean target) { return target ? targetColor : idleColor; }

    public void texFor(boolean target, int i) {
        if (target) targetTex(i);
        else idleTex(i);
    }

    public void colorFor(boolean target, int c) {
        if (target) targetColor(c);
        else idleColor(c);
    }

    public boolean invert() { return invert; }
    public void invert(boolean b) { this.invert = b; Modules.save(); }

    public boolean attackIndicator() { return attackIndicator; }
    public void attackIndicator(boolean b) { this.attackIndicator = b; Modules.save(); }

    private static int wrap(int i) {
        int n = TEX.size();
        return ((i % n) + n) % n;
    }

    // ------------------------------------------------------------------ runtime

    /** True while the player is looking at a living entity inside attack reach. */
    public boolean targeting(Minecraft mc) {
        if (mc.player == null || mc.hitResult == null) return false;
        Entity e = mc.crosshairPickEntity;
        if (!(e instanceof LivingEntity le) || !le.isAlive()) return false;
        AttackRange range = mc.player.getActiveItem().get(DataComponents.ATTACK_RANGE);
        return range == null || range.isInRange(le, mc.hitResult.getLocation());
    }

    /** Draws the crosshair (and the optional attack indicator) for the current frame. */
    public void render(GuiGraphicsExtractor g, Minecraft mc) {
        boolean target = targeting(mc);
        int texIndex = texFor(target);
        int color = invert ? 0xFFFFFFFF : colorFor(target);
        drawCentred(g, texIndex, color, invert);
        if (attackIndicator) drawAttackIndicator(g, mc, target);
    }

    private void drawCentred(GuiGraphicsExtractor g, int texIndex, int color, boolean invert) {
        Tex tex = TEX.get(wrap(texIndex));
        if (tex == null) return;
        draw(g, tex, color, invert, g.guiWidth() / 2 - tex.w() / 2, g.guiHeight() / 2 - tex.h() / 2, 1);
    }

    /** Draws one of the crosshair textures at an arbitrary spot, used by the settings previews. */
    public static void draw(GuiGraphicsExtractor g, int texIndex, int color, boolean invert, int x, int y, int scale) {
        draw(g, TEX.get(wrap(texIndex)), color, invert, x, y, Math.max(1, scale));
    }

    public static int texW(int texIndex) {
        Tex t = TEX.get(wrap(texIndex));
        return t == null ? 0 : t.w();
    }

    public static int texH(int texIndex) {
        Tex t = TEX.get(wrap(texIndex));
        return t == null ? 0 : t.h();
    }

    private static void draw(GuiGraphicsExtractor g, Tex tex, int color, boolean invert, int ox, int oy, int scale) {
        if (tex == null) return;
        int ta = (color >>> 24) & 0xFF;
        int tr = (color >> 16) & 0xFF, tg = (color >> 8) & 0xFF, tb = color & 0xFF;
        for (int y = 0; y < tex.h(); y++) {
            for (int x = 0; x < tex.w(); x++) {
                int p = tex.px()[y * tex.w() + x];
                int a = (p >>> 24) & 0xFF;
                if (a == 0) continue;
                int x0 = ox + x * scale, y0 = oy + y * scale;
                if (invert) {
                    // Minecraft's crosshair blend: inverts whatever is behind it.
                    g.fill(RenderPipelines.GUI_INVERT, x0, y0, x0 + scale, y0 + scale, 0xFFFFFFFF);
                } else {
                    int r = ((p >> 16) & 0xFF) * tr / 255;
                    int gg = ((p >> 8) & 0xFF) * tg / 255;
                    int b = (p & 0xFF) * tb / 255;
                    int alpha = a * ta / 255;
                    g.fill(x0, y0, x0 + scale, y0 + scale, (alpha << 24) | (r << 16) | (gg << 8) | b);
                }
            }
        }
    }

    /** Vanilla's crosshair attack indicator, drawn the same way the game draws it. */
    private void drawAttackIndicator(GuiGraphicsExtractor g, Minecraft mc, boolean target) {
        if (mc.player == null) return;
        float charge = mc.player.getAttackStrengthScale(0f);
        boolean full = target && charge >= 1f && mc.player.getCurrentItemAttackStrengthDelay() > 5f;
        int x = g.guiWidth() / 2 - 8;
        int y = g.guiHeight() / 2 - 7 + 16;
        if (full) {
            g.blitSprite(RenderPipelines.CROSSHAIR, FULL, x, y, 16, 16);
        } else if (charge < 1f) {
            int progress = (int) (charge * 17f);
            g.blitSprite(RenderPipelines.CROSSHAIR, BACKGROUND, x, y, 16, 4);
            g.blitSprite(RenderPipelines.CROSSHAIR, PROGRESS, 16, 4, 0, 0, x, y, progress, 4);
        }
    }

    // ------------------------------------------------------------------ assets

    private static Tex load(String name) {
        try (InputStream in = CrosshairModule.class.getResourceAsStream("/assets/critclient/textures/crosshair/" + name + ".png")) {
            if (in == null) return null;
            BufferedImage img = ImageIO.read(in);
            if (img == null) return null;
            int w = img.getWidth(), h = img.getHeight();
            return new Tex(w, h, img.getRGB(0, 0, w, h, null, 0, w));
        } catch (Exception e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ persistence

    @Override
    void read(JsonObject o) {
        super.read(o);
        if (o == null) return;
        if (o.has("customs") && CUSTOM_FILES.isEmpty()) {
            for (var el : o.getAsJsonArray("customs")) loadCustom(el.getAsString());
        }
        if (o.has("idleTex")) idleTex = wrap(o.get("idleTex").getAsInt());
        if (o.has("targetTex")) targetTex = wrap(o.get("targetTex").getAsInt());
        if (o.has("idleColor")) idleColor = o.get("idleColor").getAsInt() | 0xFF000000;
        if (o.has("targetColor")) targetColor = o.get("targetColor").getAsInt() | 0xFF000000;
        if (o.has("invert")) invert = o.get("invert").getAsBoolean();
        if (o.has("attackIndicator")) attackIndicator = o.get("attackIndicator").getAsBoolean();
    }

    @Override
    JsonObject write() {
        JsonObject o = super.write();
        com.google.gson.JsonArray customs = new com.google.gson.JsonArray();
        for (String f : CUSTOM_FILES) customs.add(f);
        o.add("customs", customs);
        o.addProperty("idleTex", idleTex);
        o.addProperty("targetTex", targetTex);
        o.addProperty("idleColor", idleColor);
        o.addProperty("targetColor", targetColor);
        o.addProperty("invert", invert);
        o.addProperty("attackIndicator", attackIndicator);
        return o;
    }
}
