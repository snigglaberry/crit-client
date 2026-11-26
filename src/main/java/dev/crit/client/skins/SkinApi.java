package dev.crit.client.skins;

import dev.crit.client.Net;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import net.minecraft.client.User;

/** Everything that talks to Mojang: applying a skin to your account, and looking up other players' skins. */
public final class SkinApi {
    public record Applied(String url, boolean slim) {}
    public record Stolen(String name, byte[] png, boolean slim) {}

    /** Uploads the skin to your Mojang account using the session's access token. */
    public static Applied apply(User user, byte[] png, boolean slim) throws IOException {
        if (!AuthState.isOnline(user)) {
            throw new IOException(AuthState.status() == AuthState.Status.CHECKING
                    ? "Still verifying your account, try again in a second." : "Skins are disabled for this session.");
        }
        String b = "----SkinStudio" + Long.toHexString(System.nanoTime());
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n"
                + (slim ? "slim" : "classic") + "\r\n").getBytes(StandardCharsets.UTF_8));
        o.write(("--" + b + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        o.write(png);
        o.write(("\r\n--" + b + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest r = Net.req("https://api.minecraftservices.com/minecraft/profile/skins")
                .header("Authorization", "Bearer " + user.getAccessToken())
                .header("Content-Type", "multipart/form-data; boundary=" + b)
                .POST(HttpRequest.BodyPublishers.ofByteArray(o.toByteArray()))
                .build();
        HttpResponse<byte[]> res = Net.send(r);
        int code = res.statusCode();
        if (code == 200) {
            JsonObject j = JsonParser.parseString(new String(res.body(), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonArray skins = j.getAsJsonArray("skins");
            JsonObject pick = null;
            if (skins != null) {
                for (JsonElement el : skins) {
                    JsonObject s = el.getAsJsonObject();
                    if (pick == null || "ACTIVE".equals(s.has("state") ? s.get("state").getAsString() : "")) pick = s;
                }
            }
            if (pick == null || !pick.has("url")) throw new IOException("Mojang accepted the skin but sent no texture URL.");
            boolean s = pick.has("variant") ? "SLIM".equalsIgnoreCase(pick.get("variant").getAsString()) : slim;
            return new Applied(pick.get("url").getAsString(), s);
        }
        throw new IOException(switch (code) {
            case 401 -> "Your session expired. Relaunch the game from your launcher.";
            case 400 -> "Mojang rejected that skin file.";
            case 429 -> "Mojang is rate limiting skin changes. Wait a minute and retry.";
            default -> "Mojang returned HTTP " + code + ".";
        });
    }

    /** Looks up a player by name and downloads their current skin (public data). */
    public static Stolen steal(String name) throws IOException {
        if (!name.matches("[A-Za-z0-9_]{1,16}")) throw new IOException("That isn't a valid Minecraft username.");
        String lookup;
        try {
            lookup = Net.getText("https://api.minecraftservices.com/minecraft/profile/lookup/name/" + name);
        } catch (Net.HttpStatusException e) {
            if (e.code == 404 || e.code == 204) throw new IOException("No player named \"" + name + "\" exists.");
            if (e.code == 429) throw new IOException("Too many lookups. Wait a minute and retry.");
            throw e;
        }
        JsonObject p = JsonParser.parseString(lookup).getAsJsonObject();
        String id = p.get("id").getAsString();
        String real = p.get("name").getAsString();

        String prof = Net.getText("https://sessionserver.mojang.com/session/minecraft/profile/" + id);
        JsonArray props = JsonParser.parseString(prof).getAsJsonObject().getAsJsonArray("properties");
        if (props != null) {
            for (JsonElement el : props) {
                JsonObject prop = el.getAsJsonObject();
                if (!"textures".equals(prop.get("name").getAsString())) continue;
                JsonObject tex = JsonParser.parseString(new String(
                        Base64.getDecoder().decode(prop.get("value").getAsString()), StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonObject("textures");
                if (tex == null || !tex.has("SKIN")) break;
                JsonObject skin = tex.getAsJsonObject("SKIN");
                boolean slim = skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")
                        && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
                return new Stolen(real, Net.getBytes(skin.get("url").getAsString()), slim);
            }
        }
        throw new IOException(real + " is using a default skin, nothing to steal.");
    }

    /** Public Ely.by profile data for the title preview; never send the Ely.by token to Mojang. */
    public static Stolen elySkin(String name) throws IOException {
        if (!name.matches("[A-Za-z0-9_]{1,16}")) throw new IOException("That isn't a valid Minecraft username.");
        JsonObject profile = JsonParser.parseString(Net.getText("https://skinsystem.ely.by/textures/"
                + name.toLowerCase(Locale.ROOT))).getAsJsonObject();
        JsonObject skin = profile.getAsJsonObject("SKIN");
        if (skin == null || !skin.has("url")) throw new IOException("No skin on this Ely.by profile.");
        JsonObject meta = skin.getAsJsonObject("metadata");
        boolean slim = meta != null && meta.has("model") && "slim".equals(meta.get("model").getAsString());
        String url = skin.get("url").getAsString().replaceFirst("^http://", "https://");
        URI uri = URI.create(url);
        String host = uri.getHost();
        if (!"https".equals(uri.getScheme()) || host == null
                || (!host.equals("ely.by") && !host.endsWith(".ely.by"))) {
            throw new IOException("Unexpected Ely.by skin host.");
        }
        return new Stolen(name, Net.getBytes(url), slim);
    }

    private SkinApi() {}
}
