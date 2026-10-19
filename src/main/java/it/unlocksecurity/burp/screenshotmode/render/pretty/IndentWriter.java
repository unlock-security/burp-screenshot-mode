package it.unlocksecurity.burp.screenshotmode.render.pretty;

import it.unlocksecurity.burp.screenshotmode.render.MessageText;

/**
 * Output of the pretty printers. They copy every character of the source and only call {@link #newline()} and
 * {@link #indent()}: line breaks and indentation are written lazily, before the first character of each line, so
 * whitespace at the start of a source line can simply be skipped to re-indent it, and the output never ends with a
 * line break.
 *
 * <p>Output stops growing at {@link MessageText#MAX_CHARS} (the rest is only counted, for the truncation note), so
 * deeply nested input can't blow up into megabytes of indentation.
 */
final class IndentWriter {

    private static final String INDENT = "  ";

    private final StringBuilder out = new StringBuilder();
    private final int limit;
    private long dropped;
    private int depth;
    private boolean lineStart = true;
    /** Line breaks to write before the next character: none at the very start, 2 for a blank line. */
    private int pendingBreaks;

    IndentWriter() {
        this(MessageText.MAX_CHARS);
    }

    IndentWriter(int limit) {
        this.limit = limit;
    }

    void indent() {
        depth++;
    }

    void dedent() {
        depth = Math.max(0, depth - 1);
    }

    /** @return whether nothing but (pending) indentation has been written on the current line */
    boolean atLineStart() {
        return lineStart;
    }

    /** Ends the current line, unless it is still empty. */
    void newline() {
        if (!lineStart) {
            lineStart = true;
            pendingBreaks = 1;
        }
    }

    /** Ends the current line and leaves one empty line, unless at the start of the output. */
    void blankLine() {
        newline();
        if (pendingBreaks > 0) {
            pendingBreaks = 2;
        }
    }

    void write(char c) {
        startLine();
        if (dropped == 0 && out.length() < limit) {
            out.append(c);
        } else {
            dropped++;
        }
        lineStart = false;
    }

    void write(CharSequence text, int from, int to) {
        if (from >= to) {
            return;
        }
        startLine();
        int room = dropped > 0 ? 0 : Math.max(0, Math.min(to - from, limit - out.length()));
        out.append(text, from, from + room);
        dropped += to - from - room;
        lineStart = false;
    }

    void write(CharSequence text) {
        write(text, 0, text.length());
    }

    /** @return the output, with {@link MessageText#truncationNote} when it hit the limit */
    @Override
    public String toString() {
        return dropped == 0 ? out.toString() : out + MessageText.truncationNote(dropped);
    }

    private void startLine() {
        if (lineStart) {
            if (pendingBreaks > 0 && dropped == 0) {
                stripTrailingBlanks();
            }
            append("\n".repeat(pendingBreaks));
            pendingBreaks = 0;
            if (dropped > 0) {
                dropped += (long) depth * INDENT.length();
            } else {
                for (int i = 0; i < depth && dropped == 0; i++) {
                    append(INDENT);
                }
            }
        }
    }

    private void append(String text) {
        if (dropped == 0 && out.length() + text.length() <= limit) {
            out.append(text);
        } else {
            dropped += text.length();
        }
    }

    private void stripTrailingBlanks() {
        int end = out.length();
        while (end > 0 && (out.charAt(end - 1) == ' ' || out.charAt(end - 1) == '\t')) {
            end--;
        }
        out.setLength(end);
    }
}
