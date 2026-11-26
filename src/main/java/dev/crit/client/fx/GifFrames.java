package dev.crit.client.fx;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.w3c.dom.Node;

/**
 * Decodes an animated GIF into complete frames (each one a PNG) with their delays. GIFs usually store only the part of
 * the picture that changed, so this paints every frame onto a running canvas and honours each frame's disposal rule.
 * Has no Minecraft dependencies: it only turns bytes into bytes, so it can run on any thread.
 */
public final class GifFrames {
    static {
        System.setProperty("java.awt.headless", "true");
    }

    public record Frame(byte[] png, int delayMs) {}

    public record Result(int width, int height, List<Frame> frames, int[] ends, int totalMs) {}

    public static Result decode(InputStream in) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) throw new IOException("No GIF reader available");
        ImageReader reader = readers.next();
        try (MemoryCacheImageInputStream stream = new MemoryCacheImageInputStream(in)) {
            reader.setInput(stream, false, false);
            int count = reader.getNumImages(true);
            if (count <= 0) throw new IOException("The GIF has no frames");

            int cw = 0, ch = 0;
            IIOMetadata sm = reader.getStreamMetadata();
            if (sm != null) {
                IIOMetadataNode root = (IIOMetadataNode) sm.getAsTree("javax_imageio_gif_stream_1.0");
                Node lsd = child(root, "LogicalScreenDescriptor");
                if (lsd != null) {
                    cw = intAttr(lsd, "logicalScreenWidth", 0);
                    ch = intAttr(lsd, "logicalScreenHeight", 0);
                }
            }
            if (cw <= 0 || ch <= 0) {
                BufferedImage first = reader.read(0);
                cw = first.getWidth();
                ch = first.getHeight();
            }

            BufferedImage canvas = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_ARGB);
            List<Frame> frames = new ArrayList<>(count);
            int[] ends = new int[count];
            int total = 0;

            for (int i = 0; i < count; i++) {
                BufferedImage img = reader.read(i);
                IIOMetadataNode root = (IIOMetadataNode) reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                Node desc = child(root, "ImageDescriptor");
                Node gce = child(root, "GraphicControlExtension");
                int left = desc == null ? 0 : intAttr(desc, "imageLeftPosition", 0);
                int top = desc == null ? 0 : intAttr(desc, "imageTopPosition", 0);
                int iw = img.getWidth(), ih = img.getHeight();
                String disposal = gce == null ? "none" : ((IIOMetadataNode) gce).getAttribute("disposalMethod");
                int delay = gce == null ? 10 : intAttr(gce, "delayTime", 10) * 10;
                if (delay < 20) delay = 100; // browsers treat tiny delays as 100 ms; so do we

                BufferedImage before = null;
                if ("restoreToPrevious".equals(disposal)) before = copy(canvas);

                Graphics2D g = canvas.createGraphics();
                g.setComposite(AlphaComposite.SrcOver);
                g.drawImage(img, left, top, null);
                g.dispose();

                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ImageIO.write(canvas, "png", bos);
                frames.add(new Frame(bos.toByteArray(), delay));
                total += delay;
                ends[i] = total;

                if ("restoreToBackgroundColor".equals(disposal)) {
                    Graphics2D c = canvas.createGraphics();
                    c.setComposite(AlphaComposite.Clear);
                    c.fillRect(left, top, iw, ih);
                    c.dispose();
                } else if (before != null) {
                    canvas = before;
                }
            }
            return new Result(cw, ch, frames, ends, total);
        } finally {
            reader.dispose();
        }
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static Node child(Node parent, String name) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (name.equals(n.getNodeName())) return n;
        }
        return null;
    }

    private static int intAttr(Node n, String name, int fallback) {
        Node a = n.getAttributes().getNamedItem(name);
        if (a == null) return fallback;
        try {
            return Integer.parseInt(a.getNodeValue());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private GifFrames() {}
}
