package it.unlocksecurity.burp.screenshotmode.render;

/**
 * What one rule did in one {@link RuleEngine#apply} call.
 *
 * @param matches how many times it matched
 * @param problem why it did not fully apply, or {@code null} if it did
 * @param detail  the regex syntax error for {@link Problem#INVALID}, {@code null} otherwise
 */
public record RuleStatus(int matches, Problem problem, String detail) {
    public enum Problem {
        /** The pattern is not a valid regular expression: the rule was not applied. */
        INVALID,
        /** Matching stopped early (time budget or stack overflow): only the matches found until then were applied. */
        STOPPED,
    }

    public boolean failed() {
        return problem != null;
    }
}
