package it.unlocksecurity.burp.screenshotmode.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaletteTest {

    @Test
    void keepsReplacedTextReadableOnItsBox() {
        for (Palette palette : new Palette[] { Palette.LIGHT, Palette.DARK }) {
            double ratio = Palette.contrast(palette.redactionText(), palette.redaction());
            assertTrue(ratio >= 4.5, "WCAG AA for normal text, got " + ratio);
        }
    }

    @Test
    void keepsEveryTextColorReadable() {
        for (Palette palette : new Palette[] { Palette.LIGHT, Palette.DARK }) {
            List<Color> text = List.of(
                palette.text(),
                palette.muted(),
                palette.sni(),
                palette.method(),
                palette.path(),
                palette.version(),
                palette.headerName(),
                palette.statusText(),
                palette.key(),
                palette.string(),
                palette.number(),
                palette.keyword(),
                palette.tag(),
                palette.attribute(),
                palette.punctuation(),
                palette.comment(),
                palette.status2xx(),
                palette.status3xx(),
                palette.status4xx(),
                palette.status5xx()
            );
            for (Color color : text) {
                double ratio = Palette.contrast(color, palette.background());
                assertTrue(ratio >= 4.5, "WCAG AA for normal text, got " + ratio + " for " + color);
            }
            // The URL bar's "URL" label, on the title bar.
            double ratio = Palette.contrast(palette.muted(), palette.headerBackground());
            assertTrue(ratio >= 4.5, "WCAG AA for normal text, got " + ratio);
        }
    }

    @Test
    void setsRedactionsApartFromTheBackground() {
        for (Palette palette : new Palette[] { Palette.LIGHT, Palette.DARK }) {
            double ratio = Palette.contrast(palette.redaction(), palette.background());
            assertTrue(ratio >= 3, "WCAG AA for graphical objects, got " + ratio);
        }
    }
}
