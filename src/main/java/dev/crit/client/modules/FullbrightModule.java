package dev.crit.client.modules;


/**
 * Lights everything up: caves, nights and shadows look as bright as day. It works by feeding the game's
 * lightmap the same full night-vision boost a potion would (see LightmapRenderStateExtractorMixin), so it
 * does not touch your brightness setting and turns off cleanly.
 */
public final class FullbrightModule extends Module {
    /** True after the mixin last forced the lightmap, so the lightmap gets refreshed once more when this turns off. */
    public static volatile boolean forced;

    public FullbrightModule() {
        super("fullbright", "Fullbright", "Lights everything up so caves and nights are as bright as day.", false);
    }

}
