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

    /** The text from {@code start} on, with the spans shifted to it and cut where they begin earlier. */
    public Decorated from(int start) {
        List<Span> shifted = spans
            .stream()
            .filter(span -> span.to() > start)
            .map(span -> new Span(Math.max(span.from(), start) - start, span.to() - start, span.kind(), span.color()))
            .toList();
        return new Decorated(text.substring(start), shifted);
    }

    public List<Span> spans(Kind kind) {
        return spans
            .stream()
            .filter(span -> span.kind() == kind)
            .toList();
    }
}
