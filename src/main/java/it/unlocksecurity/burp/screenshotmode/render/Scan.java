package it.unlocksecurity.burp.screenshotmode.render;

/**
 * Scanning helpers shared by the pretty printers and {@link HttpSyntax}. Each runs in time linear in the characters it skips, so the
 * formatters stay linear on any input. Bodies are scanned by hand rather than with regular expressions: a pattern
 * like {@code <(?:"[^"]*"|[^">])*>} recurses once per character and overflows the stack on a long tag.
 */
public final class Scan {

    private Scan() {}

    /**
     * ASCII whitespace only. {@link Character#isWhitespace} also matches control characters (U+001C to U+001F),
     * which the formatters must show rather than treat as layout.
     */
    public static boolean isWhitespace(char c) {
        return c == ' ' || c == '\n' || c == '\t' || c == '\r' || c == '\f' || c == '\u000B';
    }

    /** @return {@code text} without leading and trailing {@linkplain #isWhitespace whitespace} */
    public static String strip(String text) {
        int start = skipWhitespace(text, 0);
        int end = text.length();
        while (end > start && isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    public static int skipWhitespace(String text, int from) {
        int i = from;
        while (i < text.length() && isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    /** Skips spaces and tabs only, staying on the same line. */
    public static int skipBlanks(String text, int from) {
        int i = from;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) {
            i++;
        }
        return i;
    }

    public static int newlines(String text, int from, int to) {
        int count = 0;
        for (int i = from; i < to; i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    public static boolean hasNewline(String text, int from, int to) {
        for (int i = from; i < to; i++) {
            if (text.charAt(i) == '\n') {
                return true;
            }
        }
        return false;
    }

    /** @return the index just past the first {@code token} at or after {@code from}, or the end of the text */
    public static int past(String text, String token, int from) {
        int index = text.indexOf(token, from);
        return index < 0 ? text.length() : index + token.length();
    }

    /**
     * End of a JSON, CSS or JavaScript string starting at {@code start}: just past the closing quote, or, for an
     * unterminated string, at the line break that ends it (JSON strings have been validated, so they never break).
     */
    public static int quotedEnd(String text, int start) {
        int end = closedQuoteEnd(text, start);
        return end >= 0 ? end : -end - 1;
    }

    /**
     * @return the index just past the closing quote of the string starting at {@code start}, or, when a line break
     *     or the end of the text comes first, {@code -1 - }the index where it stopped
     */
    public static int closedQuoteEnd(String text, int start) {
        char quote = text.charAt(start);
        for (int i = start + 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\') {
                i++;
            } else if (c == quote) {
                return i + 1;
            } else if (c == '\n') {
                return -1 - i;
            }
        }
        return -1 - text.length();
    }

    /** @return whether a comment starts on the same line, after optional spaces: it stays at the end of the line */
    public static boolean commentFollows(String text, int from, boolean lineComments) {
        int i = skipBlanks(text, from);
        return text.startsWith("/*", i) || (lineComments && text.startsWith("//", i));
    }

    public static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /** @return the end of the word (identifier, keyword or number) starting at {@code from} */
    public static int wordEnd(String text, int from) {
        int i = from;
        while (i < text.length() && isWordChar(text.charAt(i))) {
            i++;
        }
        return i;
    }

    public static boolean startsWithWord(String text, int from, String word) {
        return text.startsWith(word, from) && wordEnd(text, from) == from + word.length();
    }
}
