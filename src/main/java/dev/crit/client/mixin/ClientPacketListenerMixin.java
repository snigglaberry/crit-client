package dev.crit.client.mixin;

import dev.crit.client.keys.NetStats;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds the server's time updates and ping answers to the TPS / ping overlays. Never required: if a name changes they just show "--". */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleSetTime", at = @At("HEAD"), require = 0)
    private void critclient$time(ClientboundSetTimePacket packet, CallbackInfo ci) {
        NetStats.onTimeUpdate();
    }

    @Inject(method = "handlePongResponse", at = @At("HEAD"), require = 0)
    private void critclient$pong(ClientboundPongResponsePacket packet, CallbackInfo ci) {
        NetStats.onPong(packet.time());
    }
}
