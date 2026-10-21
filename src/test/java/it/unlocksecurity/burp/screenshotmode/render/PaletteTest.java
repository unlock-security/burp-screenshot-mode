package it.unlocksecurity.burp.screenshotmode.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class PaletteTest {

    @Test
    void keepsReplacedTextReadableOnItsBox() {
        for (Palette palette : new Palette[] { Palette.LIGHT, Palette.DARK }) {
            double ratio = contrast(palette.redactionText(), palette.redaction());
            assertTrue(ratio >= 4.5, "WCAG AA for normal text, got " + ratio);
        }
    }

    @Test
    void setsRedactionsApartFromTheBackground() {
        for (Palette palette : new Palette[] { Palette.LIGHT, Palette.DARK }) {
            double ratio = contrast(palette.redaction(), palette.background());
            assertTrue(ratio >= 3, "WCAG AA for graphical objects, got " + ratio);
        }
    }

    /** The WCAG 2 contrast ratio. */
    private static double contrast(Color a, Color b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(Color color) {
        return (
            0.2126 * channel(color.getRed()) + 0.7152 * channel(color.getGreen()) + 0.0722 * channel(color.getBlue())
        );
    }

    private static double channel(int value) {
        double c = value / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
