package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.util.Arrays;
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
}
