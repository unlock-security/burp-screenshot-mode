package it.unlocksecurity.burp.screenshotmode.render;

import it.unlocksecurity.burp.screenshotmode.model.HighlightMode;
import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Port of the Caido plugin's {@code decorations.ts}: finds highlight and redaction matches and resolves them
 * into spans. Unlike CodeMirror replace decorations, replacements are applied to the text itself, so every
 * other span is mapped from source offsets to display offsets.
 */
public final class RuleEngine {

    /** Guards Burp's UI thread against catastrophic backtracking in user-typed patterns. */
    private static final long MATCH_BUDGET_NANOS = TimeUnit.MILLISECONDS.toNanos(500);

    record Range(int from, int to) {
        boolean overlaps(Range other) {
            return from < other.to && other.from < to;
        }
    }

    record Group(int from, int to, int index) {}

    record Match(int from, int to, List<Group> groups) {}

    private record Replacement(int from, int to, String text) {}

    private record Segment(int sourceFrom, int sourceTo, int displayFrom, int displayTo) {}

    private RuleEngine() {}

    public static Decorated apply(String text, List<HighlightRule> highlights, List<RedactionRule> redactions) {
        List<List<Range>> redactionRanges = new ArrayList<>();
        List<Range> redacted = new ArrayList<>();
        for (RedactionRule rule : redactions) {
            List<Range> ranges = new ArrayList<>();
            for (Match match : findMatches(text, rule.regex, rule.matchMode)) {
                ranges.addAll(redactionRanges(match, rule.useCaptureGroups, rule.selectedGroups));
            }
            redactionRanges.add(ranges);
            redacted.addAll(ranges);
        }

        List<Decorated.Span> sourceSpans = new ArrayList<>();
        for (HighlightRule rule : highlights) {
            Decorated.Kind kind =
                rule.mode == HighlightMode.RECTANGLE ? Decorated.Kind.RECTANGLE : Decorated.Kind.HIGHLIGHT;
            Color color = parseColor(rule.color, Color.YELLOW);
            for (Match match : findMatches(text, rule.regex, rule.matchMode)) {
                Range range = new Range(match.from(), match.to());
                if (redacted.stream().noneMatch(range::overlaps)) {
                    sourceSpans.add(new Decorated.Span(match.from(), match.to(), kind, color));
                }
            }
        }

        List<Replacement> replacements = new ArrayList<>();
        for (int i = 0; i < redactions.size(); i++) {
            RedactionRule rule = redactions.get(i);
            for (Range range : redactionRanges.get(i)) {
                if (range.to() <= range.from()) {
                    continue;
                }
                switch (rule.mode) {
                    case BLUR -> sourceSpans.add(
                        new Decorated.Span(range.from(), range.to(), Decorated.Kind.BLUR, null)
                    );
                    case OPAQUE -> sourceSpans.add(
                        new Decorated.Span(
                            range.from(),
                            range.to(),
                            Decorated.Kind.OPAQUE,
                            parseColor(rule.color, Color.BLACK)
                        )
                    );
                    case REPLACE -> replacements.add(
                        new Replacement(
                            range.from(),
                            range.to(),
                            rule.replacementText == null || rule.replacementText.isEmpty()
                                ? RedactionRule.DEFAULT_REPLACEMENT
                                : rule.replacementText
                        )
                    );
                }
            }
        }

        // Overlapping replacements cannot both apply: keep the earliest one.
        replacements.sort(Comparator.comparingInt(Replacement::from).thenComparingInt(Replacement::to));
        StringBuilder display = new StringBuilder(text.length());
        List<Segment> segments = new ArrayList<>();
        int position = 0;
        for (Replacement replacement : replacements) {
            if (replacement.from() < position) {
                continue;
            }
            display.append(text, position, replacement.from());
            int displayFrom = display.length();
            display.append(replacement.text());
            segments.add(new Segment(replacement.from(), replacement.to(), displayFrom, display.length()));
            position = replacement.to();
        }
        display.append(text, position, text.length());

        List<Decorated.Span> spans = new ArrayList<>();
        for (Decorated.Span span : sourceSpans) {
            int from = toDisplay(segments, span.from(), false);
            int to = toDisplay(segments, span.to(), true);
            if (to > from) {
                spans.add(new Decorated.Span(from, to, span.kind(), span.color()));
            }
        }
        for (Segment segment : segments) {
            spans.add(new Decorated.Span(segment.displayFrom(), segment.displayTo(), Decorated.Kind.REPLACED, null));
        }
        spans.sort(Comparator.comparingInt(Decorated.Span::from).thenComparingInt(Decorated.Span::to));
        return new Decorated(display.toString(), List.copyOf(spans));
    }

