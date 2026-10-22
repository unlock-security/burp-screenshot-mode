package it.unlocksecurity.burp.screenshotmode.render.pretty;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MarkupFormatterTest {

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
            MarkupFormatter.format(xml, false)
        );
    }

    @Test
    void keepsDoctypeInternalSubsetTogether() {
        assertEquals(
            "<!DOCTYPE x [<!ENTITY a \"<b>\">]>\n<x>&a;</x>",
            MarkupFormatter.format("<!DOCTYPE x [<!ENTITY a \"<b>\">]><x>&a;</x>", false)
        );
    }

    @Test
    void indentsHtmlWithVoidAndVerbatimElements() {
        String html =
            "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>a<b</title></head>" +
            "<body><p title=\"a>b\">Hi <b>you</b></p><br><pre>  keep\n  this</pre><textarea> <i> </textarea></body></html>";
        assertEquals(
            """
            <!DOCTYPE html>
            <html>
              <head>
                <meta charset="utf-8">
                <title>a<b</title>
              </head>
              <body>
                <p title="a>b">
                  Hi
                  <b>you</b>
                </p>
                <br>
                <pre>  keep
              this</pre>
                <textarea> <i> </textarea>
              </body>
            </html>""",
            MarkupFormatter.format(html, true)
        );
    }

    @Test
    void formatsScriptsAndStyles() {
        String html =
            "<head><script>if (a<b) { x(\"</p>\"); }</script><style>p{margin:0}</style>" +
            "<script type=\"application/ld+json\">{\"a\":1}</script><script src=\"x.js\"></script></head>";
        assertEquals(
            """
            <head>
              <script>
                if (a<b) {
                  x("</p>");
                }
              </script>
              <style>
                p{
                  margin:0
                }
              </style>
              <script type="application/ld+json">
                {
                  "a":1
                }
              </script>
              <script src="x.js"></script>
            </head>""",
            MarkupFormatter.format(html, true)
        );
    }

    @Test
    void leavesTemplatesAndUnknownScriptTypesVerbatim() {
        String html = "<script type='text/x-template'><div>{{ a }}</div></script>";
        assertEquals(html, MarkupFormatter.format(html, true));
        // Not valid JSON: shown as it is.
        String json = "<script type=application/json>{a:1}</script>";
        assertEquals(json, MarkupFormatter.format(json, true));
    }

    @Test
    void showsBrokenMarkupAsSent() {
        // Nothing is repaired: unquoted values, a stray "<" and an unterminated tag stay as they are.
        assertEquals(
            "<p>\n  a < b\n  <img src=x onerror=alert(1)>\n  <a href=\"x",
            MarkupFormatter.format("<p>a < b<img src=x onerror=alert(1)><a href=\"x", true)
        );
        assertEquals("<a>\n  <!-- never closed", MarkupFormatter.format("<a><!-- never closed", true));
        assertEquals("<script>\n  f()", MarkupFormatter.format("<script>f()", true));
    }

    @Test
    void handlesLongTagsWithoutRecursion() {
        // Overflowed the stack with the former regular expression.
        String image = "<img src=data:image/png;base64," + "A".repeat(100_000) + ">";
        assertEquals("<p>\n  " + image + "\n</p>", MarkupFormatter.format("<p>" + image + "</p>", true));
    }

    @Test
    void leavesTextWithoutTagsUntouched() {
        assertNull(MarkupFormatter.format("just text", true));
        assertNull(MarkupFormatter.format("a < b and c > d", true));
    }
}
