package dev.crit.client.skins;

import dev.crit.client.CritClient;
import com.google.common.collect.ImmutableMultimap;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * This is what makes the skin change appear without restarting the game.
 *
 * Minecraft caches your profile's texture data for the whole session, which is why a skin
 * changed through Mojang's API normally only shows up after a relaunch. After a successful
 * upload we know the new texture URL, so PlayerInfoMixin swaps it into your own GameProfile
 * whenever the game builds your PlayerInfo (joining a world or server).
 */
public final class SkinOverride {
    private record Override(UUID owner, String url, boolean slim) {}
    private static volatile Override skin;

    public static void set(UUID owner, String url, boolean slim) { skin = new Override(owner, url, slim); }

    public static boolean active() {
        return activeSkin() != null;
    }

    private static Override activeSkin() {
        Override current = skin;
        if (current == null) return null;
        var user = AuthState.user();
        return AuthState.isOnline(user) && current.owner().equals(user.getProfileId()) ? current : null;
    }

    public static GameProfile transform(GameProfile p) {
        Override current = activeSkin();
        if (current == null || !p.id().equals(current.owner())) return p;
        try {
            JsonObject textures = new JsonObject();
            JsonObject skin = new JsonObject();
            skin.addProperty("url", current.url());
            if (current.slim()) {
                JsonObject meta = new JsonObject();
                meta.addProperty("model", "slim");
                skin.add("metadata", meta);
            }
            textures.add("SKIN", skin);

            // keep the cape if the old textures property had one
            for (Property old : p.properties().get("textures")) {
                JsonObject o = JsonParser.parseString(new String(
                        Base64.getDecoder().decode(old.value()), StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject t = o.getAsJsonObject("textures");
                if (t != null && t.has("CAPE")) textures.add("CAPE", t.get("CAPE"));
                break;
            }

            JsonObject root = new JsonObject();
            root.addProperty("timestamp", System.currentTimeMillis());
            root.addProperty("profileId", p.id().toString().replace("-", ""));
            root.addProperty("profileName", p.name());
            root.add("textures", textures);

            String b64 = Base64.getEncoder().encodeToString(root.toString().getBytes(StandardCharsets.UTF_8));
            PropertyMap props = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", b64)));
            return new GameProfile(p.id(), p.name(), props);
        } catch (Exception e) {
            CritClient.LOG.warn("Skin override failed, using vanilla profile", e);
            return p;
        }
    }

    private SkinOverride() {}
}
