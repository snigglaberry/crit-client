package dev.crit.client.mixin;

import dev.crit.client.sky.CustomSky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Custom Sky: tints the distance fog to match the sky, so the horizon doesn't show the old blue. */
@Mixin(AtmosphericFogEnvironment.class)
public abstract class AtmosphericFogEnvironmentMixin {
    @Inject(method = "getBaseColor", at = @At("RETURN"), cancellable = true)
    private void critclient$skyFog(ClientLevel level, Camera camera, int renderDistance, float partialTick,
                                   CallbackInfoReturnable<Integer> cir) {
        Integer fog = CustomSky.fog();
        if (fog != null) cir.setReturnValue(fog);
    }
}
