package dev.crit.client.modules;

import dev.crit.client.CritClient;
import dev.crit.client.Icons;
import dev.crit.client.Net;
import dev.crit.client.fx.GifFrames;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Plays a looping Subway Surfers gameplay clip down the right edge of the screen: as tall as the screen, flush with
 * the top, bottom and right edges. The GIF is decoded once on a background thread; only the frames about to be shown
 * exist as textures, so it costs very little memory.
 */
public final class SubwayModule extends Module {
    private static final String RESOURCE = "/assets/critclient/subway/gameplay.gif";
    /** How many upcoming frames are kept ready as textures. */
    private static final int LOOKAHEAD = 3;

    private volatile GifFrames.Result gif;
    private volatile boolean failed;
    private boolean loading;
    private long startNanos;
    private final Map<Integer, Identifier> textures = new HashMap<>();

    public SubwayModule() {
        super("subway", "Subway Surfer Gameplay", "Plays Subway Surfers gameplay down the right edge of the screen, from the top to the bottom.", false);
    }


    @Override
    public void onEnable() {
        startNanos = System.nanoTime();
    }

    @Override
    public void onDisable() {
        release(Minecraft.getInstance());
    }

    /** Draws the current frame, as tall as the screen and pressed against its right edge. */
    public void render(GuiGraphicsExtractor g, Minecraft mc) {
        if (failed) return;
        GifFrames.Result r = gif;
        if (r == null) {
            startLoading();
            return;
        }
        if (startNanos == 0) startNanos = System.nanoTime();

        int n = r.frames().size();
        int idx = frameAt(r);
        Identifier id = texture(mc, r, idx);
        if (id == null) return;
        for (int k = 1; k <= LOOKAHEAD; k++) texture(mc, r, (idx + k) % n);

        // free the frames that are no longer about to be shown
        for (Iterator<Map.Entry<Integer, Identifier>> it = textures.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Identifier> e = it.next();
            if (((e.getKey() - idx + n) % n) > LOOKAHEAD) {
                mc.getTextureManager().release(e.getValue());
                it.remove();
            }
        }

        int sw = g.guiWidth(), sh = g.guiHeight();
        int dw = Math.max(1, Math.round(sh * (float) r.width() / r.height()));
        g.blit(RenderPipelines.GUI_TEXTURED, id, sw - dw, 0, 0, 0, dw, sh, r.width(), r.height(), r.width(), r.height());
    }

    private int frameAt(GifFrames.Result r) {
        long t = ((System.nanoTime() - startNanos) / 1_000_000L) % r.totalMs();
        int[] ends = r.ends();
        for (int i = 0; i < ends.length; i++) if (t < ends[i]) return i;
        return ends.length - 1;
    }

    private Identifier texture(Minecraft mc, GifFrames.Result r, int i) {
        Identifier id = textures.get(i);
        if (id != null) return id;
        try {
            NativeImage img = NativeImage.read(new ByteArrayInputStream(r.frames().get(i).png()));
            id = Identifier.fromNamespaceAndPath("critclient", "subway/frame_" + i);
            mc.getTextureManager().register(id, Icons.texture(img));
            textures.put(i, id);
            return id;
        } catch (Exception e) {
            failed = true;
            CritClient.LOG.error("Subway gameplay: couldn't make a texture for frame {}", i, e);
            return null;
        }
    }

    private void release(Minecraft mc) {
        for (Identifier id : textures.values()) mc.getTextureManager().release(id);
        textures.clear();
    }

    private void startLoading() {
        if (loading) return;
        loading = true;
        Net.POOL.execute(() -> {
            try (InputStream in = SubwayModule.class.getResourceAsStream(RESOURCE)) {
                if (in == null) throw new java.io.FileNotFoundException(RESOURCE);
                gif = GifFrames.decode(in);
                CritClient.LOG.info("Subway gameplay loaded: {} frames", gif.frames().size());
            } catch (Throwable t) {
                failed = true;
                CritClient.LOG.error("Subway gameplay couldn't be loaded", t);
            }
        });
    }
}
