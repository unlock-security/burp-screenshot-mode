package it.unlocksecurity.burp.screenshotmode.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A redaction rule. {@link #color} only applies to {@link RedactionMode#OPAQUE} and {@link #replacementText} only
 * to {@link RedactionMode#REPLACE}; both are kept in memory so switching mode back and forth doesn't lose them.
 */
public final class RedactionRule {

    public static final String DEFAULT_COLOR = "#000000";
    public static final String DEFAULT_REPLACEMENT = "[REDACTED]";

    public String id = UUID.randomUUID().toString();
    public String regex = "";
    public RuleTarget target = RuleTarget.REQUEST;
    public RedactionMode mode = RedactionMode.BLUR;
    public String color = DEFAULT_COLOR;
    public String replacementText = DEFAULT_REPLACEMENT;
    public boolean useCaptureGroups = false;
    public List<Integer> selectedGroups = new ArrayList<>();
    public MatchMode matchMode = MatchMode.REGEX;

    public static RedactionRule create(String pattern, RuleTarget target, RedactionMode mode, MatchMode matchMode) {
        RedactionRule rule = new RedactionRule();
        rule.regex = pattern;
        rule.target = target;
        rule.mode = mode;
        rule.matchMode = matchMode;
        return rule;
    }

    public RedactionRule copy() {
        RedactionRule copy = new RedactionRule();
        copy.id = id;
        copy.regex = regex;
        copy.target = target;
        copy.mode = mode;
        copy.color = color;
        copy.replacementText = replacementText;
        copy.useCaptureGroups = useCaptureGroups;
        copy.selectedGroups = new ArrayList<>(selectedGroups);
        copy.matchMode = matchMode;
        return copy;
    }
}
