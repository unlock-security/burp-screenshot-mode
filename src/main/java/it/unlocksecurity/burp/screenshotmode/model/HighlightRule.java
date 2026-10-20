package it.unlocksecurity.burp.screenshotmode.model;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class HighlightRule extends Rule {

    public static final String DEFAULT_COLOR = "#ffff00";

    public String color = DEFAULT_COLOR;
    public HighlightMode mode = HighlightMode.HIGHLIGHT;

    public static HighlightRule create(String pattern, RuleTarget target, MatchMode matchMode) {
        HighlightRule rule = new HighlightRule();
        rule.regex = pattern;
        rule.targets = EnumSet.of(target);
        rule.matchMode = matchMode;
        return rule;
    }

    /** Highlights the whole line of each of these headers. */
    public static HighlightRule forHeaders(List<String> headers) {
        HighlightRule rule = new HighlightRule();
        rule.type = RuleType.HEADER;
        rule.headers = new ArrayList<>(headers);
        return rule;
    }

    public HighlightRule copy() {
        HighlightRule copy = new HighlightRule();
        copyInto(copy);
        copy.color = color;
        copy.mode = mode;
        return copy;
    }
}
