package it.unlocksecurity.burp.screenshotmode.model;

/** What a rule matches: chosen when the rule is added. */
public enum RuleType implements Labeled {
    /** The value of the named headers (a highlight marks the whole header line), in requests and responses. */
    HEADER("header", "Header"),
    /** A literal or regex pattern, in the messages and URL the rule targets. */
    PATTERN("pattern", "Text pattern");

    private final String id;
    private final String label;

    RuleType(String id, String label) {
        this.id = id;
        this.label = label;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
