package dev.crit.client.mixin;

import dev.crit.client.title.MenuSettings;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.client.renderer.texture.CubeMapTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Loads Crit's panorama alongside Minecraft's, so switching between them doesn't require a resource reload. */
@Mixin(CubeMap.class)
public abstract class CubeMapMixin {
    @Unique private static final Identifier critclient$vanilla = Identifier.withDefaultNamespace("textures/gui/title/background/panorama");
    @Unique private static final Identifier critclient$panorama = Identifier.fromNamespaceAndPath("critclient", critclient$vanilla.getPath());

    @Shadow @Final private Identifier location;

    @Inject(method = "registerTextures", at = @At("TAIL"))
    private void critclient$registerPanorama(TextureManager textures, CallbackInfo ci) {
        if (critclient$vanilla.equals(location)) textures.register(critclient$panorama, new CubeMapTexture(critclient$panorama));
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureManager;getTexture(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/client/renderer/texture/AbstractTexture;"))
    private Identifier critclient$selectPanorama(Identifier id) {
        return critclient$vanilla.equals(id) && MenuSettings.panorama() ? critclient$panorama : id;
    }
}
