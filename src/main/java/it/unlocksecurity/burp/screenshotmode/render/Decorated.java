package it.unlocksecurity.burp.screenshotmode.render;

import java.awt.Color;
import java.util.List;

/** Text to display plus the decorations to paint over it, with offsets into {@link #text()}. */
public record Decorated(String text, List<Span> spans) {
    public enum Kind {
        HIGHLIGHT,
        RECTANGLE,
        BLUR,
        OPAQUE,
        /** Replacement text inserted by a {@code replace} redaction. */
        REPLACED,
    }

    public record Span(int from, int to, Kind kind, Color color) {}

    public List<Span> spans(Kind kind) {
        return spans
            .stream()
            .filter(span -> span.kind() == kind)
            .toList();
    }
}
