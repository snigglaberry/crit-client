package dev.crit.client.fx;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import dev.crit.client.CritClient;
import dev.crit.client.Icons;
import dev.crit.client.modules.Modules;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * Motion blur built only from the game's own blit shader and plain blending (no custom shader). With s = blur
 * strength, the history texture H always holds (previous output * s), so each frame is:
 *
 * 1. darken the new frame to N * (1 - s)   (draw a black 1x1 texture of alpha s over it),
 * 2. add H on top                          (additive blend)  ->  out = N * (1 - s) + previous * s,
 * 3. draw out into H (a plain draw, no texture copy), then darken H to out * s (black texture of alpha 1 - s).
 */
public final class MotionBlur {
    private static final Map<Integer, AbstractTexture> STRENGTH_TEXTURES = new HashMap<>();
    private static final Map<String, RenderPipeline> PIPELINES = new HashMap<>();
    private static GpuFormat pipelineFormat;
    private static int pipelineCounter;

    private static GpuTexture history;
    private static GpuTextureView historyView;
    private static int width, height;
    private static boolean historyValid;
    private static boolean failed;
    private static boolean logged;

    /** Called right after the world has been drawn into the main render target. */
    public static void apply(Minecraft mc) {
        if (failed || !Modules.MOTION_BLUR.enabled() || mc.level == null) {
            historyValid = false;
            return;
        }
        try {
            run(mc);
        } catch (Throwable t) {
            // never let a rendering problem take the game down: switch the effect off for this session instead
            failed = true;
            historyValid = false;
            CritClient.LOG.error("Motion blur failed and was turned off for this session", t);
        }
    }

    private static void run(Minecraft mc) throws Exception {
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        GpuTexture src = target.getColorTexture();
        GpuTextureView srcView = target.getColorTextureView();
        if (src == null || srcView == null) return;

        int w = src.getWidth(0), h = src.getHeight(0);
        GpuDevice device = RenderSystem.getDevice();
        if (history == null || w != width || h != height) rebuild(device, src.getFormat(), w, h);

        int step = Math.max(1, Math.min(19, (int) Math.round(Modules.MOTION_BLUR.strength() * 20.0)));
        AbstractTexture strength = strengthTexture(step);
        AbstractTexture inverse = strengthTexture(20 - step);
        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        CommandEncoder encoder = device.createCommandEncoder();

        if (!logged) {
            logged = true;
            CritClient.LOG.info("Motion blur running: {}x{} format {}", w, h, src.getFormat());
        }

        if (historyValid) {
            // 1. new frame -> N * (1 - s)
            draw(encoder, srcView, "darken frame", pipeline("blend", Optional.of(BlendFunction.TRANSLUCENT), src.getFormat()),
                    strength.getTextureView(), sampler);
            // 2. + history (already multiplied by s)
            draw(encoder, srcView, "add history", pipeline("add", Optional.of(BlendFunction.ADDITIVE), src.getFormat()),
                    historyView, sampler);
        }

        // 3. remember this frame (drawn, not copied), scaled by s for the next one
        draw(encoder, historyView, "store frame", pipeline("replace", Optional.empty(), src.getFormat()), srcView, sampler);
        draw(encoder, historyView, "scale history", pipeline("blend", Optional.of(BlendFunction.TRANSLUCENT), src.getFormat()),
                inverse.getTextureView(), sampler);
        historyValid = true;
    }

    private static void draw(CommandEncoder encoder, GpuTextureView target, String label, RenderPipeline pipeline,
                             GpuTextureView texture, GpuSampler sampler) {
        try (RenderPass pass = encoder.createRenderPass(() -> "critclient motion blur " + label, target, Optional.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture("InSampler", texture, sampler);
            pass.draw(3, 1, 0, 0);
        }
    }

    /** The game's blit shader with the given blending, writing all channels of a target of the given format. */
    private static RenderPipeline pipeline(String name, Optional<BlendFunction> blend, GpuFormat format) {
        if (pipelineFormat != format) {
            PIPELINES.clear();
            pipelineFormat = format;
        }
        return PIPELINES.computeIfAbsent(name, n -> RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("critclient", "pipeline/motion_blur_" + n + "_" + (pipelineCounter++)))
                .withVertexShader("core/screenquad")
                .withFragmentShader("core/blit_screen")
                .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
                .withColorTargetState(new ColorTargetState(blend, format, ColorTargetState.WRITE_ALL))
                .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
                .build()));
    }

    /** A 1x1 texture whose alpha is the blur strength (in 5% steps, 0..20). */
    private static AbstractTexture strengthTexture(int step) throws Exception {
        AbstractTexture cached = STRENGTH_TEXTURES.get(step);
        if (cached != null) return cached;
        BufferedImage px = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        px.setRGB(0, 0, Math.round(step * 255f / 20f) << 24);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(px, "png", bos);
        AbstractTexture tex = Icons.texture(NativeImage.read(new ByteArrayInputStream(bos.toByteArray())));
        STRENGTH_TEXTURES.put(step, tex);
        return tex;
    }

    private static void rebuild(GpuDevice device, GpuFormat format, int w, int h) {
        if (historyView != null) historyView.close();
        if (history != null) history.close();
        int usage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_RENDER_ATTACHMENT;
        history = device.createTexture("critclient motion blur history", usage, format, w, h, 1, 1);
        historyView = device.createTextureView(history);
        width = w;
        height = h;
        historyValid = false;
    }

    private MotionBlur() {}
}
