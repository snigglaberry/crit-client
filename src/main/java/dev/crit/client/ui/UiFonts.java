package dev.crit.client.ui;

import com.mojang.blaze3d.textures.GpuTexture;
import dev.crit.client.mixin.FontAccessor;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GlyphSource;
import net.minecraft.client.gui.font.glyphs.EffectGlyph;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** Changes the default face for Crit UI only, including its native edit boxes and their text measurements. */
public final class UiFonts {
    private static final Identifier INTER_ID = Identifier.fromNamespaceAndPath("critclient", "inter");
    private static final Identifier SEMIBOLD_ID = Identifier.fromNamespaceAndPath("critclient", "inter_semibold");
    private static final FontDescription INTER = new FontDescription.Resource(INTER_ID);
    private static final FontDescription SEMIBOLD = new FontDescription.Resource(SEMIBOLD_ID);
    // Atlases are rebuilt on resource reload. Don't keep the old GPU textures alive.
    private static final Set<GpuTexture> INTER_ATLASES = Collections.newSetFromMap(new WeakHashMap<>());
    private static Font lastVanilla, inter;

    public static void trackAtlas(Identifier font, GpuTexture texture) {
        if (INTER_ID.equals(font) || SEMIBOLD_ID.equals(font)) INTER_ATLASES.add(texture);
    }

    public static boolean isInterAtlas(GpuTexture texture) { return INTER_ATLASES.contains(texture); }

    static Font font(Font vanilla) {
        if (UiThemes.usesVanillaFont()) return vanilla;
        if (vanilla != lastVanilla) {
            Font.Provider provider = ((FontAccessor) vanilla).critclient$getProvider();
            inter = new Font(new Font.Provider() {
                @Override public GlyphSource glyphs(FontDescription description) {
                    return provider.glyphs(FontDescription.DEFAULT.equals(description) ? INTER : description);
                }

                @Override public EffectGlyph effect() { return provider.effect(); }
            });
            lastVanilla = vanilla;
        }
        return inter;
    }

    static Component heading(String text) {
        return UiThemes.usesVanillaFont() ? Component.literal(text).withStyle(ChatFormatting.BOLD)
                : Component.literal(text).withStyle(style -> style.withFont(SEMIBOLD));
    }

    private UiFonts() {}
}
