package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.MessageMode;
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
    void namesTheFileAfterTheMethodPathAndStatus() {
        String request = "POST /api/v1/users/42?token=abc HTTP/1.1\nHost: x\n";
        String response = "HTTP/1.1 200 OK\n";
        assertEquals(
            "POST-api-v1-users-42-200.png",
            ImageCapture.fileName(
                request,
                "https://shop.example.com:8443/api/v1/users/42?token=abc#top",
                response,
                MessageMode.BOTH
            ),
            "no host, port, query or fragment"
        );
        assertEquals(
            "POST-api-v1-users-42-200.png",
            ImageCapture.fileName(request, "/api/v1/users/42", response, MessageMode.BOTH)
        );
        assertEquals(
            "GET-200.png",
            ImageCapture.fileName("GET / HTTP/2", "https://x/", "HTTP/2 200", MessageMode.BOTH),
            "the root"
        );
        assertEquals(
            "GET-search.json.png",
            ImageCapture.fileName("GET /search.json HTTP/2", "https://x/search.json", null, MessageMode.BOTH),
            "no response, no status"
        );
        assertEquals(
            "GET-files-my-report-v2-404.png",
            ImageCapture.fileName(
                "GET /x HTTP/2",
                "https://x/files/my%20report%20(v2)/",
                "HTTP/2 404 Not Found",
                MessageMode.BOTH
            ),
            "decoded, and only characters safe on every file system"
        );
        assertEquals(
            "GET-reset-REDACTED-200.png",
            ImageCapture.fileName("GET /x HTTP/2", "https://x/reset/[REDACTED]", response, MessageMode.BOTH),
            "what the URL bar shows, redactions included"
        );
        assertEquals(
            "POST-api-v1-users-42-req-200.png",
            ImageCapture.fileName(request, "/api/v1/users/42", response, MessageMode.REQUEST),
            "only the request shown"
        );
        assertEquals(
            "POST-api-v1-users-42-resp-200.png",
            ImageCapture.fileName(request, "/api/v1/users/42", response, MessageMode.RESPONSE),
            "only the response shown"
        );
        assertEquals("GET-req.png", ImageCapture.fileName("GET / HTTP/2", "/", null, MessageMode.REQUEST));
        assertEquals("screenshot.png", ImageCapture.fileName("", "", null, MessageMode.BOTH));

        String longPath = "https://x/" + "segment/".repeat(20);
        String name = ImageCapture.fileName("GET /x HTTP/2", longPath, response, MessageMode.BOTH);
        assertTrue(name.length() <= "GET-".length() + 60 + "-200.png".length(), name);
        assertTrue(name.endsWith("-segment-200.png"), "cut between segments, the status kept: " + name);
    }

    @Test
    void numbersANameAlreadyTaken(@org.junit.jupiter.api.io.TempDir java.io.File directory) throws Exception {
        assertEquals("GET-200.png", ImageCapture.freeFile(directory, "GET-200.png").getName());
        assertTrue(new java.io.File(directory, "GET-200.png").createNewFile());
        assertTrue(new java.io.File(directory, "GET-200-2.png").createNewFile());
        assertEquals("GET-200-3.png", ImageCapture.freeFile(directory, "GET-200.png").getName());
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
