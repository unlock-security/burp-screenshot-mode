package it.unlocksecurity.burp.screenshotmode.model;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** What highlight and redaction rules have in common: a name, an on/off switch and what they match. */
public abstract sealed class Rule permits HighlightRule, RedactionRule {

    public String id = UUID.randomUUID().toString();
    /** Optional: an unnamed rule is shown by its pattern or headers. */
    public String name = "";
    public boolean enabled = true;
    public RuleType type = RuleType.PATTERN;
    /** {@link RuleType#HEADER} only: header names, matched ignoring case. */
    public List<String> headers = new ArrayList<>();
    /** {@link RuleType#PATTERN} only. */
    public Set<RuleTarget> targets = EnumSet.of(RuleTarget.REQUEST);
    /** {@link RuleType#PATTERN} only. */
    public String regex = "";
    /** {@link RuleType#PATTERN} only. */
    public MatchMode matchMode = MatchMode.REGEX;

    /** Whether the rule is on and matches in {@code target}: header rules match the headers of both messages. */
    public boolean appliesTo(RuleTarget target) {
        if (!enabled) {
            return false;
        }
        return type == RuleType.HEADER ? target != RuleTarget.URL : targets.contains(target);
    }

    protected void copyInto(Rule copy) {
        copy.id = id;
        copy.name = name;
        copy.enabled = enabled;
        copy.type = type;
        copy.headers = new ArrayList<>(headers);
        copy.targets = targets.isEmpty() ? EnumSet.noneOf(RuleTarget.class) : EnumSet.copyOf(targets);
        copy.regex = regex;
        copy.matchMode = matchMode;
    }
}
