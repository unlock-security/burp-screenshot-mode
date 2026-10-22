package it.unlocksecurity.burp.screenshotmode.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

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

    private static Predicate<String> hiding(String... names) {
        Set<String> hidden = Set.of(names);
        return name -> hidden.contains(name.toLowerCase(Locale.ROOT));
    }

    @Test
    void returnsTheTextUnchangedWhenNothingIsHidden() {
        assertEquals(RAW_REQUEST, HeaderFilter.filter(RAW_REQUEST, name -> false));
    }

    @Test
    void hidesTheHeadersThePredicateAccepts() {
        String result = HeaderFilter.filter(RAW_REQUEST, hiding("accept", "x-custom"));
        assertFalse(result.contains("Accept:"));
        assertFalse(result.contains("X-Custom:"));
        assertTrue(result.contains("Host: example.com"));
        assertTrue(result.contains("Content-Type: application/json"));
    }

    @Test
    void neverHidesTheStartLineOrTheBody() {
        String text = "GET / HTTP/1.1\nAccept: */*\n\nAccept: not a header";
        assertEquals(
            "GET / HTTP/1.1\n\nAccept: not a header",
            HeaderFilter.filter(text, hiding("accept", "get / http/1.1"))
        );
    }

    @Test
    void keepsLinesWithoutAName() {
        assertEquals("GET / HTTP/1.1\n: odd", HeaderFilter.filter("GET / HTTP/1.1\n: odd", name -> true));
    }

    @Test
    void listsHeaderNamesOnceInOrder() {
        String text = "HTTP/2 200\nset-cookie: a=1\ncontent-type: text/html\nSet-Cookie: b=2\n\nx: body";
        assertEquals(List.of("set-cookie", "content-type"), HeaderFilter.names(text));
    }

    @Test
    void listsNoNamesWithoutHeaders() {
        assertEquals(List.of(), HeaderFilter.names(""));
        assertEquals(List.of(), HeaderFilter.names("GET / HTTP/1.1"));
        assertEquals(List.of(), HeaderFilter.names("GET / HTTP/1.1\n\nbody: x"));
    }
}
