package dev.crit.client.accounts;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.crit.client.skins.AuthState;
import dev.crit.client.skins.OfflineSkin;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Selects the startup account and swaps the client's user for manual account changes. Creating and
 * replacing the user uses reflection to handle constructor differences between Minecraft versions.
 */
public final class Sessions {
    public static final Logger LOG = LoggerFactory.getLogger("CritClient/accounts");
    public static final String LAUNCHER_ACCOUNT_TYPE_PROPERTY = "critclient.launcher.accountType";
    private static final String LAUNCHER_ACCOUNT_TYPE = System.getProperty(LAUNCHER_ACCOUNT_TYPE_PROPERTY, "");
    private static final String USER_CLASS = "net.minecraft.client.User";

    private static final String KEYS_CLASS = "net.minecraft.client.multiplayer.ProfileKeyPairManager";

    private static volatile User original;
    private static Object originalKeys;
    private static boolean applied;
    private static String activeAccountId;

    public static boolean launcherManaged() {
        return switch (LAUNCHER_ACCOUNT_TYPE) {
            case "offline", "microsoft", "ely" -> true;
            default -> false;
        };
    }

    public static String launcherAccountType(User user) {
        return user == original && launcherManaged() ? LAUNCHER_ACCOUNT_TYPE : "";
    }

    /** The saved choice can differ from the active one when Crit Launcher supplies the startup account. */
    public static String activeAccountId() { return activeAccountId; }

    private static Field userField() throws ReflectiveOperationException {
        for (Field f : Minecraft.class.getDeclaredFields()) {
            if (f.getType().getName().equals(USER_CLASS)) {
                f.setAccessible(true);
                return f;
            }
        }
        throw new NoSuchFieldException("Minecraft has no User field (" + USER_CLASS + ")");
    }

    public static String currentName() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null || mc.getUser() == null ? "?" : mc.getUser().getName();
    }

    private static void capture() throws ReflectiveOperationException {
        if (original == null) {
            original = Minecraft.getInstance().getUser();
            Field kf = keysField();
            if (kf != null) originalKeys = kf.get(Minecraft.getInstance());
        }
    }

    /**
     * The game's profile key pair (used to sign chat) belongs to the account the game was launched with. If we swap
     * the user but leave it, joining a world sends the old account's public key with the new UUID and the server
     * rejects it with "Invalid signature for profile public key". So the key manager is swapped together with the user.
     */
    private static Field keysField() {
        try {
            for (Field f : Minecraft.class.getDeclaredFields()) {
                if (f.getType().getName().equals(KEYS_CLASS)) {
                    f.setAccessible(true);
                    return f;
                }
            }
        } catch (RuntimeException e) {
            LOG.warn("Couldn't look up the profile key manager", e);
        }
        return null;
    }

    /** The "no keys" manager Minecraft itself uses for offline play, or null if this version doesn't have one. */
    private static Object emptyKeys() {
        try {
            Class<?> c = Class.forName(KEYS_CLASS);
            for (Field f : c.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == c) {
                    f.setAccessible(true);
                    return f.get(null);
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("Couldn't find the empty profile key manager", e);
        }
        return null;
    }

    private static void setKeys(Object manager) {
        if (manager == null) return;
        try {
            Field kf = keysField();
            if (kf != null) kf.set(Minecraft.getInstance(), manager);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("Couldn't swap the profile key manager", e);
        }
    }

    public static void restoreOriginal() throws ReflectiveOperationException {
        capture();
        userField().set(Minecraft.getInstance(), original);
        setKeys(originalKeys);
        activeAccountId = null;
        refreshSkins(false);
    }

    public static void use(Store.Account a) throws ReflectiveOperationException {
        select(a);
        refreshSkins(false);
    }

    private static void select(Store.Account a) throws ReflectiveOperationException {
        capture();
        String token = "OFFLINE".equals(a.type) ? "0" : a.token;
        userField().set(Minecraft.getInstance(), build(a.name, UUID.fromString(a.uuid), token));
        setKeys(emptyKeys()); // no signed chat key for a swapped account; the original's key would be rejected
        activeAccountId = a.id;
    }

    /** Keep Crit Launcher's account on a managed boot; restore the saved account on standalone boots. */
    public static synchronized void applySavedOnce() {
        if (applied) return;
        applied = true;
        try {
            capture();
            if (!launcherManaged()) {
                Store.Account a = Store.get().activeAccount();
                if (a != null) select(a);
            }
        } catch (Exception e) {
            LOG.warn("Couldn't re-apply the saved account", e);
        }
        refreshSkins(!launcherManaged());
    }

    private static void refreshSkins(boolean migrateLegacy) {
        AuthState.check();
        OfflineSkin.load(migrateLegacy);
    }

    private static Object build(String name, UUID id, String token) throws ReflectiveOperationException {
        Class<?> uc = Class.forName(USER_CLASS);
        Constructor<?> best = null;
        for (Constructor<?> c : uc.getDeclaredConstructors()) {
            if (best == null || c.getParameterCount() > best.getParameterCount()) best = c;
        }
        if (best == null) throw new NoSuchMethodException("User has no constructor");
        Class<?>[] types = best.getParameterTypes();
        Object[] args = new Object[types.length];
        int strings = 0;
        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];
            if (t == String.class) {
                args[i] = strings == 0 ? name : strings == 1 ? token : "";
                strings++;
            } else if (t == UUID.class) {
                args[i] = id;
            } else if (t == Optional.class) {
                args[i] = Optional.empty();
            } else if (t.isEnum()) {
                Object[] cs = t.getEnumConstants();
                Object pick = cs.length > 0 ? cs[0] : null;
                for (Object c : cs) if (c.toString().equals("MSA")) pick = c;
                args[i] = pick;
            }
        }
        best.setAccessible(true);
        return best.newInstance(args);
    }

    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    /** Checks a Minecraft access token with Mojang and returns the account it belongs to. */
    public static CompletableFuture<Store.Account> fromToken(String raw) {
        String token = raw.trim();
        if (token.regionMatches(true, 0, "Bearer ", 0, 7)) token = token.substring(7).trim();
        final String tok = token;
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + tok)
                .GET().build();
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                .sendAsync(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(r -> {
                    if (r.statusCode() == 401 || r.statusCode() == 403) throw new IllegalStateException("Token is invalid or expired");
                    if (r.statusCode() == 404) throw new IllegalStateException("That Microsoft account doesn't own Minecraft");
                    if (r.statusCode() != 200) throw new IllegalStateException("Mojang answered HTTP " + r.statusCode());
                    JsonObject o = JsonParser.parseString(r.body()).getAsJsonObject();
                    String id = o.get("id").getAsString();
                    Store.Account a = new Store.Account();
                    a.type = "MICROSOFT";
                    a.name = o.get("name").getAsString();
                    a.uuid = UUID.fromString(id.replaceFirst(
                            "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5")).toString();
                    a.token = tok;
                    return a;
                });
    }

    private Sessions() {}
}
