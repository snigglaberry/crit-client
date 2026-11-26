package dev.crit.client.ui;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;

/** One analytic quad per shape. The GPU evaluates coverage at framebuffer resolution, not at GUI scale. */
final class RoundedRect {
    private static final Identifier SHADER = Identifier.fromNamespaceAndPath("critclient", "core/rounded_rect");
    private static final VertexFormat FORMAT = VertexFormat.builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("Color", GpuFormat.RGBA8_UNORM)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT) // local position, interpolated across the quad
            .addAttribute("UV1", GpuFormat.RG16_SINT) // width and height
            .addAttribute("UV2", GpuFormat.RG16_SINT) // the outline color, packed into two shorts
            .addAttribute("LineWidth", GpuFormat.R32_FLOAT) // corner radius (negative for a cutout)
            .build();
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("critclient", "pipeline/rounded_rect"))
            .withVertexShader(SHADER).withFragmentShader(SHADER)
            .withVertexBinding(0, FORMAT)
            .build();

    static void draw(GuiGraphicsExtractor g, int x, int y, int w, int h, float radius, int top, int bottom, int edge) {
        if (w <= 0 || h <= 0 || ((top | bottom | edge) >>> 24) == 0) return;
        float r = Math.copySign(Math.min(Math.abs(radius), Math.min(w, h) * 0.5f), radius);
        // Leave room for the partial pixels just outside the analytic edge, unless we're filling a cutout.
        int pad = r < 0 ? 0 : 1;
        Matrix3x2f pose = new Matrix3x2f(g.pose());
        ScreenRectangle bounds = new ScreenRectangle(x - pad, y - pad, w + 2 * pad, h + 2 * pad).transformMaxBounds(pose);
        ScreenRectangle scissor = g.scissorStack.peek();
        if (scissor != null) bounds = scissor.intersection(bounds);
        if (bounds != null) g.guiRenderState.addGuiElement(new Quad(pose, x, y, w, h, pad, r, top, bottom, edge, scissor, bounds));
    }

    private record Quad(Matrix3x2f pose, int x, int y, int w, int h, int pad, float r, int top, int bottom, int edge,
                        ScreenRectangle scissorArea, ScreenRectangle bounds) implements GuiElementRenderState {
        @Override public RenderPipeline pipeline() { return PIPELINE; }
        @Override public TextureSetup textureSetup() { return TextureSetup.noTexture(); }

        @Override
        public void buildVertices(VertexConsumer vertices) {
            vertex(vertices, -pad, -pad, top);
            vertex(vertices, -pad, h + pad, bottom);
            vertex(vertices, w + pad, h + pad, bottom);
            vertex(vertices, w + pad, -pad, top);
        }

        private void vertex(VertexConsumer vertices, float u, float v, int color) {
            vertices.addVertexWith2DPose(pose, x + u, y + v).setColor(color).setUv(u, v)
                    .setUv1(w, h).setUv2(edge >>> 16, edge & 0xFFFF).setLineWidth(r);
        }
    }

    private RoundedRect() {}
}
