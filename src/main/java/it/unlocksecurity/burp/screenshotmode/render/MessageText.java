package it.unlocksecurity.burp.screenshotmode.render;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Turns raw HTTP message bytes into the text shown in the screenshot. */
public final class MessageText {

    /** Larger messages are truncated: nobody screenshots megabytes and Swing text layout gets slow. */
    static final int MAX_CHARS = 300_000;

    /**
     * Enough bytes for {@link #MAX_CHARS} characters in any encoding (UTF-8 needs at most 3 bytes per char, a CRLF
     * 2 bytes per line break). Callers can pass just this prefix of a larger message to {@link #fromBytes(byte[],
     * int)}, so a huge body is never copied or decoded in full.
     */
    public static final int MAX_BYTES = MAX_CHARS * 4;

    private MessageText() {}

    public static String fromBytes(byte[] bytes) {
        return fromBytes(bytes, bytes.length);
    }

    /**
     * @param head   the message bytes, or only their first {@link #MAX_BYTES} or more
     * @param length length of the whole message
     */
    public static String fromBytes(byte[] head, int length) {
        if (length <= MAX_BYTES && head.length == length) {
            return truncate(normalizeNewlines(decode(head, head.length)));
        }
        int end = utf8Boundary(head, Math.min(head.length, MAX_BYTES));
        String text = strictUtf8(head, end);
        boolean utf8 = text != null;
        if (!utf8) {
            text = latin1(head, end);
        }
        int shown = rawLength(text, MAX_CHARS);
        int shownBytes = utf8 ? text.substring(0, shown).getBytes(StandardCharsets.UTF_8).length : shown;
        return normalizeNewlines(text.substring(0, shown)) + "\n… [truncated " + (length - shownBytes) + " bytes]";
    }

    /** Strict UTF-8 with an ISO-8859-1 fallback, so binary bodies keep one character per byte. */
    static String decode(byte[] bytes, int length) {
        String text = strictUtf8(bytes, length);
        return text != null ? text : latin1(bytes, length);
    }

    /** Line breaks become {@code \n}, like CodeMirror's document model the Caido rules are matched against. */
    static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    static String truncate(String text) {
        if (text.length() <= MAX_CHARS) {
            return text;
        }
        return text.substring(0, MAX_CHARS) + truncationNote(text.length() - MAX_CHARS);
    }

    static String truncationNote(long characters) {
        return "\n… [truncated " + characters + " characters]";
    }

    /** @return the text, or {@code null} when the bytes are not valid UTF-8 */
    private static String strictUtf8(byte[] bytes, int length) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, 0, length))
                .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static String latin1(byte[] bytes, int length) {
        return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
    }

    /** Moves {@code end} back to the start of a UTF-8 sequence, so a cut doesn't make valid UTF-8 look invalid. */
    private static int utf8Boundary(byte[] bytes, int end) {
        int boundary = end;
        while (boundary > 0 && boundary < bytes.length && end - boundary < 3 && (bytes[boundary] & 0xC0) == 0x80) {
            boundary--;
        }
        return boundary;
    }

    /** @return how many characters of {@code text} make up its first {@code chars} once line breaks are normalized */
    private static int rawLength(String text, int chars) {
        int raw = 0;
        for (int count = 0; raw < text.length() && count < chars; count++) {
            raw += text.startsWith("\r\n", raw) ? 2 : 1;
        }
        return raw;
    }
}