    /** Maps a source offset to a display offset; offsets inside a replaced range snap to its edges. */
    private static int toDisplay(List<Segment> segments, int offset, boolean isEnd) {
        int display = offset;
        for (Segment segment : segments) {
            if (offset >= segment.sourceTo()) {
                display = offset - segment.sourceTo() + segment.displayTo();
            } else if (offset > segment.sourceFrom()) {
                return isEnd ? segment.displayTo() : segment.displayFrom();
            } else {
                break;
            }
        }
        return display;
    }

    static List<Range> redactionRanges(Match match, boolean useCaptureGroups, List<Integer> selectedGroups) {
        if (!useCaptureGroups || match.groups().isEmpty()) {
            return List.of(new Range(match.from(), match.to()));
        }
        List<Range> ranges = new ArrayList<>();
        for (Group group : match.groups()) {
            if (selectedGroups.contains(group.index())) {
                ranges.add(new Range(group.from(), group.to()));
            }
        }
        return ranges;
    }

    static List<Match> findMatches(String text, String pattern, MatchMode matchMode) {
        List<Match> matches = new ArrayList<>();
        Optional<Pattern> compiled = compile(pattern, matchMode);
        if (compiled.isEmpty()) {
            return matches;
        }

        Matcher matcher = compiled
            .get()
            .matcher(new DeadlineCharSequence(text, System.nanoTime() + MATCH_BUDGET_NANOS));
        try {
            while (matcher.find()) {
                if (matcher.end() == matcher.start()) {
                    continue;
                }
                List<Group> groups = new ArrayList<>();
                for (int i = 1; i <= matcher.groupCount(); i++) {
                    if (matcher.start(i) >= 0) {
                        groups.add(new Group(matcher.start(i), matcher.end(i), i));
                    }
                }
                matches.add(new Match(matcher.start(), matcher.end(), groups));
            }
        } catch (DeadlineCharSequence.TimeoutException e) {
            // Keep whatever matched before the budget ran out.
        }
        return matches;
    }

    private static Optional<Pattern> compile(String pattern, MatchMode matchMode) {
        if (pattern == null || pattern.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Pattern.compile(matchMode == MatchMode.STRING ? Pattern.quote(pattern) : pattern));
        } catch (PatternSyntaxException e) {
            return Optional.empty();
        }
    }

    /** Returns the syntax error of a pattern, if any. */
    public static Optional<String> validate(String pattern, MatchMode matchMode) {
        if (matchMode == MatchMode.STRING || pattern == null || pattern.isEmpty()) {
            return Optional.empty();
        }
        try {
            Pattern.compile(pattern);
            return Optional.empty();
        } catch (PatternSyntaxException e) {
            return Optional.of(e.getDescription() + (e.getIndex() >= 0 ? " near index " + e.getIndex() : ""));
        }
    }

    /**
     * Labels of the capture groups in a pattern, the label of group {@code n} at index {@code n - 1}: its name for a
     * named group, its number otherwise. Empty for literal strings and invalid patterns. Caido only shows numbers.
     */
    public static List<String> groupLabels(String pattern, MatchMode matchMode) {
        if (matchMode == MatchMode.STRING) {
            return List.of();
        }
        return compile(pattern, matchMode)
            .map(compiled -> {
                String[] labels = new String[compiled.matcher("").groupCount()];
                for (int i = 0; i < labels.length; i++) {
                    labels[i] = String.valueOf(i + 1);
                }
                compiled.namedGroups().forEach((name, group) -> labels[group - 1] = name);
                return List.of(labels);
            })
            .orElse(List.of());
    }

    public static Color parseColor(String hex, Color fallback) {
        if (hex != null && hex.matches("#?[0-9a-fA-F]{6}")) {
            return new Color(Integer.parseInt(hex.startsWith("#") ? hex.substring(1) : hex, 16));
        }
        return fallback;
    }

    public static String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /** A CharSequence that aborts regex matching once a deadline has passed. */
    private static final class DeadlineCharSequence implements CharSequence {

        static final class TimeoutException extends RuntimeException {

            TimeoutException() {
                super(null, null, false, false);
            }
        }

        private final CharSequence inner;
        private final long deadline;
        private int reads;

        DeadlineCharSequence(CharSequence inner, long deadline) {
            this.inner = inner;
            this.deadline = deadline;
        }

        @Override
        public char charAt(int index) {
            if ((++reads & 0xFFF) == 0 && System.nanoTime() > deadline) {
                throw new TimeoutException();
            }
            return inner.charAt(index);
        }

        @Override
        public int length() {
            return inner.length();
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new DeadlineCharSequence(inner.subSequence(start, end), deadline);
        }

        @Override
        public String toString() {
            return inner.toString();
        }
    }
}
