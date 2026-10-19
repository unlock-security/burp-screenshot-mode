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

    @Test
    void decodesOnlyTheHeadOfVeryLargeMessages() {
        byte[] head = "é".repeat(MessageText.MAX_BYTES).getBytes(StandardCharsets.UTF_8);
        int length = 50 << 20;
        String text = MessageText.fromBytes(head, length);
        // Still valid UTF-8 although the head was cut in the middle of a character.
        assertTrue(text.startsWith("éé"));
        assertTrue(
            text.endsWith("\n… [truncated " + (length - 2 * MessageText.MAX_CHARS) + " bytes]"),
            text.substring(text.length() - 50)
        );
    }

    @Test
    void countsTruncatedBytesOfBinaryAndCrlfText() {
        byte[] binary = new byte[MessageText.MAX_BYTES + 7];
        binary[0] = (byte) 0xff;
        assertTrue(
            MessageText.fromBytes(binary).endsWith("[truncated " + (binary.length - MessageText.MAX_CHARS) + " bytes]")
        );
        byte[] crlf = "a\r\n".repeat(MessageText.MAX_BYTES).getBytes(StandardCharsets.UTF_8);
        String text = MessageText.fromBytes(crlf);
        assertTrue(text.startsWith("a\na\n"));
        // MAX_CHARS characters shown, each "\n" standing for two bytes.
        assertTrue(text.endsWith("[truncated " + (crlf.length - (MessageText.MAX_CHARS * 3) / 2) + " bytes]"));
    }
}
