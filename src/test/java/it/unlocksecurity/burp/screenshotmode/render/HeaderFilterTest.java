package it.unlocksecurity.burp.screenshotmode.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Port of the Caido plugin's headers.spec.ts. */
class HeaderFilterTest {

    private static final String RAW_REQUEST = String.join(
        "\n",
        "GET / HTTP/1.1",
        "Host: example.com",
        "Accept: text/html",
        "Content-Type: application/json",
        "X-Custom: value",
        "",
        "body content"
    );

    @Test
    void returnsRawUnchangedWhenNoRules() {
        assertEquals(RAW_REQUEST, HeaderFilter.filter(RAW_REQUEST, List.of()));
    }

    @Test
    void hidesASingleHeader() {
        String result = HeaderFilter.filter(RAW_REQUEST, List.of("Accept"));
        assertTrue(result.contains("Host: example.com"));
        assertFalse(result.contains("Accept: text/html"));
        assertTrue(result.contains("Content-Type: application/json"));
    }

    @Test
    void hidesMultipleHeaders() {
        String result = HeaderFilter.filter(RAW_REQUEST, List.of("Accept", "X-Custom"));
        assertFalse(result.contains("Accept: text/html"));
        assertFalse(result.contains("X-Custom: value"));
        assertTrue(result.contains("Host: example.com"));
    }

    @Test
    void isCaseInsensitive() {
        String result = HeaderFilter.filter(RAW_REQUEST, List.of("accept", "HOST"));
        assertFalse(result.contains("Accept: text/html"));
        assertFalse(result.contains("Host: example.com"));
        assertTrue(result.contains("Content-Type: application/json"));
    }

    @Test
    void preservesTheRequestLine() {
        assertTrue(HeaderFilter.filter(RAW_REQUEST, List.of("Host", "Accept")).contains("GET / HTTP/1.1"));
    }

    @Test
    void preservesTheBody() {
        assertTrue(HeaderFilter.filter(RAW_REQUEST, List.of("Host", "Accept")).contains("body content"));
    }

    @Test
    void showsAHeaderWithBangPrefixEvenWithoutHideRules() {
        String result = HeaderFilter.filter(RAW_REQUEST, List.of("!Accept"));
        assertTrue(result.contains("Accept: text/html"));
        assertTrue(result.contains("Host: example.com"));
    }

    @Test
    void bangPrefixOverridesAHideRuleForTheSameHeader() {
        assertTrue(HeaderFilter.filter(RAW_REQUEST, List.of("Accept", "!Accept")).contains("Accept: text/html"));
    }

    @Test
    void hidesSomeAndShowsOthersWithMixedRules() {
        String result = HeaderFilter.filter(RAW_REQUEST, List.of("Accept", "Content-Type", "!Content-Type"));
        assertFalse(result.contains("Accept: text/html"));
        assertTrue(result.contains("Content-Type: application/json"));
        assertTrue(result.contains("Host: example.com"));
    }

    @Test
    void doesNotAffectBodyContentEvenIfItLooksLikeAHeader() {
        String raw = String.join("\n", "GET / HTTP/1.1", "Host: example.com", "", "Accept: should-stay");
        String result = HeaderFilter.filter(raw, List.of("Accept"));
        assertTrue(result.contains("Accept: should-stay"));
    }

    @Test
    void keepsLineStructureExactly() {
        String result = HeaderFilter.filter(RAW_REQUEST, List.of("Accept", "X-Custom"));
        assertEquals("GET / HTTP/1.1\nHost: example.com\nContent-Type: application/json\n\nbody content", result);
    }

    @Test
    void normalizesCrlfFromBurpMessages() {
        byte[] raw = "GET / HTTP/1.1\r\nAccept: */*\r\n\r\nline1\r\nline2".getBytes();
        String text = MessageText.fromBytes(raw);
        assertEquals("GET / HTTP/1.1\n\nline1\nline2", HeaderFilter.filter(text, List.of("Accept")));
    }
}
