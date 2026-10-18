package it.unlocksecurity.burp.screenshotmode.model;

import java.util.UUID;

public final class HighlightRule {

    public static final String DEFAULT_COLOR = "#ffff00";

    public String id = UUID.randomUUID().toString();
    public String regex = "";
    public RuleTarget target = RuleTarget.REQUEST;
    public String color = DEFAULT_COLOR;
    public HighlightMode mode = HighlightMode.HIGHLIGHT;
    public MatchMode matchMode = MatchMode.REGEX;

    public static HighlightRule create(String pattern, RuleTarget target, MatchMode matchMode) {
        HighlightRule rule = new HighlightRule();
        rule.regex = pattern;
        rule.target = target;
        rule.matchMode = matchMode;
        return rule;
    }

    public HighlightRule copy() {
        HighlightRule copy = new HighlightRule();
        copy.id = id;
        copy.regex = regex;
        copy.target = target;
        copy.color = color;
        copy.mode = mode;
        copy.matchMode = matchMode;
        return copy;
    }
}
