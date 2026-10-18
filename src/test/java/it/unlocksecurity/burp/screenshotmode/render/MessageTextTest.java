package it.unlocksecurity.burp.screenshotmode.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MessageTextTest {

    @Test
    void decodesUtf8() {
        assertEquals("caffè ☕", MessageText.fromBytes("caffè ☕".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void fallsBackToLatin1ForBinary() {
        byte[] binary = { (byte) 0xff, (byte) 0xfe, 0x41 };
        assertEquals("ÿþA", MessageText.fromBytes(binary));
    }

    @Test
    void normalizesLoneCarriageReturns() {
        assertEquals("a\nb\nc", MessageText.fromBytes("a\r\nb\rc".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void truncatesHugeMessages() {
        String text = MessageText.fromBytes("x".repeat(MessageText.MAX_CHARS + 10).getBytes(StandardCharsets.UTF_8));
        assertTrue(text.endsWith("[truncated 10 characters]"));
    }
}
