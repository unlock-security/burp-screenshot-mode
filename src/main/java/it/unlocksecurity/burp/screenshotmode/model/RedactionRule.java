package it.unlocksecurity.burp.screenshotmode.model;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * A redaction rule. {@link #color} only applies to {@link RedactionMode#OPAQUE} and {@link #replacementText} only
 * to {@link RedactionMode#REPLACE}; both are kept in memory so switching mode back and forth doesn't lose them.
 */
public final class RedactionRule extends Rule {

    public static final String DEFAULT_COLOR = "#000000";
    public static final String DEFAULT_REPLACEMENT = "[REDACTED]";

    public RedactionMode mode = RedactionMode.BLUR;
    public String color = DEFAULT_COLOR;
    public String replacementText = DEFAULT_REPLACEMENT;
    /** {@link RuleType#PATTERN} only: redact the {@link #selectedGroups} instead of the whole match. */
    public boolean useCaptureGroups = false;
    public List<Integer> selectedGroups = new ArrayList<>();

    public static RedactionRule create(String pattern, RuleTarget target, RedactionMode mode, MatchMode matchMode) {
        RedactionRule rule = new RedactionRule();
        rule.regex = pattern;
        rule.targets = EnumSet.of(target);
        rule.mode = mode;
        rule.matchMode = matchMode;
        return rule;
    }

    /** Redacts the whole value of each of these headers. */
    public static RedactionRule forHeaders(List<String> headers, RedactionMode mode) {
        RedactionRule rule = new RedactionRule();
        rule.type = RuleType.HEADER;
        rule.headers = new ArrayList<>(headers);
        rule.mode = mode;
        return rule;
    }

    public RedactionRule copy() {
        RedactionRule copy = new RedactionRule();
        copyInto(copy);
        copy.mode = mode;
        copy.color = color;
        copy.replacementText = replacementText;
        copy.useCaptureGroups = useCaptureGroups;
        copy.selectedGroups = new ArrayList<>(selectedGroups);
        return copy;
    }
}
