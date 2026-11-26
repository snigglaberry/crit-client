package dev.crit.client.title;

import dev.crit.client.CritClient;
import dev.crit.client.accounts.Sessions;
import dev.crit.client.accounts.Store;
import dev.crit.client.skins.AuthState;
import dev.crit.client.skins.OfflineSkin;
import dev.crit.client.skins.SkinApi;
import dev.crit.client.skins.SkinLibrary;
import dev.crit.client.skins.SkinOverride;
import dev.crit.client.ui.Ui;
import dev.crit.client.ui.UiThemes;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;

/**
 * Everything Crit Client adds to the main menu apart from the buttons themselves (TitleScreenMixin adds those so they
 * behave like the game's own): the Crit Client logo, the "Recent" cards on the left and
 * the walking 3D player on the right. There are no panels behind anything; the pieces sit straight on the panorama.
 */
public final class TitleExtras {
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("critclient", "textures/gui/title_logo.png");
    private static final int LOGO_W = 768, LOGO_H = 395;

    private static final Identifier CRIT_ICON = Identifier.fromNamespaceAndPath("critclient", "textures/gui/crit_icon.png");

    /** x, y, width, height of the Crit Client button and of the small quit button, set when the title screen is built. */
    public static int[] critRect, quitRect;

    /** The title screen's height while it is drawing itself, otherwise 0 (lets VersionTextMixin know it is on the main menu). */
    public static int drawingTitleHeight;

    /** Whether the account dropdown under the player is open. */
    public static boolean accountsOpen;

    private static long lastRetry;

    /** Where everything goes, for a screen of this size (GUI pixels). */
    public record Layout(boolean show, int lx, int rx, int pw, int top,
                         int headY, int cap1Y, int btn1Y, int cap2Y, int btn2Y, int btnH,
                         int dollY, int dollH, int acctY,
                         int logoX, int logoY, int logoW, int logoH) {}

    public static Layout layout(int w, int h) {
        int top = h / 4 + 48;                  // where the game's first button starts
        int half = w / 2;
        int side = half - 100;                 // room on each side of the 200 px button column
        int pw = Math.min(136, side - 16);
        boolean show = pw >= 96;
        int lx = (side - pw) / 2;
        int rx = half + 100 + (side - pw) / 2;

        int btnH = 40;
        int cap1 = top + 16, btn1 = cap1 + 11, cap2 = btn1 + btnH + 8, btn2 = cap2 + 11;

        int rightTop = Math.max(18, top - 44);                       // the player sits higher than the buttons
        int dollH = Math.max(72, Math.min(160, h - rightTop - 58));
        int acct = rightTop + dollH + 6;

        int lh = Math.max(24, Math.min(88, top - 24));
        int lw = lh * LOGO_W / LOGO_H;
        int maxW = Math.min(240, w - 20);
        if (lw > maxW) {
            lw = maxW;
            lh = lw * LOGO_H / LOGO_W;
        }
        return new Layout(show, lx, rx, pw, top, top, cap1, btn1, cap2, btn2, btnH, rightTop, dollH, acct,
                (w - lw) / 2, top - 12 - lh, lw, lh);
    }

    // ------------------------------------------------------------------ account dropdown

    /** One entry of the account dropdown. */
    public record Row(String label, boolean current, Runnable action) {}

