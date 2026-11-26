package dev.crit.client.skins;

import dev.crit.client.CritClient;
import dev.crit.client.Net;
import dev.crit.client.accounts.Sessions;
import java.io.IOException;
import java.net.http.HttpResponse;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;

/**
 * Selects the skin backend for the active user. Only Microsoft tokens are checked with Mojang;
 * a failed check never turns a signed-in account into a local-skin account.
 */
public final class AuthState {
    public enum Status { CHECKING, ONLINE, OFFLINE, ELY, UNAVAILABLE }

    private record State(User user, Status status, String reason) {}
    private static volatile State state = new State(null, Status.CHECKING, "");

    public static Status status() { return state.status(); }
    public static String reason() { return state.reason(); }
    public static boolean isOnline(User user) {
        State current = state;
        return current.user() == user && current.status() == Status.ONLINE;
    }

    public static User user() { return Minecraft.getInstance().getUser(); }
    public static String name() { return user().getName(); }

    public static boolean isLocalSession(User user) {
        String type = Sessions.launcherAccountType(user);
        return (type.isEmpty() || type.equals("offline")) && noToken(user.getAccessToken());
    }

    private static boolean noToken(String t) {
        return t == null || t.isBlank() || t.equals("0") || t.equalsIgnoreCase("undefined")
                || t.equalsIgnoreCase("null") || t.equalsIgnoreCase("offline");
    }

    public static synchronized void check() {
        User user = user();
        String t = user.getAccessToken();
        if ("ely".equals(Sessions.launcherAccountType(user))) {
            state = new State(user, Status.ELY, "");
            return;
        }
        if (isLocalSession(user)) {
            state = new State(user, Status.OFFLINE, "");
            return;
        }
        if (noToken(t)) {
            state = new State(user, Status.UNAVAILABLE, "No valid session token.");
            return;
        }
        State pending = new State(user, Status.CHECKING, "");
        state = pending;
        Net.POOL.execute(() -> {
            try {
                HttpResponse<byte[]> r = Net.send(Net.req("https://api.minecraftservices.com/minecraft/profile")
                        .header("Authorization", "Bearer " + t).GET().build());
                int c = r.statusCode();
                if (c == 401 || c == 403 || c == 404) {
                    finish(pending, Status.UNAVAILABLE, "Mojang rejected the session. Sign in again in your launcher.");
                } else {
                    // 200 or transient service errors: let the normal Mojang upload endpoint handle the request.
                    finish(pending, Status.ONLINE, "");
                }
            } catch (IOException | IllegalArgumentException e) {
                finish(pending, Status.UNAVAILABLE, "Couldn't verify this session with Minecraft services.");
            }
        });
    }

    private static synchronized void finish(State pending, Status status, String reason) {
        if (state != pending) return; // a previous account's request can finish after a switch
        state = new State(pending.user(), status, reason);
        if (status == Status.UNAVAILABLE) CritClient.LOG.info("Skin changes unavailable: {}", reason);
    }

    private AuthState() {}
}
