package it.unlocksecurity.burp.screenshotmode.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class SampleMessageTest {

    private static final SampleMessage SAMPLE = SampleMessage.bundled();

    @Test
    void readsTheBundledSample() {
        assertTrue(SAMPLE.url().startsWith("https://shop.example.com/"));
        assertTrue(SAMPLE.request().startsWith("POST /api/v1/login?"));
        assertTrue(SAMPLE.response().startsWith("HTTP/2 200 OK\n"));
        assertFalse(SAMPLE.request().contains("\r"));
        assertTrue(SAMPLE.requestTime() != null, "so the editor preview shows a timestamp");
    }

    @Test
    void declaresTheRightBodyLengths() {
        for (String message : new String[] { SAMPLE.request(), SAMPLE.response() }) {
            String body = message.substring(message.indexOf("\n\n") + 2);
            int length = body.getBytes(StandardCharsets.UTF_8).length;
            assertTrue(message.contains("\nContent-Length: " + length + "\n"), message);
        }
    }

    @Test
    void rejectsAnotherFormat() {
        assertThrows(IllegalArgumentException.class, () -> SampleMessage.parse("{\"format\": \"other\"}"));
        assertEquals(SAMPLE, SampleMessage.bundled());
    }
}
