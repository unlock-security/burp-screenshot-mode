package it.unlocksecurity.burp.screenshotmode.render.pretty;

import it.unlocksecurity.burp.screenshotmode.render.Scan;
import java.util.Arrays;

/**
 * Output of the CSS and JavaScript formatters: an {@link IndentWriter} plus the stack of open brackets, which
 * decides the indentation. Braces always indent their content; parentheses and square brackets only once the source
 * breaks a line inside them, like a multi-line argument list.
 */
final class CodeWriter {

    enum Bracket {
        BLOCK,
        OBJECT,
        PAREN,
        SQUARE,
        /** Square brackets laid out one element per line, like an array of objects. */
        LIST,
    }

    final IndentWriter out;
    private Bracket[] stack = new Bracket[32];
    private boolean[] indented = new boolean[32];
    private int size;
    /** Open brackets per kind, so an unmatched closer is ignored without searching the stack. */
    private final int[] open = new int[Bracket.values().length];
    /** A line break postponed until after the comment that follows on the same line. */
    private boolean breakAfterComment;

    CodeWriter(IndentWriter out) {
        this.out = out;
    }

    /** @return the innermost open bracket, or {@code null} at the top level */
    Bracket top() {
        return size == 0 ? null : stack[size - 1];
    }

    void push(Bracket bracket) {
        if (size == stack.length) {
            stack = Arrays.copyOf(stack, size * 2);
            indented = Arrays.copyOf(indented, size * 2);
        }
        boolean indents = bracket == Bracket.BLOCK || bracket == Bracket.OBJECT || bracket == Bracket.LIST;
        stack[size] = bracket;
        indented[size] = indents;
        size++;
        open[bracket.ordinal()]++;
        if (indents) {
            out.indent();
        }
    }

    /**
     * Closes the innermost bracket matching {@code closer} ({@code }}, {@code )} or {@code ]}), and any bracket left
     * open inside it.
     *
     * @return the kind of bracket closed, or {@code null} when none matches
     */
    Bracket close(char closer) {
        int matching = switch (closer) {
            case '}' -> open[Bracket.BLOCK.ordinal()] + open[Bracket.OBJECT.ordinal()];
            case ')' -> open[Bracket.PAREN.ordinal()];
            default -> open[Bracket.SQUARE.ordinal()] + open[Bracket.LIST.ordinal()];
        };
        if (matching == 0) {
            return null;
        }
        while (true) {
            Bracket popped = stack[--size];
            open[popped.ordinal()]--;
            if (indented[size]) {
                out.dedent();
            }
            if (closes(closer, popped)) {
                return popped;
            }
        }
    }

    private static boolean closes(char closer, Bracket bracket) {
        return switch (bracket) {
            case BLOCK, OBJECT -> closer == '}';
            case PAREN -> closer == ')';
            case SQUARE, LIST -> closer == ']';
        };
    }

    /**
     * Ends the line after the token that ends at {@code from}, or, when a comment follows on the same line, after
     * that comment.
     */
    void breakAfter(String code, int from, boolean lineComments) {
        if (Scan.commentFollows(code, from, lineComments)) {
            breakAfterComment = true;
        } else {
            out.newline();
        }
    }

    /** Copies the comment between {@code from} and {@code to}. */
    void comment(String code, int from, int to, boolean lineComments) {
        out.write(code, from, to);
        if (breakAfterComment) {
            breakAfterComment = false;
            breakAfter(code, to, lineComments);
        }
    }

    /**
     * Copies a run of whitespace starting at {@code from}. Spacing within a line is kept, the indentation of source
     * lines is replaced by ours, and source line breaks are kept (at most one blank line in a row): in CSS and
     * JavaScript, unlike JSON, a line break can be what separates two tokens.
     *
     * @return the end of the run
     */
    int whitespace(String code, int from) {
        int end = Scan.skipWhitespace(code, from);
        int lines = Scan.newlines(code, from, end);
        if (lines == 0) {
            if (!out.atLineStart()) {
                out.write(code, from, end);
            }
        } else {
            breakAfterComment = false;
            if (lines > 1) {
                out.blankLine();
            } else {
                out.newline();
            }
            if (size > 0 && !indented[size - 1]) {
                indented[size - 1] = true;
                out.indent();
            }
        }
        return end;
    }
}
