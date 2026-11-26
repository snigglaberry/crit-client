package dev.crit.client.pages;

import dev.crit.client.CritScreen;
import dev.crit.client.accounts.Sessions;
import dev.crit.client.accounts.Store;
import dev.crit.client.ui.Ui;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Manage offline and Microsoft accounts and switch back to the launcher account. */
public final class AccountsPage extends Page {
    private static final int ROW = 22;
    private static final int FORM_HEIGHT = 44;

    private record Row(String label, boolean selected, Runnable select, Runnable delete) {}

    private double scroll, target;
    private volatile String status = "";
    private volatile int statusColor = Ui.MUTED;

    // form state survives rebuilds
    private String aName = "", aToken = "";

    public AccountsPage(CritScreen s) {
        super(s);
    }

    private void say(String t, int color) {
        status = t;
        statusColor = color;
    }

    private int formTop() { return y + h - FORM_HEIGHT; }
    private int listTop() { return y + 24; }
    private int listBottom() { return formTop() - 20; }

    @Override
    public void init() {
        int ft = formTop();
        int hw = Math.min(w, 380);
        s.box(x, ft, hw - 90, 18, "offline username", aName, 16, v -> aName = v);
        s.box(x, ft + 24, hw - 90, 18, "Microsoft access token", aToken, 4096, v -> aToken = v);
    }

    @Override
    public void shown() {
        say("", Ui.MUTED);
    }

    // ------------------------------------------------------------------ rows

