package dev.crit.client.mixin;

import dev.crit.client.skins.OfflineSkin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cover the local player's first render, even before the tab-list entry exists. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void critclient$localSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        AbstractClientPlayer player = (AbstractClientPlayer) (Object) this;
        if (!player.isLocalPlayer()) return;
        PlayerSkin local = OfflineSkin.localSkin(player.getGameProfile());
        if (local != null) cir.setReturnValue(OfflineSkin.withVanillaCape(local, cir.getReturnValue()));
    }
}
