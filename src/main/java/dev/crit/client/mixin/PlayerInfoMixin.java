package dev.crit.client.mixin;

import com.mojang.authlib.GameProfile;
import dev.crit.client.skins.OfflineSkin;
import dev.crit.client.skins.SkinOverride;
import java.util.function.Supplier;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Online accounts: swaps in the freshly uploaded skin, bypassing the session-long profile cache.
 * Offline accounts: hands the game Crit Client's own skin for your player (no other skin mod needed).
 */
@Mixin(PlayerInfo.class)
public abstract class PlayerInfoMixin {
    @Shadow private Supplier<PlayerSkin> skinLookup;

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static GameProfile critclient$freshSkin(GameProfile profile) {
        return SkinOverride.transform(profile);
    }

    @Inject(method = "getSkin", at = @At("HEAD"), cancellable = true)
    private void critclient$offlineSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        GameProfile profile = ((PlayerInfo) (Object) this).getProfile();
        PlayerSkin local = OfflineSkin.localSkin(profile);
        if (local == null) return;
        // If a vanilla lookup already exists, keep its cape/elytra. Otherwise no need to start one.
        PlayerSkin original = skinLookup == null ? null : skinLookup.get();
        cir.setReturnValue(OfflineSkin.withVanillaCape(local, original));
    }
}
