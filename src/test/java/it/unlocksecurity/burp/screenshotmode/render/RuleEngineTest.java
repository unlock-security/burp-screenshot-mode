package it.unlocksecurity.burp.screenshotmode.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.HighlightMode;
import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import java.awt.Color;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class RuleEngineTest {

    private static final String TEXT = "Authorization: Bearer abc.def\nCookie: session=s3cr3t; theme=dark";

    private static HighlightRule highlight(String pattern, MatchMode matchMode) {
        return HighlightRule.create(pattern, RuleTarget.REQUEST, matchMode);
    }

    private static RedactionRule redaction(String pattern, RedactionMode mode) {
        return RedactionRule.create(pattern, RuleTarget.REQUEST, mode, MatchMode.REGEX);
    }

    private static String slice(Decorated decorated, Decorated.Span span) {
        return decorated.text().substring(span.from(), span.to());
    }

    @Test
    void highlightsEveryRegexMatch() {
        Decorated result = RuleEngine.apply(TEXT, List.of(highlight("[a-z]+=", MatchMode.REGEX)), List.of());
        assertEquals(TEXT, result.text());
        assertEquals(
            List.of("session=", "theme="),
            result
                .spans()
                .stream()
                .map(s -> slice(result, s))
                .toList()
        );
        assertEquals(Decorated.Kind.HIGHLIGHT, result.spans().getFirst().kind());
        assertEquals(Color.YELLOW, result.spans().getFirst().color());
    }

    @Test
    void stringModeMatchesRegexMetacharactersLiterally() {
        Decorated result = RuleEngine.apply(TEXT, List.of(highlight("abc.def", MatchMode.STRING)), List.of());
        assertEquals(1, result.spans().size());
        Decorated none = RuleEngine.apply("abcXdef", List.of(highlight("abc.def", MatchMode.STRING)), List.of());
        assertTrue(none.spans().isEmpty());
    }

    @Test
    void rectangleModeAndCustomColor() {
        HighlightRule rule = highlight("Cookie", MatchMode.STRING);
        rule.mode = HighlightMode.RECTANGLE;
        rule.color = "#ff0000";
        Decorated.Span span = RuleEngine.apply(TEXT, List.of(rule), List.of()).spans().getFirst();
        assertEquals(Decorated.Kind.RECTANGLE, span.kind());
        assertEquals(Color.RED, span.color());
    }

    @Test
    void highlightsOverlappingARedactionAreDropped() {
        Decorated result = RuleEngine.apply(
            TEXT,
            List.of(highlight("Bearer \\S+", MatchMode.REGEX), highlight("Cookie", MatchMode.STRING)),
            List.of(redaction("abc\\.def", RedactionMode.BLUR))
        );
        assertEquals(
            List.of(Decorated.Kind.BLUR, Decorated.Kind.HIGHLIGHT),
            result.spans().stream().map(Decorated.Span::kind).toList()
        );
        assertEquals("abc.def", slice(result, result.spans().getFirst()));
    }

    @Test
    void opaqueRedactionKeepsTextAndCarriesColor() {
        RedactionRule rule = redaction("s3cr3t", RedactionMode.OPAQUE);
        rule.color = "#00ff00";
        Decorated result = RuleEngine.apply(TEXT, List.of(), List.of(rule));
        assertEquals(TEXT, result.text());
        assertEquals(Color.GREEN, result.spans().getFirst().color());
    }

    @Test
    void replaceRedactionRewritesTextAndShiftsLaterSpans() {
        RedactionRule rule = redaction("abc\\.def", RedactionMode.REPLACE);
        rule.replacementText = "***";
        Decorated result = RuleEngine.apply(TEXT, List.of(highlight("theme", MatchMode.STRING)), List.of(rule));
        assertEquals("Authorization: Bearer ***\nCookie: session=s3cr3t; theme=dark", result.text());
        assertEquals(
            List.of("***", "theme"),
            result
                .spans()
                .stream()
                .map(s -> slice(result, s))
                .toList()
        );
        assertEquals(Decorated.Kind.REPLACED, result.spans().getFirst().kind());
    }

    @Test
    void emptyReplacementFallsBackToRedacted() {
        RedactionRule rule = redaction("s3cr3t", RedactionMode.REPLACE);
        rule.replacementText = "";
        assertTrue(RuleEngine.apply(TEXT, List.of(), List.of(rule)).text().contains("session=[REDACTED];"));
    }

    @Test
    void overlappingReplacementsMergeIntoTheEarliest() {
        RedactionRule first = redaction("session=s3cr3t", RedactionMode.REPLACE);
        RedactionRule second = redaction("s3cr3t; theme", RedactionMode.REPLACE);
        second.replacementText = "SECOND";
        Decorated result = RuleEngine.apply(TEXT, List.of(), List.of(second, first));
        // Nothing of either match is left visible.
        assertTrue(result.text().endsWith("Cookie: [REDACTED]=dark"), result.text());
    }

    @Test
    void aShorterReplacementInsideALongerOneIsAbsorbed() {
        RedactionRule header = redaction("(?<=Cookie: ).+", RedactionMode.REPLACE);
        header.replacementText = "[OMITTED]";
        RedactionRule prefix = redaction("session=s3cr3t", RedactionMode.REPLACE);
        Decorated result = RuleEngine.apply(TEXT, List.of(), List.of(prefix, header));
        assertTrue(result.text().endsWith("Cookie: [OMITTED]"), result.text());
    }

    @Test
    void spansInsideAReplacementSnapToItsEdges() {
        RedactionRule replace = redaction("session=s3cr3t", RedactionMode.REPLACE);
        RedactionRule blur = redaction("s3cr3t; theme", RedactionMode.BLUR);
        Decorated result = RuleEngine.apply(TEXT, List.of(), List.of(replace, blur));
        Decorated.Span blurSpan = result.spans(Decorated.Kind.BLUR).getFirst();
        assertEquals("[REDACTED]; theme", slice(result, blurSpan));
    }

    @Test
    void captureGroupsRedactOnlySelectedGroups() {
        RedactionRule rule = redaction("(session)=(\\w+)", RedactionMode.BLUR);
        rule.useCaptureGroups = true;
        rule.selectedGroups = List.of(2);
        Decorated result = RuleEngine.apply(TEXT, List.of(), List.of(rule));
        assertEquals(
            List.of("s3cr3t"),
            result
                .spans()
                .stream()
                .map(s -> slice(result, s))
                .toList()
        );
    }

    @Test
    void captureGroupsDisabledRedactWholeMatch() {
        RedactionRule rule = redaction("session=(\\w+)", RedactionMode.BLUR);
        rule.selectedGroups = List.of(1);
        Decorated result = RuleEngine.apply(TEXT, List.of(), List.of(rule));
        assertEquals(
            List.of("session=s3cr3t"),
            result
                .spans()
                .stream()
                .map(s -> slice(result, s))
                .toList()
        );
    }

    @Test
    void invalidAndEmptyPatternsMatchNothing() {
        assertTrue(
            RuleEngine.apply(TEXT, List.of(highlight("(", MatchMode.REGEX), highlight("", MatchMode.REGEX)), List.of())
                .spans()
                .isEmpty()
        );
        assertTrue(RuleEngine.validate("(", MatchMode.REGEX).isPresent());
        assertTrue(RuleEngine.validate("(", MatchMode.STRING).isEmpty());
    }

    @Test
    void emptyMatchesAreIgnored() {
        assertTrue(
            RuleEngine.apply(TEXT, List.of(highlight("x*", MatchMode.REGEX)), List.of())
                .spans()
                .isEmpty()
        );
    }

    @Test
    void labelsCaptureGroupsByNameOrNumber() {
        assertEquals(List.of("1", "name", "3"), RuleEngine.groupLabels("(a)(?:b)(?<name>c)(d)", MatchMode.REGEX));
        assertEquals(List.of(), RuleEngine.groupLabels("(a)", MatchMode.STRING));
        assertEquals(List.of(), RuleEngine.groupLabels("(", MatchMode.REGEX));
    }

    private static Map<Object, RuleStatus> statuses(
        String text,
        List<HighlightRule> highlights,
        List<RedactionRule> redactions
    ) {
        Map<Object, RuleStatus> statuses = new IdentityHashMap<>();
        RuleEngine.apply(text, highlights, redactions, statuses::put);
        return statuses;
    }

    @Test
    void reportsHowManyTimesEachRuleMatched() {
        HighlightRule twice = highlight("e", MatchMode.STRING);
        RedactionRule none = redaction("absent", RedactionMode.BLUR);
        Map<Object, RuleStatus> statuses = statuses(TEXT, List.of(twice), List.of(none));
        assertEquals(new RuleStatus(7, null, null), statuses.get(twice));
        assertEquals(new RuleStatus(0, null, null), statuses.get(none));
        assertFalse(statuses.get(none).failed(), "no match is not a failure");
    }

    @Test
    void reportsInvalidPatternsWithTheirError() {
        RedactionRule invalid = redaction("session=(", RedactionMode.BLUR);
        RedactionRule empty = redaction("", RedactionMode.BLUR);
        RedactionRule literal = RedactionRule.create("(", RuleTarget.REQUEST, RedactionMode.BLUR, MatchMode.STRING);
        Map<Object, RuleStatus> statuses = statuses(TEXT, List.of(), List.of(invalid, empty, literal));
        assertEquals(RuleStatus.Problem.INVALID, statuses.get(invalid).problem());
        assertTrue(statuses.get(invalid).detail().startsWith("Unclosed group"), statuses.get(invalid).detail());
        assertFalse(statuses.get(empty).failed(), "an empty draft rule is not a failure");
        assertFalse(statuses.get(literal).failed(), "a literal string is never an invalid regex");
    }

    @Test
    @Timeout(5)
    void reportsRulesThatStoppedEarly() {
        // The backreference turns off the memoization that keeps (a+)+b fast on Java 21.
        RedactionRule slow = redaction("(a+)+\\1b", RedactionMode.BLUR);
        RedactionRule deep = redaction("x(a|b)*y", RedactionMode.BLUR);
        String text = "a".repeat(40) + "!\nx" + "ab".repeat(50_000) + "y";
        Map<Object, RuleStatus> statuses = statuses(text, List.of(), List.of(slow, deep));
        assertEquals(RuleStatus.Problem.STOPPED, statuses.get(slow).problem());
        assertEquals(RuleStatus.Problem.STOPPED, statuses.get(deep).problem());
    }

    @Test
    @Timeout(5)
    void catastrophicBacktrackingIsCutShort() {
        String text = "a".repeat(40) + "!";
        assertTrue(
            RuleEngine.apply(text, List.of(highlight("(a+)+\\1b", MatchMode.REGEX)), List.of())
                .spans()
                .isEmpty()
        );
    }

    @Test
    void stackOverflowingPatternsAreSkipped() {
        // (a|b)* recurses once per character in java.util.regex.
        String text = "x" + "ab".repeat(50_000) + "y";
        assertTrue(
            RuleEngine.apply(
                text,
                List.of(highlight("x(a|b)*y", MatchMode.REGEX), highlight("y", MatchMode.STRING)),
                List.of()
            )
                .spans()
                .size() == 1
        );
    }

    @Test
    void parsesAndFormatsColors() {
        assertEquals(new Color(0x12ab34), RuleEngine.parseColor("#12AB34", Color.BLACK));
        assertEquals(Color.BLACK, RuleEngine.parseColor("red", Color.BLACK));
        assertEquals("#12ab34", RuleEngine.toHex(new Color(0x12ab34)));
    }
}
