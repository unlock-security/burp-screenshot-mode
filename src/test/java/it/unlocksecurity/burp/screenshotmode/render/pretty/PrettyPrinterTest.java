package it.unlocksecurity.burp.screenshotmode.render.pretty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.render.MessageText;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class PrettyPrinterTest {

    private static final String JSON_HEADERS = "HTTP/2 200 OK\nContent-Type: application/json\n\n";

    private static String body(String contentType, String body) {
        return PrettyPrinter.format("HTTP/1.1 200 OK\nContent-Type: " + contentType + "\n\n" + body).split(
            "\n\n",
            2
        )[1];
    }

    @Test
    void indentsJsonKeepingTokensAndInlineSpacing() {
        String body = "{\"user\":{\"id\":1608, \"tags\":[\"a,b\",\"c\\\"}\"],\"empty\":{},\"none\":[ ]},\"ok\": true}";
        assertEquals(
            JSON_HEADERS +
                """
                {
                  "user":{
                    "id":1608,
                    "tags":[
                      "a,b",
                      "c\\"}"
                    ],
                    "empty":{},
                    "none":[]
                  },
                  "ok": true
                }""",
            PrettyPrinter.format(JSON_HEADERS + body)
        );
    }

    @Test
    void keepsJsonEscapesAndNumbersAsSent() {
        assertEquals("[\n  \"\\u003c\\/a>\",\n  1.50e+3\n]", JsonFormatter.format("[\"\\u003c\\/a>\",1.50e+3]"));
    }

    @Test
    void reformatsAlreadyIndentedJson() {
        String pretty = PrettyPrinter.format(JSON_HEADERS + "{\"a\":[1,2]}");
        String reindented = PrettyPrinter.format(JSON_HEADERS + "{\n\t\"a\" : [\n\t\t1,\n\t\t2\n\t]\n}\n");
        assertEquals(pretty.replace("\"a\":", "\"a\" : "), reindented);
    }

    @Test
    void leavesInvalidOrScalarJsonUntouched() {
        assertNull(JsonFormatter.format("{\"a\":1"));
        assertNull(JsonFormatter.format("{\"a\":1,}"));
        assertNull(JsonFormatter.format("[section]"));
        assertNull(JsonFormatter.format("{} {}"));
        assertNull(JsonFormatter.format("42"));
        String truncated = JSON_HEADERS + "{\"a\":\"xyz\n… [truncated 5 characters]";
        assertEquals(truncated, PrettyPrinter.format(truncated));
    }

    @Test
    void sniffsJsonWithoutContentType() {
        assertEquals("POST / HTTP/1.1\nHost: x\n\n[\n  1\n]", PrettyPrinter.format("POST / HTTP/1.1\nHost: x\n\n[1]"));
    }

    @Test
    void sniffsTheBodyWhenTheContentTypeDoesNotMatchIt() {
        assertEquals("{\n  \"a\":1\n}", body("text/html; charset=utf-8", "{\"a\":1}"));
        assertEquals("<a>\n  <b/>\n</a>", body("text/plain", "<a><b/></a>"));
    }

    @Test
    void picksTheFormatFromTheContentType() {
        assertEquals("<svg>\n  <g/>\n</svg>", body("image/svg+xml", "<svg><g/></svg>"));
        assertEquals("<feed>\n  <id>1</id>\n</feed>", body("application/atom+xml", "<feed><id>1</id></feed>"));
        assertEquals("a{\n  b:c\n}", body("text/css; charset=utf-8", "a{b:c}"));
        assertEquals("f();\ng();", body("application/javascript", "f();g();"));
        assertEquals("f();\ng();", body("text/javascript", "f();g();"));
        assertEquals("{\n  \"a\":1\n}", body("application/problem+json", "{\"a\":1}"));
        // HTML served as XML still gets HTML rules (void elements).
        assertEquals("<!doctype html>\n<p>\n  <br>\n</p>", body("application/xml", "<!doctype html><p><br></p>"));
    }

    @Test
    void leavesHeadersAndOtherBodiesUntouched() {
        String form = "POST / HTTP/1.1\nContent-Type: application/x-www-form-urlencoded\n\na={\"b\":1}";
        assertEquals(form, PrettyPrinter.format(form));
        String plain = "HTTP/1.1 200 OK\nContent-Type: text/plain\n\nf();g();";
        assertEquals(plain, PrettyPrinter.format(plain));
        assertEquals("GET / HTTP/1.1\nHost: x", PrettyPrinter.format("GET / HTTP/1.1\nHost: x"));
        assertEquals("GET / HTTP/1.1\nHost: x\n\n", PrettyPrinter.format("GET / HTTP/1.1\nHost: x\n\n"));
    }

    @Test
    void truncatesOutputGrownPastTheLimit() {
        // Every "<a>" goes on its own line, ever more indented: the output would be quadratic in the input.
        String pretty = PrettyPrinter.format("HTTP/1.1 200 OK\nContent-Type: text/html\n\n" + "<a>".repeat(20_000));
        assertTrue(pretty.length() < MessageText.MAX_CHARS + 100, "length " + pretty.length());
        assertTrue(pretty.matches("(?s).*\n… \\[truncated \\d+ characters]"));
    }

    // Robustness: these bodies come from servers under test, possibly hostile, and are formatted on the Swing thread.

    private static final List<Function<String, String>> FORMATTERS = List.of(
        JsonFormatter::format,
        body -> MarkupFormatter.format(body, true),
        body -> MarkupFormatter.format(body, false),
        CssFormatter::format,
        JavaScriptFormatter::format
    );

    @Test
    void onlyEverChangesWhitespace() {
        Random random = new Random(1608);
        String alphabet = "<>/!?-[]{}()\"'`=;:,.$\\*+&|#@ \n\t\rabcxyz019";
        for (int run = 0; run < 3000; run++) {
            StringBuilder input = new StringBuilder();
            int length = random.nextInt(300);
            for (int i = 0; i < length; i++) {
                input.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            assertOnlyWhitespaceChanges(input.toString());
        }
        for (String input : List.of(
            "<script>if (a</b) { x(\"</script>\"); }</script><p>",
            "<style>a{b:url(x;y)}</style><a b='c>d' e=f>",
            "<!DOCTYPE x [<!ENTITY a \"]>\">]><x/>",
            "var a = b / c / d, r = /[/]\\//g; // end",
            "`${`${a}`}` + '\\'' + \"\\\"\"",
            "{\"a\":[{\"b\":\"}\"}]}"
        )) {
            assertOnlyWhitespaceChanges(input);
        }
    }

    private static void assertOnlyWhitespaceChanges(String input) {
        String expected = input.replaceAll("\\s", "");
        for (Function<String, String> formatter : FORMATTERS) {
            String output = formatter.apply(input);
            if (output != null) {
                assertEquals(expected, output.replaceAll("\\s", ""), () -> "input: " + input);
            }
        }
    }

    @Test
    void runsInLinearTimeOnHostileBodies() {
        int n = MessageText.MAX_CHARS;
        List<String> bodies = List.of(
            "<p><img src=data:image/png;base64," + "A".repeat(n) + "></p>",
            "<a" + "<".repeat(n),
            "<a>" + "<!--".repeat(n / 4),
            "<script>".repeat(n / 8),
            "<a>" + "<a b=\"".repeat(n / 6),
            "<a/>" + "<!DOCTYPE [".repeat(n / 11),
            "{".repeat(n),
            "(/".repeat(n / 2),
            "`${".repeat(n / 3),
            "a{".repeat(n / 2),
            "[".repeat(n)
        );
        for (String body : bodies) {
            for (Function<String, String> formatter : FORMATTERS) {
                assertTimeoutPreemptively(Duration.ofSeconds(2), () -> formatter.apply(body));
            }
        }
    }
}
