package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class ImageCaptureTest {

    private static final byte[] PNG_SIGNATURE = { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n' };

    @Test
    void clipboardDataIsPng() throws Exception {
        byte[] png = ImageCapture.toPng(new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB));
        assertArrayEquals(PNG_SIGNATURE, Arrays.copyOf(png, PNG_SIGNATURE.length));
        assertEquals(
            "image/png",
            ImageCapture.PNG_FLAVOR.getPrimaryType() + "/" + ImageCapture.PNG_FLAVOR.getSubType()
        );
    }

    @Test
    void sizeLabelReportsThePngPixels() {
        Locale before = Locale.getDefault();
        Locale.setDefault(Locale.US);
        try {
            assertEquals("2,400 × 1,522 px PNG (2×)", ImageCapture.sizeLabel(1200, 761));
        } finally {
            Locale.setDefault(before);
        }
    }

    @Test
    void refusesImagesTooLargeToBuild() {
        assertFalse(ImageCapture.tooLarge(1200, 10_000), "2,400 × 20,000 px: 48 megapixels");
        assertTrue(ImageCapture.tooLarge(1200, 10_500), "2,400 × 21,000 px: 50.4 megapixels");
    }

    @Test
    void notesAScreenshotTallerThanOnePage() {
        assertNull(ImageCapture.pageNote(WidthMode.A4, 842), "exactly one page");
        assertEquals("Taller than one A4 page", ImageCapture.pageNote(WidthMode.A4, 843));
        assertNull(ImageCapture.pageNote(WidthMode.LETTER, 792));
        assertEquals("Taller than one Letter page", ImageCapture.pageNote(WidthMode.LETTER, 793));
        for (WidthMode other : new WidthMode[] { WidthMode.FULL, WidthMode.CONTENT, WidthMode.PIXEL }) {
            assertNull(ImageCapture.pageNote(other, 5000), "no page for " + other);
        }
    }
}
