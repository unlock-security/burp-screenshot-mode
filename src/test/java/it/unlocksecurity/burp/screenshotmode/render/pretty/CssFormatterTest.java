package it.unlocksecurity.burp.screenshotmode.render.pretty;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CssFormatterTest {

    @Test
    void putsRulesAndDeclarationsOnTheirOwnLines() {
        assertEquals(
            """
            @import url(a.css);
            @media (max-width:600px){
              .a,.b>p{
                color:red;
                background:url(data:image/png;base64,AA==)
              }
            }
            .x{}
            a[title="{;}"]{
              content:"}"
            }""",
            CssFormatter.format(
                "@import url(a.css);@media (max-width:600px){.a,.b>p{color:red;background:url(data:image/png;base64,AA==)}}" +
                    ".x{}a[title=\"{;}\"]{content:\"}\"}"
            )
        );
    }

    @Test
    void keepsTrailingCommentsOnTheirLine() {
        assertEquals(
            "a{\n  b:c; /* why */\n  d:e\n}/* next */\nf{}",
            CssFormatter.format("a{b:c; /* why */d:e}/* next */f{}")
        );
    }

    @Test
    void reindentsFormattedCss() {
        assertEquals(
            "h1,\nh2{\n  color:red;\n\n  margin:0\n}",
            CssFormatter.format("h1,\nh2{\n        color:red;\n\n\n\n    margin:0\n}\n")
        );
    }
}
