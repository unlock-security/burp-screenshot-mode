package it.unlocksecurity.burp.screenshotmode.render;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Turns raw HTTP message bytes into the text shown in the screenshot. */
public final class MessageText {

    /** Larger messages are truncated: nobody screenshots megabytes and Swing text layout gets slow. */
    static final int MAX_CHARS = 300_000;

    private MessageText() {}

    public static String fromBytes(byte[] bytes) {
        return truncate(normalizeNewlines(decode(bytes)));
    }

    /** Strict UTF-8 with an ISO-8859-1 fallback, so binary bodies keep one character per byte. */
    static String decode(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, StandardCharsets.ISO_8859_1);
        }
    }

    /** Line breaks become {@code \n}, like CodeMirror's document model the Caido rules are matched against. */
    static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    static String truncate(String text) {
        if (text.length() <= MAX_CHARS) {
            return text;
        }
        return text.substring(0, MAX_CHARS) + "\n… [truncated " + (text.length() - MAX_CHARS) + " characters]";
    }
}
