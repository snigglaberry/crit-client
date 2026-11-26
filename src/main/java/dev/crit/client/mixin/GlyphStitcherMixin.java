package dev.crit.client.mixin;

import dev.crit.client.ui.UiFonts;
import net.minecraft.client.gui.font.GlyphStitcher;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(GlyphStitcher.class)
public abstract class GlyphStitcherMixin {
    @Shadow @Final private Identifier texturePrefix;

    @ModifyArg(method = "stitch", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/texture/TextureManager;register(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/renderer/texture/AbstractTexture;)V"), index = 1)
    private AbstractTexture critclient$trackInterAtlas(AbstractTexture texture) {
        UiFonts.trackAtlas(texturePrefix, texture.getTexture());
        return texture;
    }
}
