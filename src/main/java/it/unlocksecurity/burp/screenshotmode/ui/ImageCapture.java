package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.MessageMode;
import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Renders the screenshot content to an image and exports it (port of {@code utils/screenshot.ts}). */
final class ImageCapture {

    /**
     * PNG bytes under the platform's native PNG clipboard type. {@link DataFlavor#imageFlavor} can't be used: on
     * macOS the JDK always converts it to TIFF.
     */
    static final DataFlavor PNG_FLAVOR = pngFlavor();

    private ImageCapture() {}

    /** @param scale image pixels per pixel on screen */
    static BufferedImage render(ContentPanel content, int scale) {
        content.setCaptureMode(true);
        try {
            BufferedImage image = new BufferedImage(
                pixels(content.getWidth(), scale),
                pixels(content.getHeight(), scale),
                BufferedImage.TYPE_INT_RGB
            );
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.scale(scale, scale);
            content.printAll(g);
            g.dispose();
            return image;
        } finally {
            content.setCaptureMode(false);
        }
    }

    /** The longest path in a file name, so a long URL still gives a name that file managers show whole. */
    private static final int MAX_PATH = 60;

    /**
     * A file name that says what the screenshot shows, such as "POST-api-v1-users-42-200.png": the request method, the
     * path without host and query, "req" or "resp" when only one message is shown, and the response status. {@code
     * url} is the URL bar's text as it may leave the screenshot, so what a rule redacts there stays out of the name too.
     */
    static String fileName(String request, String url, String response, MessageMode messages) {
        List<String> parts = new ArrayList<>();
        parts.add(firstWords(request, 1));
        String path = url.substring(ContentPanel.pathStart(url));
        int end = indexOfAny(path, "?#");
        String pathPart = safe(String.join("-", decoded(path.substring(0, end < 0 ? path.length() : end).split("/"))));
        if (pathPart.length() > MAX_PATH) {
            int cut = pathPart.lastIndexOf('-', MAX_PATH);
            pathPart = pathPart.substring(0, cut > 0 ? cut : MAX_PATH);
        }
        parts.add(pathPart);
        switch (messages) {
            case REQUEST -> parts.add("req");
            case RESPONSE -> parts.add("resp");
            case BOTH -> {
            }
        }
        String status = firstWords(response, 2);
        if (status.matches("\\S+ \\d{3}")) {
            parts.add(status.substring(status.indexOf(' ') + 1));
        }
        String name = safe(String.join("-", parts));
        return (name.isEmpty() ? "screenshot" : name) + ".png";
    }

    /** {@code name} in {@code directory}, numbered "name-2.png", "name-3.png", ... when a file already has it. */
    static File freeFile(File directory, String name) {
        File file = new File(directory, name);
        String base = name.substring(0, name.lastIndexOf('.'));
        for (int n = 2; file.exists(); n++) {
            file = new File(directory, base + "-" + n + ".png");
        }
        return file;
    }

    /** The first {@code count} space-separated words of the first line, or "" if there is no text. */
    private static String firstWords(String text, int count) {
        if (text == null) {
            return "";
        }
        int lineEnd = text.indexOf('\n');
        String[] words = (lineEnd < 0 ? text : text.substring(0, lineEnd)).strip().split(" ", count + 1);
        return String.join(" ", Arrays.asList(words).subList(0, Math.min(count, words.length)));
    }

    private static int indexOfAny(String text, String chars) {
        for (int i = 0; i < text.length(); i++) {
            if (chars.indexOf(text.charAt(i)) >= 0) {
                return i;
            }
        }
        return -1;
    }

    /** The path segments with their %-escapes decoded, so "%20" becomes a separator rather than "20". */
    private static List<String> decoded(String[] segments) {
        List<String> decoded = new ArrayList<>();
        for (String segment : segments) {
            try {
                decoded.add(URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                decoded.add(segment);
            }
        }
        return decoded;
    }

    /** Only letters, digits, dots and underscores, with one "-" between runs of them: safe on every file system. */
    private static String safe(String name) {
        return name.replaceAll("[^A-Za-z0-9._]+", "-").replaceAll("^[-.]+|-+$", "");
    }

    /** The image pixels for {@code size} on screen. */
    static int pixels(int size, int scale) {
        return size * scale;
    }

    /** The size of the PNG for content of {@code width} × {@code height} on screen, such as "2,400 × 1,520 px PNG (2×)". */
    static String sizeLabel(int width, int height, int scale) {
        NumberFormat number = NumberFormat.getIntegerInstance();
        return (
            number.format(pixels(width, scale)) +
            " × " +
            number.format(pixels(height, scale)) +
            " px PNG (" +
            scale +
            "×)"
        );
    }

    /** The most pixels an image may have: beyond, building it could run Burp out of memory. */
    static final long MAX_PIXELS = 50_000_000;

    /** Whether content of {@code width} × {@code height} on screen makes an image too large to build. */
    static boolean tooLarge(int width, int height, int scale) {
        return (long) pixels(width, scale) * pixels(height, scale) > MAX_PIXELS;
    }

    /** A note when content {@code height} tall won't fit on one page of the paper width chosen, or {@code null}. */
    static String pageNote(WidthMode width, int height) {
        return width.page() != null && height > width.pageHeight() ? "Taller than one " + width.page() + " page" : null;
    }

    static void save(BufferedImage image, File file) throws IOException {
        if (!ImageIO.write(image, "png", file)) {
            throw new IOException("No PNG writer available");
        }
    }

    /** Puts a PNG made by {@link #toPng} on the clipboard; throws {@link IllegalStateException} if it's unavailable. */
    static void copyToClipboard(byte[] png) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new PngSelection(png), null);
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
