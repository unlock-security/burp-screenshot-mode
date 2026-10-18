package it.unlocksecurity.burp.screenshotmode.ui;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.FlavorMap;
import java.awt.datatransfer.SystemFlavorMap;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Renders the screenshot content to an image and exports it (port of {@code utils/screenshot.ts}). */
final class ImageCapture {

    /** Same pixel density as the Caido plugin's {@code domToPng(..., { scale: 2 })}. */
    static final double SCALE = 2;

    /**
     * PNG bytes under the platform's native PNG clipboard type. {@link DataFlavor#imageFlavor} can't be used: on
     * macOS the JDK always converts it to TIFF.
     */
    static final DataFlavor PNG_FLAVOR = pngFlavor();

    private ImageCapture() {}

    static BufferedImage render(ContentPanel content) {
        content.setCaptureMode(true);
        try {
            BufferedImage image = new BufferedImage(
                (int) Math.ceil(content.getWidth() * SCALE),
                (int) Math.ceil(content.getHeight() * SCALE),
                BufferedImage.TYPE_INT_RGB
            );
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.scale(SCALE, SCALE);
            content.printAll(g);
            g.dispose();
            return image;
        } finally {
            content.setCaptureMode(false);
        }
    }

    static void save(BufferedImage image, File file) throws IOException {
        if (!ImageIO.write(image, "png", file)) {
            throw new IOException("No PNG writer available");
        }
    }

    static void copyToClipboard(BufferedImage image) throws IOException {
        Toolkit.getDefaultToolkit()
            .getSystemClipboard()
            .setContents(new PngSelection(toPng(image)), null);
    }

    static byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", png)) {
            throw new IOException("No PNG writer available");
        }
        return png.toByteArray();
    }

    private static DataFlavor pngFlavor() {
        DataFlavor flavor = new DataFlavor("image/png;class=java.io.InputStream", "PNG image");
        FlavorMap flavorMap = SystemFlavorMap.getDefaultFlavorMap();
        if (flavorMap instanceof SystemFlavorMap systemFlavorMap) {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            boolean macOrWindows = os.startsWith("mac") || os.startsWith("windows");
            // X11 clipboards name formats by MIME type; macOS and Windows use a "PNG" format.
            systemFlavorMap.addUnencodedNativeForFlavor(flavor, macOrWindows ? "PNG" : "image/png");
        }
        return flavor;
    }

    private record PngSelection(byte[] png) implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] { PNG_FLAVOR };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return PNG_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return new ByteArrayInputStream(png);
        }
    }
}
