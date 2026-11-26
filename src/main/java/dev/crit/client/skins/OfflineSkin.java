package dev.crit.client.skins;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import dev.crit.client.CritClient;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** Crit Client's local-only skin, shared with the launcher through {@link OfflineSkinFile}. */
public final class OfflineSkin {
    private record Applied(User user, Preview preview, PlayerSkin localSkin) {}

    public record Preview(Identifier id, int[] pixels, boolean slim, String clientKey) {}

    private static volatile Applied applied;
    private static int counter;

    /** Called on the client thread after the startup account is selected, and after manual account switches. */
    public static void load(boolean migrateLegacy) {
        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();
        discard(mc);
        if (!AuthState.isLocalSession(user)) return;
        try {
            UUID owner = user.getProfileId();
            var selection = OfflineSkinFile.read(CritClient.DIR, owner);
            if (selection == null && migrateLegacy) {
                selection = OfflineSkinFile.migrateLegacy(CritClient.DIR, owner, SkinLibrary.active());
            }
            if (selection != null && selection.png() != null) register(mc, user, selection);
        } catch (Exception e) {
            CritClient.LOG.warn("Could not load the saved offline skin: {}", e.getMessage());
        }
    }

    /** Validate on the worker thread, then save and upload together on the client thread. */
    public static CompletableFuture<Void> apply(User user, byte[] raw, Boolean slim, String clientKey) throws IOException {
        if (!AuthState.isLocalSession(user)) throw new IOException("Local skins are for offline accounts.");
        var selection = OfflineSkinFile.prepare(raw, slim, clientKey);
        Minecraft mc = Minecraft.getInstance();
        return mc.submit(() -> {
            try {
                if (mc.getUser() != user) throw new IOException("The account changed. Apply the skin again.");
                OfflineSkinFile.write(CritClient.DIR, user.getProfileId(), selection);
                register(mc, user, selection);
            } catch (IOException e) {
                throw new CompletionException(e);
            }
        });
    }

    /** On the client thread, reset this account without reviving its old skin on the next boot. */
    public static void clear() throws IOException {
        Minecraft mc = Minecraft.getInstance();
        User user = mc.getUser();
        if (!AuthState.isLocalSession(user)) return;
        OfflineSkinFile.reset(CritClient.DIR, user.getProfileId());
        discard(mc);
    }

    public static Preview preview() {
        Applied skin = applied;
        return skin != null && Minecraft.getInstance().getUser() == skin.user() ? skin.preview() : null;
    }

    /** Replace only your player's body and model, without starting a vanilla skin lookup for a local file. */
    public static PlayerSkin localSkin(GameProfile profile) {
        Applied skin = ownSkin(profile);
        return skin == null ? null : skin.localSkin();
    }

    public static PlayerSkin withVanillaCape(PlayerSkin local, PlayerSkin original) {
        return original == null || original == local || (original.cape() == null && original.elytra() == null) ? local
                : PlayerSkin.insecure(local.body(), original.cape(), original.elytra(), local.model());
    }

    private static Applied ownSkin(GameProfile profile) {
        Applied skin = applied;
        if (skin == null || profile == null) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.getUser() != skin.user()) return null;
        // Offline servers sometimes assign their own UUID to the local player. Never match by name alone.
        UUID owner = mc.player != null ? mc.player.getUUID() : skin.user().getProfileId();
        return owner.equals(profile.id()) && skin.user().getName().equalsIgnoreCase(profile.name()) ? skin : null;
    }

    private static void register(Minecraft mc, User user, OfflineSkinFile.Selection selection) {
        Identifier id = Identifier.fromNamespaceAndPath("critclient", "offline_skin_" + counter++);
        NativeImage image = new NativeImage(64, 64, false);
        DynamicTexture texture = null;
        try {
            int[] pixels = selection.pixels();
            for (int i = 0; i < pixels.length; i++) image.setPixel(i % 64, i / 64, pixels[i]);
            texture = new DynamicTexture(() -> "Crit offline skin", image);
            mc.getTextureManager().register(id, texture);
        } catch (RuntimeException e) {
            if (texture == null) image.close();
            else texture.close();
            throw e;
        }
        discard(mc);
        PlayerSkin local = PlayerSkin.insecure(new ClientAsset.ResourceTexture(id, id), null, null,
                selection.slim() ? PlayerModelType.SLIM : PlayerModelType.WIDE);
        applied = new Applied(user, new Preview(id, selection.pixels(), selection.slim(), selection.clientKey()), local);
    }

    private static void discard(Minecraft mc) {
        Applied old = applied;
        applied = null;
        if (old != null) mc.getTextureManager().release(old.preview().id());
    }

    private OfflineSkin() {}
}
