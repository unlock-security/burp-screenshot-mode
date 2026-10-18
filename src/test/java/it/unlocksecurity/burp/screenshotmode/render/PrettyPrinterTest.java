package it.unlocksecurity.burp.screenshotmode.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class PrettyPrinterTest {

    private static final String JSON_HEADERS = "HTTP/2 200 OK\nContent-Type: application/json\n\n";

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
    void reformatsAlreadyIndentedJson() {
        String pretty = PrettyPrinter.format(JSON_HEADERS + "{\"a\":[1,2]}");
        String reindented = PrettyPrinter.format(JSON_HEADERS + "{\n\t\"a\" : [\n\t\t1,\n\t\t2\n\t]\n}\n");
        assertEquals(pretty.replace("\"a\":", "\"a\" : "), reindented);
    }

    @Test
    void leavesInvalidOrScalarJsonUntouched() {
        assertNull(PrettyPrinter.json("{\"a\":1"));
        assertNull(PrettyPrinter.json("{\"a\":1,}"));
        assertNull(PrettyPrinter.json("[section]"));
        assertNull(PrettyPrinter.json("{} {}"));
        assertNull(PrettyPrinter.json("42"));
        String truncated = JSON_HEADERS + "{\"a\":\"xyz\n… [truncated 5 characters]";
        assertEquals(truncated, PrettyPrinter.format(truncated));
    }

    @Test
    void sniffsJsonWithoutContentType() {
        assertEquals("POST / HTTP/1.1\nHost: x\n\n[\n  1\n]", PrettyPrinter.format("POST / HTTP/1.1\nHost: x\n\n[1]"));
    }

    @Test
    void leavesHeadersAndOtherBodiesUntouched() {
        String form = "POST / HTTP/1.1\nContent-Type: application/x-www-form-urlencoded\n\na={\"b\":1}";
        assertEquals(form, PrettyPrinter.format(form));
        assertEquals("GET / HTTP/1.1\nHost: x", PrettyPrinter.format("GET / HTTP/1.1\nHost: x"));
        assertEquals("GET / HTTP/1.1\nHost: x\n\n", PrettyPrinter.format("GET / HTTP/1.1\nHost: x\n\n"));
    }

    @Test
    void indentsXml() {
        String xml =
            "<?xml version=\"1.0\"?><feed><title>News</title><link>https://x.test/</link>" +
            "<entry id=\"1\"><summary><![CDATA[a <b>]]></summary><empty/></entry><!-- end --></feed>";
        assertEquals(
            """
            <?xml version="1.0"?>
            <feed>
              <title>News</title>
              <link>https://x.test/</link>
              <entry id="1">
                <summary>
                  <![CDATA[a <b>]]>
                </summary>
                <empty/>
              </entry>
              <!-- end -->
            </feed>""",
            PrettyPrinter.markup(xml, false)
        );
    }

    @Test
    void indentsHtmlWithVoidAndVerbatimElements() {
        String html =
            "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><script>if (a<b) { x(\"</p>\"); }</script></head>" +
            "<body><p title=\"a>b\">Hi <b>you</b></p><br><pre>  keep\n  this</pre></body></html>";
        assertEquals(
            """
            <!DOCTYPE html>
            <html>
              <head>
                <meta charset="utf-8">
                <script>if (a<b) { x("</p>"); }</script>
              </head>
              <body>
                <p title="a>b">
                  Hi
                  <b>you</b>
                </p>
                <br>
                <pre>  keep
              this</pre>
              </body>
            </html>""",
            PrettyPrinter.format("HTTP/1.1 200 OK\nContent-Type: text/html\n\n" + html).split("\n\n", 2)[1]
        );
    }

    @Test
    void leavesTextWithoutTagsUntouched() {
        assertNull(PrettyPrinter.markup("just text", true));
    }
}
