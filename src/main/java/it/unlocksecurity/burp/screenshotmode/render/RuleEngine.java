package it.unlocksecurity.burp.screenshotmode.render;

import it.unlocksecurity.burp.screenshotmode.model.HighlightMode;
import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.Rule;
import it.unlocksecurity.burp.screenshotmode.model.RuleType;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
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

    /** The matches of one pattern, and why matching did not complete, if it did not. */
    record Found(List<Match> matches, RuleStatus.Problem problem) {}

    private record Replacement(int from, int to, String text) {}

    private record Segment(int sourceFrom, int sourceTo, int displayFrom, int displayTo) {}

    private RuleEngine() {}

    public static Decorated apply(String text, List<HighlightRule> highlights, List<RedactionRule> redactions) {
        return apply(text, highlights, redactions, (rule, status) -> {});
    }

    /** Receives a range of the source text a rule changed: what a redaction hides, or what a highlight marks. */
    public interface HitListener {
        void hit(Rule rule, int from, int to);
    }

    /** Like {@link #apply(String, List, List)}, also reporting what each rule did. */
    public static Decorated apply(
        String text,
        List<HighlightRule> highlights,
        List<RedactionRule> redactions,
        BiConsumer<Object, RuleStatus> statuses
    ) {
        return apply(text, highlights, redactions, statuses, (rule, from, to) -> {});
    }

    /** Like {@link #apply(String, List, List, BiConsumer)}, also reporting where each rule changed the text. */
    public static Decorated apply(
        String text,
        List<HighlightRule> highlights,
        List<RedactionRule> redactions,
        BiConsumer<Object, RuleStatus> statuses,
        HitListener hits
    ) {
        List<List<Range>> redactionRanges = new ArrayList<>();
        List<Range> redacted = new ArrayList<>();
        for (RedactionRule rule : redactions) {
            List<Range> ranges = new ArrayList<>();
            Found found = find(text, rule, false);
            statuses.accept(rule, status(found, rule));
            for (Match match : found.matches()) {
                ranges.addAll(
                    rule.type == RuleType.HEADER
                        ? List.of(new Range(match.from(), match.to()))
                        : redactionRanges(match, rule.useCaptureGroups, rule.selectedGroups)
                );
            }
            redactionRanges.add(ranges);
            redacted.addAll(ranges);
            ranges
                .stream()
                .filter(range -> range.to() > range.from())
                .forEach(range -> hits.hit(rule, range.from(), range.to()));
        }

        List<Decorated.Span> sourceSpans = new ArrayList<>();
        for (HighlightRule rule : highlights) {
            Decorated.Kind kind =
                rule.mode == HighlightMode.RECTANGLE ? Decorated.Kind.RECTANGLE : Decorated.Kind.HIGHLIGHT;
            Color color = parseColor(rule.color, Color.YELLOW);
            Found found = find(text, rule, true);
            statuses.accept(rule, status(found, rule));
            for (Match match : found.matches()) {
                Range range = new Range(match.from(), match.to());
                if (redacted.stream().noneMatch(range::overlaps)) {
                    sourceSpans.add(new Decorated.Span(match.from(), match.to(), kind, color));
                    hits.hit(rule, match.from(), match.to());
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
                        new Decorated.Span(range.from(), range.to(), Decorated.Kind.OPAQUE, null)
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

        // Overlapping replacements cannot both apply: merge them into one, with the text of the one starting first (the
        // longest, if they start together), so no part of either match stays visible.
        replacements.sort(
            Comparator.comparingInt(Replacement::from).thenComparing(
                Comparator.comparingInt(Replacement::to).reversed()
            )
        );
        List<Replacement> merged = new ArrayList<>();
        for (Replacement replacement : replacements) {
            Replacement last = merged.isEmpty() ? null : merged.getLast();
            if (last != null && replacement.from() < last.to()) {
                merged.set(
                    merged.size() - 1,
                    new Replacement(last.from(), Math.max(last.to(), replacement.to()), last.text())
                );
            } else {
                merged.add(replacement);
            }
        }
        StringBuilder display = new StringBuilder(text.length());
        List<Segment> segments = new ArrayList<>();
        int position = 0;
        for (Replacement replacement : merged) {
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

    private static RuleStatus status(Found found, Rule rule) {
        return new RuleStatus(
            found.matches().size(),
            found.problem(),
            found.problem() == RuleStatus.Problem.INVALID ? validate(rule).orElse(null) : null
        );
    }

    /** @param wholeLine for a header rule, match the whole header line instead of its value */
    private static Found find(String text, Rule rule, boolean wholeLine) {
        return rule.type == RuleType.HEADER
            ? new Found(headerMatches(text, rule.headers, wholeLine), null)
            : findMatches(text, rule);
    }

    /**
     * The values (or whole lines) of the named headers, ignoring case. Scans the header lines by hand, in linear time,
     * as the message comes from the server under test.
     */
    static List<Match> headerMatches(String text, List<String> names, boolean wholeLine) {
        Set<String> wanted = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        wanted.addAll(names);
        List<Match> matches = new ArrayList<>();
        int start = text.indexOf('\n') + 1; // after the start line
        while (start > 0 && start < text.length()) {
            int end = text.indexOf('\n', start);
            if (end < 0) {
                end = text.length();
            }
            if (end == start) {
                break; // the end of the headers
            }
            int colon = start;
            while (colon < end && text.charAt(colon) != ':') {
                colon++;
            }
            if (colon > start && colon < end && wanted.contains(text.substring(start, colon))) {
                int from = colon + 1;
                while (from < end && (text.charAt(from) == ' ' || text.charAt(from) == '\t')) {
                    from++;
                }
                if (wholeLine) {
                    matches.add(new Match(start, end, List.of()));
                } else if (from < end) {
                    matches.add(new Match(from, end, List.of()));
                }
            }
            start = end + 1;
        }
        return matches;
    }

    static Found findMatches(String text, Rule rule) {
        List<Match> matches = new ArrayList<>();
        Optional<Pattern> compiled = compile(rule);
        if (compiled.isEmpty()) {
            boolean invalid = validate(rule).isPresent();
            return new Found(matches, invalid ? RuleStatus.Problem.INVALID : null);
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
        } catch (DeadlineCharSequence.TimeoutException | StackOverflowError e) {
            // Keep whatever matched before the budget ran out, or before a pattern that recurses once per character,
            // like (a|b)*, overflowed the stack on a long match, and say so.
            return new Found(matches, RuleStatus.Problem.STOPPED);
        }
        return new Found(matches, null);
    }

    /** Where a whole word can start or end: anywhere but between two word characters, Unicode letters included. */
    private static final String WORD_EDGE = "(?:(?<![\\p{L}\\p{N}_])|(?![\\p{L}\\p{N}_]))";

    /** What a pattern rule matches, with its options applied; empty for no pattern or an invalid one. */
    private static Optional<Pattern> compile(Rule rule) {
        if (rule.regex == null || rule.regex.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(compileWithOptions(rule));
        } catch (PatternSyntaxException e) {
            return Optional.empty();
        }
    }

    private static Pattern compileWithOptions(Rule rule) {
        String source = rule.matchMode == MatchMode.STRING ? Pattern.quote(rule.regex) : rule.regex;
        if (rule.wholeWord) {
            // In a group of its own, so an alternation in the pattern stays between the edges.
            source = WORD_EDGE + "(?:" + source + ")" + WORD_EDGE;
        }
        return Pattern.compile(source, rule.matchCase ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /** Returns the syntax error of a rule's pattern, if any. */
    public static Optional<String> validate(Rule rule) {
        if (rule.matchMode == MatchMode.STRING || rule.regex == null || rule.regex.isEmpty()) {
            return Optional.empty();
        }
        try {
            // On its own first, so the error points into the pattern as typed.
            Pattern.compile(rule.regex);
        } catch (PatternSyntaxException e) {
            return Optional.of(e.getDescription() + (e.getIndex() >= 0 ? " near index " + e.getIndex() : ""));
        }
        try {
            compileWithOptions(rule);
            return Optional.empty();
        } catch (PatternSyntaxException e) {
            // Such as a comment in (?x) mode, which swallows the end of the whole word group.
            return Optional.of("Can't match whole words with this pattern");
        }
    }

    /**
     * Whether a redaction is set to hide only some of its pattern's capture groups but none is chosen: it matches, and a
     * match hides nothing. A rule that is off, or applies nowhere (also shown as off), doesn't count.
     */
    public static boolean redactsNothing(RedactionRule rule) {
        return (
            rule.enabled &&
            rule.type == RuleType.PATTERN &&
            !rule.targets.isEmpty() &&
            rule.useCaptureGroups &&
            rule.selectedGroups.isEmpty() &&
            !groupLabels(rule.regex, rule.matchMode).isEmpty()
        );
    }

    /**
     * Labels of the capture groups in a pattern, the label of group {@code n} at index {@code n - 1}: its name for a
     * named group, its number otherwise. Empty for literal strings and invalid patterns. Caido only shows numbers.
     */
    public static List<String> groupLabels(String pattern, MatchMode matchMode) {
        if (matchMode == MatchMode.STRING) {
            return List.of();
        }
        if (pattern == null || pattern.isEmpty()) {
            return List.of();
        }
        Pattern compiled;
        try {
            compiled = Pattern.compile(pattern);
        } catch (PatternSyntaxException e) {
            return List.of();
        }
        String[] labels = new String[compiled.matcher("").groupCount()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = String.valueOf(i + 1);
        }
        compiled.namedGroups().forEach((name, group) -> labels[group - 1] = name);
        return List.of(labels);
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
