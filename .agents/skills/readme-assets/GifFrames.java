import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Saves frames of a GIF as PNG, as a viewer shows them (each frame drawn over the previous ones), and prints the frame
 * count and length. Usage: java GifFrames.java <gif> <output dir> [frame number...]
 */
public class GifFrames {

    public static void main(String[] args) throws Exception {
        ImageReader reader = ImageIO.getImageReadersByFormatName("gif").next();
        reader.setInput(ImageIO.createImageInputStream(new File(args[0])));
        Element screen = child(
            reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0"),
            "LogicalScreenDescriptor"
        );
        int width = Integer.parseInt(screen.getAttribute("logicalScreenWidth"));
        int height = Integer.parseInt(screen.getAttribute("logicalScreenHeight"));
        File out = new File(args[1]);
        out.mkdirs();
        Set<Integer> wanted = new HashSet<>();
        for (int i = 2; i < args.length; i++) {
            wanted.add(Integer.parseInt(args[i]));
        }
        BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        int frames = reader.getNumImages(true);
        int centiseconds = 0;
        for (int i = 0; i < frames; i++) {
            Node tree = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
            Element descriptor = child(tree, "ImageDescriptor");
            Element control = child(tree, "GraphicControlExtension");
            if (control != null) {
                centiseconds += Integer.parseInt(control.getAttribute("delayTime"));
            }
            g.drawImage(
                reader.read(i),
                Integer.parseInt(descriptor.getAttribute("imageLeftPosition")),
                Integer.parseInt(descriptor.getAttribute("imageTopPosition")),
                null
            );
            if (wanted.contains(i)) {
                File file = new File(out, "f" + i + ".png");
                ImageIO.write(canvas, "png", file);
                System.out.println(file.getAbsolutePath());
            }
        }
        System.out.println(width + " × " + height + ", " + frames + " frames, " + centiseconds / 100.0 + " s");
    }

    private static Element child(Node node, String name) {
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeName().equals(name)) {
                return (Element) child;
            }
        }
        return null;
    }
}