    private List<Row> rows() {
        Store st = Store.get();
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("Original launcher account", Sessions.activeAccountId() == null, () -> {
            try {
                Sessions.restoreOriginal();
                st.activeAccount = null;
                st.save();
                say("Back on your launcher account.", Ui.ACCENT);
            } catch (Exception e) {
                say("Couldn't switch: " + msg(e), Ui.ERR);
            }
        }, null));
        for (Store.Account a : new ArrayList<>(st.accounts)) {
            String kind = a.type.equals("OFFLINE") ? "Offline" : "Microsoft";
            rows.add(new Row(a.name + "  (" + kind + ")", a.id.equals(Sessions.activeAccountId()), () -> {
                try {
                    Sessions.use(a);
                    st.activeAccount = a.id;
                    st.save();
                    say("Logged in as " + a.name + ".", Ui.ACCENT);
                } catch (Exception e) {
                    say("Couldn't switch: " + msg(e), Ui.ERR);
                }
            }, () -> {
                st.accounts.remove(a);
                if (a.id.equals(st.activeAccount)) st.activeAccount = null;
                if (a.id.equals(Sessions.activeAccountId())) {
                    try {
                        Sessions.restoreOriginal();
                    } catch (Exception ignored) {
                    }
                }
                st.save();
            }));
        }
        return rows;
    }

    private double maxScroll(int n) {
        return Math.max(0, n * ROW - (listBottom() - listTop()));
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphicsExtractor g, int mx, int my) {
        Ui.text(g, s.font(), "Accounts", x, y + 4, Ui.TEXT);
        String using = "Playing as: " + Sessions.currentName();
        Ui.right(g, s.font(), Ui.trim(s.font(), using, w - 90), x + w, y + 4, Ui.MUTED);

        List<Row> rows = rows();
        target = Math.max(0, Math.min(target, maxScroll(rows.size())));
        scroll += (target - scroll) * 0.35;
        if (Math.abs(target - scroll) < 0.1) scroll = target;

        int top = listTop(), bottom = listBottom();
        int rw = Math.min(w, 380);
        g.enableScissor(x, top, x + w, bottom);
        s.clip(top, bottom);
        int ry = top - (int) Math.round(scroll);
        for (Row r : rows) {
            if (ry + ROW > top && ry < bottom) drawRow(g, r, x, ry, rw, mx, my, top, bottom);
            ry += ROW;
        }
        s.unclip();
        g.disableScissor();
        Ui.scrollbar(g, x + rw + 4, top, bottom - top, scroll, maxScroll(rows.size()));

        if (!status.isEmpty()) Ui.text(g, s.font(), Ui.trim(s.font(), status, w), x, formTop() - 14, statusColor);

        int ft = formTop();
        int hw = Math.min(w, 380);
        s.button(g, x + hw - 84, ft, 84, 18, "Add offline", mx, my, true, true, this::addOffline);
        s.button(g, x + hw - 84, ft + 24, 84, 18, "Add token", mx, my, true, true, this::addToken);
    }

    private void drawRow(GuiGraphicsExtractor g, Row r, int rx, int ry, int rw, int mx, int my, int top, int bottom) {
        boolean hov = my >= top && my < bottom && Ui.in(mx, my, rx, ry, rw, ROW - 2);
        Ui.controlBox(g, rx, ry, rw, ROW - 2, hov ? Ui.TILE_HOVER : Ui.TILE, r.selected() ? Ui.ACCENT : hov ? Ui.EDGE_LIGHT : Ui.EDGE);
        if (r.selected()) g.fill(rx + 1, ry + 3, rx + 3, ry + ROW - 5, Ui.ACCENT);
        int dw = r.delete() == null ? 0 : 22;
        Ui.text(g, s.font(), Ui.trim(s.font(), r.label(), rw - 18 - dw), rx + 9, ry + 6, r.selected() ? Ui.ACCENT : Ui.TEXT);
        s.region(rx, ry, rw - dw, ROW - 2, r.select());
        if (r.delete() != null) {
            boolean dh = my >= top && my < bottom && Ui.in(mx, my, rx + rw - 20, ry + 2, 16, 16);
            Ui.controlBox(g, rx + rw - 20, ry + 2, 16, 16, dh ? 0xFF5A1D1D : Ui.FIELD, dh ? Ui.ERR : Ui.EDGE_LIGHT);
            Ui.center(g, s.font(), "x", rx + rw - 12, ry + 6, dh ? Ui.TEXT : Ui.MUTED);
            s.region(rx + rw - 20, ry + 2, 16, 16, r.delete());
        }
    }

    @Override
    public boolean scroll(double mx, double my, double dy) {
        target = Math.max(0, Math.min(target - dy * 30, maxScroll(rows().size())));
        return true;
    }

    // ------------------------------------------------------------------ accounts

    private void addOffline() {
        String n = aName.trim();
        if (!n.matches("[A-Za-z0-9_]{1,16}")) {
            say("Names are 1-16 letters, digits or underscores.", Ui.ERR);
            return;
        }
        Store.Account acc = new Store.Account();
        acc.type = "OFFLINE";
        acc.name = n;
        acc.uuid = Sessions.offlineUuid(n).toString();
        aName = "";
        finishAdd(acc);
    }

    private void addToken() {
        String t = aToken.trim();
        if (t.isEmpty()) {
            say("Paste a token first.", Ui.ERR);
            return;
        }
        say("Checking token with Mojang...", Ui.MUTED);
        Sessions.fromToken(t).whenComplete((acc, err) -> s.mc().execute(() -> {
            if (err != null) {
                Throwable c = err.getCause() != null ? err.getCause() : err;
                say("Token rejected: " + msg(c), Ui.ERR);
            } else {
                aToken = "";
                finishAdd(acc);
            }
        }));
    }

    private void finishAdd(Store.Account acc) {
        Store st = Store.get();
        st.accounts.removeIf(o -> o.uuid.equals(acc.uuid) && o.type.equals(acc.type));
        st.accounts.add(acc);
        try {
            Sessions.use(acc);
            st.activeAccount = acc.id;
            say("Logged in as " + acc.name + ".", Ui.ACCENT);
        } catch (Exception e) {
            say("Saved " + acc.name + " but couldn't switch: " + msg(e), Ui.ERR);
        }
        st.save();
        s.rebuild();
    }

    private static String msg(Throwable t) {
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