    /** The launcher account followed by every saved account; the actions switch to that account (they don't close the list). */
    public static List<Row> accountRows() {
        Store st = Store.get();
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Launcher account", Sessions.activeAccountId() == null, () -> {
            try {
                Sessions.restoreOriginal();
                st.activeAccount = null;
                st.save();
            } catch (Exception e) {
                CritClient.LOG.warn("Couldn't switch back to the launcher account", e);
            }
        }));
        for (Store.Account a : new ArrayList<>(st.accounts)) {
            rows.add(new Row(a.name, a.id.equals(Sessions.activeAccountId()), () -> {
                try {
                    Sessions.use(a);
                    st.activeAccount = a.id;
                    st.save();
                } catch (Exception e) {
                    CritClient.LOG.warn("Couldn't switch to {}", a.name, e);
                }
            }));
        }
        return rows;
    }

    public static final int ROW_H = 18;

    /** How many account rows fit under the account button (one more row, "Manage accounts", always follows them). */
    public static int shownRows(Layout l, int screenH, int total) {
        int fit = Math.max(2, (screenH - (l.acctY() + 24) - 6) / ROW_H);
        return Math.min(total, fit - 1);
    }

    // ------------------------------------------------------------------ drawing

    /** Draws the logo, the "Recent" cards and the player; called by TitleScreenMixin after the screen has drawn. */
    public static void draw(GuiGraphicsExtractor g, int width, int height, int mx, int my) {
        Layout l = layout(width, height);
        Minecraft mc = Minecraft.getInstance();
        Font font = Ui.font(mc.font);

        g.blit(RenderPipelines.GUI_TEXTURED, LOGO, l.logoX(), l.logoY(), 0, 0, l.logoW(), l.logoH(), LOGO_W, LOGO_H, LOGO_W, LOGO_H);
        drawCritButton(g, font, mx, my);
        drawQuit(g, mx, my);
        if (!l.show()) return;

        // ---- left: Recent
        Ui.text(g, font, "Recent", l.lx(), l.headY(), Ui.ACCENT);
        g.fill(l.lx(), l.headY() + 11, l.lx() + l.pw(), l.headY() + 12, Ui.ACCENT_DARK);

        Ui.text(g, font, "Multiplayer", l.lx() + 1, l.cap1Y(), Ui.MUTED);
        boolean hasServer = QuickPlay.hasServer();
        card(g, font, l.lx(), l.btn1Y(), l.pw(), l.btnH(), QuickPlay.serverIcon(mc),
                hasServer ? QuickPlay.serverName() : "No server yet",
                hasServer ? QuickPlay.when(QuickPlay.serverTime()) : "Join one to see it here",
                hasServer, hasServer && Ui.in(mx, my, l.lx(), l.btn1Y(), l.pw(), l.btnH()));

        Ui.text(g, font, "Singleplayer", l.lx() + 1, l.cap2Y(), Ui.MUTED);
        boolean hasWorld = QuickPlay.hasWorld();
        card(g, font, l.lx(), l.btn2Y(), l.pw(), l.btnH(), QuickPlay.worldIcon(mc),
                hasWorld ? QuickPlay.worldName() : "No world yet",
                hasWorld ? QuickPlay.when(QuickPlay.worldTime()) : "Open one to see it here",
                hasWorld, hasWorld && Ui.in(mx, my, l.lx(), l.btn2Y(), l.pw(), l.btnH()));

        // ---- right: the player, then the account button and its dropdown
        drawDoll(g, mc, font, l, mx, my);
        drawAccount(g, font, l, height, mx, my);
    }

    /** The wide Crit Client button: the crosshair logo in the left corner, then the name. */
    private static void drawCritButton(GuiGraphicsExtractor g, Font font, int mx, int my) {
        int[] r = critRect;
        if (r == null) return;
        int x = r[0], y = r[1], w = r[2], h = r[3];
        boolean hover = Ui.in(mx, my, x, y, w, h);
        Ui.box(g, x, y, w, h, hover ? Ui.TILE_HOVER : Ui.TILE, hover ? Ui.ACCENT : Ui.ACCENT_DARK);
        Ui.indicator(g, x, y, h, hover ? Ui.ACCENT_HOVER : Ui.ACCENT);
        int icon = h - 6;
        g.blit(RenderPipelines.GUI_TEXTURED, CRIT_ICON, x + 6, y + 3, 0, 0, icon, icon, 64, 64, 64, 64);
        g.text(font, Ui.heading("Crit Client"), x + 6 + icon + 5, y + (h - 8) / 2, Ui.TEXT, false);
    }

    /** The small quit button in the top right corner: a red X. */
    private static void drawQuit(GuiGraphicsExtractor g, int mx, int my) {
        int[] r = quitRect;
        if (r == null) return;
        int x = r[0], y = r[1], w = r[2], h = r[3];
        boolean hover = Ui.in(mx, my, x, y, w, h);
        Ui.box(g, x, y, w, h, hover ? Ui.TILE_HOVER : Ui.TILE, hover ? Ui.ERR : Ui.EDGE_LIGHT);
        int color = hover ? 0xFFFF6A62 : Ui.ERR;
        if (UiThemes.rounded()) {
            Ui.center(g, Ui.font(Minecraft.getInstance().font), "×", x + w / 2, y + (h - 8) / 2, color);
            return;
        }
        // the X crosshair's own picture, in red, centred on the button
        int[] data = dev.crit.client.modules.CrosshairModule.pixelData(dev.crit.client.modules.CrosshairModule.X_CROSSHAIR);
        if (data == null) return;
        int tw = data[0], th = data[1];
        int minX = tw, minY = th, maxX = -1, maxY = -1;
        for (int py = 0; py < th; py++) {
            for (int px = 0; px < tw; px++) {
                if ((data[2 + py * tw + px] >>> 24) == 0) continue;
                minX = Math.min(minX, px); maxX = Math.max(maxX, px);
                minY = Math.min(minY, py); maxY = Math.max(maxY, py);
            }
        }
        if (maxX < 0) return;
        int ox = x + (w - (maxX - minX + 1)) / 2 - minX, oy = y + (h - (maxY - minY + 1)) / 2 - minY;
        for (int py = minY; py <= maxY; py++) {
            for (int px = minX; px <= maxX; px++) {
                int a = data[2 + py * tw + px] >>> 24;
                if (a == 0) continue;
                g.fill(ox + px, oy + py, ox + px + 1, oy + py + 1, (a << 24) | (color & 0xFFFFFF));
            }
        }
    }

    /** A "recent" card in the client's tile style: icon, name and date. */
    private static void card(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h,
                             QuickPlay.Icon icon, String title, String sub, boolean enabled, boolean hover) {
        Ui.box(g, x, y, w, h, !enabled ? Ui.FIELD : hover ? Ui.TILE_HOVER : Ui.TILE,
                !enabled ? Ui.EDGE : hover ? Ui.ACCENT_DARK : Ui.EDGE_LIGHT);
        if (enabled && (!UiThemes.rounded() || hover)) Ui.indicator(g, x, y, h, hover ? Ui.ACCENT : Ui.ACCENT_DARK);

        int size = 32, ix = x + 8, iy = y + (h - size - 2) / 2;
        Ui.shapedBox(g, ix, iy, size + 2, size + 2, 0, Ui.FIELD, Ui.EDGE);
        g.blit(RenderPipelines.GUI_TEXTURED, icon.id(), ix + 1, iy + 1, 0, 0, size, size, icon.w(), icon.h(), icon.w(), icon.h());
        if (!enabled) g.fill(ix + 1, iy + 1, ix + 1 + size, iy + 1 + size, 0xB0141414);

        int tx = ix + size + 2 + 7, room = x + w - 5 - tx;
        Ui.text(g, font, Ui.trim(font, title, room), tx, y + h / 2 - 9, enabled ? Ui.TEXT : Ui.MUTED);
        Ui.text(g, font, Ui.trim(font, sub, room), tx, y + h / 2 + 2, enabled ? Ui.MUTED : Ui.DIM);
    }

    private static void drawAccount(GuiGraphicsExtractor g, Font font, Layout l, int height, int mx, int my) {
        int x = l.rx(), y = l.acctY(), w = l.pw();
        boolean hover = Ui.in(mx, my, x, y, w, 20);
        Ui.box(g, x, y, w, 20, hover || accountsOpen ? Ui.TILE_HOVER : Ui.TILE,
                accountsOpen ? Ui.ACCENT_DARK : hover ? Ui.EDGE_LIGHT : Ui.EDGE);
        Ui.indicator(g, x, y, 20, accountsOpen || hover ? Ui.ACCENT : Ui.ACCENT_DARK);
        Ui.text(g, font, Ui.trim(font, Sessions.currentName(), w - 30), x + 11, y + 6, Ui.TEXT);
        Ui.chevron(g, x + w - 12, y + 10, accountsOpen, Ui.TEXT);

        if (!accountsOpen) return;
        List<Row> rows = accountRows();
        int shown = shownRows(l, height, rows.size());
        int top = y + 23, total = shown + 1;
        Ui.panel(g, x, top - 2, w, total * ROW_H + 4, Ui.TILE, Ui.PANEL, Ui.EDGE_LIGHT);
        for (int i = 0; i < total; i++) {
            int ry = top + i * ROW_H;
            boolean manage = i == shown;
            boolean over = Ui.in(mx, my, x + 1, ry, w - 2, ROW_H);
            if (manage) g.fill(x + 4, ry - 1, x + w - 4, ry, Ui.EDGE);
            if (over) Ui.surface(g, x + 4, ry + 1, w - 8, ROW_H - 2, Ui.TILE_HOVER);
            if (manage) {
                Ui.text(g, font, "Manage accounts...", x + 9, ry + 5, over ? Ui.TEXT : Ui.MUTED);
            } else {
                Row r = rows.get(i);
                if (r.current()) Ui.surface(g, x + 4, ry + 4, 2, ROW_H - 8, Ui.ACCENT);
                Ui.text(g, font, Ui.trim(font, r.label(), w - 22), x + 9, ry + 5, r.current() ? Ui.ACCENT : over ? Ui.TEXT : Ui.MUTED);
            }
        }
    }

    private static void drawDoll(GuiGraphicsExtractor g, Minecraft mc, Font font, Layout l, int mx, int my) {
        loadDollSkin(mc);

        int x = l.rx(), y = l.dollY(), w = l.pw(), h = l.dollH();
        int rs = Math.max(1, Math.min(3, (int) Math.round((double) mc.getWindow().getGuiScale())));

        // soft shadow on the "floor" under the feet
        int cx = x + w / 2, floor = y + h - 6;
        g.fill(cx - 16, floor, cx + 16, floor + 1, 0x30000000);
        g.fill(cx - 22, floor + 1, cx + 22, floor + 3, 0x28000000);
        g.fill(cx - 16, floor + 3, cx + 16, floor + 4, 0x20000000);

        double follow = Math.max(-1, Math.min(1, (mx - cx) / (w * 2.0)));
        int[] rects = DollRenderer.frame(w * rs, h * rs, follow, rs);
        if (rects != null) {
            // drawn at the screen's real pixel size, so edges are as fine as the rest of the game
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(1f / rs, 1f / rs);
            for (int i = 0; i < rects.length; i += 5) g.fill(rects[i], rects[i + 1], rects[i + 2], rects[i + 3], rects[i + 4]);
            g.pose().popMatrix();
        } else if (DollRenderer.failed()) {
            Ui.center(g, font, "?", cx, y + h / 2 - 4, Ui.DIM);
            long now = System.currentTimeMillis();
            if (now - lastRetry > 15000) { // try again now and then, e.g. once the connection is back
                lastRetry = now;
                DollRenderer.retry();
            }
        } else {
            Ui.center(g, font, "...", cx, y + h / 2 - 4, Ui.DIM);
        }
    }

    private static void loadDollSkin(Minecraft mc) {
        var local = OfflineSkin.preview();
        if (local != null) {
            DollRenderer.useLocalSkin(local.id().toString(), local.pixels(), local.slim());
            return;
        }

        var user = mc.getUser();
        boolean offline = AuthState.isLocalSession(user);
        boolean ely = "ely".equals(Sessions.launcherAccountType(user));
        String active = !offline && !ely && SkinOverride.active() ? SkinLibrary.active() : "";
        var profile = user.getProfileId();
        String key = user.getName() + ":" + profile + ":" + offline + ":" + ely + ":" + active;
        DollRenderer.ensureSkin(key, () -> {
            if (!offline) {
                try {
                    if (!ely && !active.isEmpty()) {
                        for (SkinLibrary.Entry e : SkinLibrary.all()) {
                            if (active.equals(e.id)) return new DollRenderer.Skin(SkinLibrary.bytes(e), e.slim);
                        }
                    }
                    var skin = ely ? SkinApi.elySkin(user.getName()) : SkinApi.steal(user.getName());
                    return new DollRenderer.Skin(skin.png(), skin.slim());
                } catch (IOException | RuntimeException e) {
                    // No published skin or the profile service is unavailable; display the vanilla default.
                }
            }
            var vanilla = DefaultPlayerSkin.get(profile);
            try (var in = mc.getResourceManager().open(vanilla.body().texturePath())) {
                return new DollRenderer.Skin(in.readAllBytes(), vanilla.model() == PlayerModelType.SLIM);
            }
        });
    }

    private TitleExtras() {}
}
