package it.unlocksecurity.burp.screenshotmode.render.pretty;

import it.unlocksecurity.burp.screenshotmode.render.Scan;
import it.unlocksecurity.burp.screenshotmode.render.pretty.CodeWriter.Bracket;
import java.util.Arrays;
import java.util.Set;

/**
 * Re-indents JavaScript, mostly for minified code: a line break after each statement and around blocks, one property
 * per line in object literals, indentation by nesting. Only line breaks and indentation change, and the line breaks
 * of the source are kept. Strings, template literals, regular expressions and comments are copied as they are.
 *
 * <p>Like any formatter that doesn't parse the whole language, it relies on the previous token to tell a regular
 * expression from a division and an object literal from a block. A wrong guess only affects the layout.
 */
final class JavaScriptFormatter {

    /** Keywords followed by an expression: a {@code /} after them starts a regex, a <code>{</code> an object. */
    private static final Set<String> EXPRESSION_KEYWORDS = Set.of(
        "await",
        "case",
        "delete",
        "in",
        "instanceof",
        "new",
        "of",
        "return",
        "throw",
        "typeof",
        "void",
        "yield"
    );
    /** Keywords followed by a statement: a <code>{</code> after them is a block, a {@code /} starts a regex. */
    private static final Set<String> STATEMENT_KEYWORDS = Set.of("do", "else");
    /** Keywords that continue the statement of the block before them, so they stay on the line of its <code>}</code>. */
    private static final Set<String> BLOCK_CONTINUATIONS = Set.of("catch", "else", "finally");
    private static final String OPERATOR_CHARS = "+-*%=<>!&|^~?";

    private final String code;
    private final IndentWriter out;
    private final CodeWriter writer;
    /** Whether a {@code /} at this point starts a regular expression rather than a division. */
    private boolean regexAllowed = true;
    /** Whether a <code>{</code> at this point starts an object literal rather than a block. */
    private boolean objectAllowed;
    /** No regex literal can end before this index: an earlier attempt ran into the end of the line. */
    private int noRegexBefore;

    private JavaScriptFormatter(String code, IndentWriter out) {
        this.code = code;
        this.out = out;
        this.writer = new CodeWriter(out);
    }

    static String format(String code) {
        IndentWriter out = new IndentWriter();
        write(code, out);
        return out.toString();
    }

    /** Writes {@code code} at the current indentation of {@code out}. */
    static void write(String code, IndentWriter out) {
        new JavaScriptFormatter(code, out).run();
    }

    private void run() {
        int length = code.length();
        int i = 0;
        while (i < length) {
            char c = code.charAt(i);
            if (Scan.isWhitespace(c)) {
                i = writer.whitespace(code, i);
            } else if (c == '/' && code.startsWith("/", i + 1)) {
                // Up to the line break, which the next whitespace run turns into a line break of the output.
                int end = code.indexOf('\n', i);
                end = end < 0 ? length : end;
                writer.comment(code, i, end, true);
                i = end;
            } else if (c == '/' && code.startsWith("*", i + 1)) {
                int end = Scan.past(code, "*/", i + 2);
                writer.comment(code, i, end, true);
                i = end;
            } else if (c == '/') {
                i = slash(i);
            } else if (c == '"' || c == '\'') {
                i = operand(i, Scan.quotedEnd(code, i));
            } else if (c == '`') {
                i = operand(i, templateEnd(i));
            } else if (Scan.isWordChar(c)) {
                i = word(i);
            } else if (OPERATOR_CHARS.indexOf(c) >= 0) {
                i = operator(i);
            } else {
                i = punctuation(c, i);
            }
        }
    }

    private int copy(int from, int to) {
        out.write(code, from, to);
        return to;
    }

    private int operand(int from, int to) {
        regexAllowed = false;
        objectAllowed = false;
        return copy(from, to);
    }

    private int word(int from) {
        int end = Scan.wordEnd(code, from);
        String word = code.substring(from, end);
        boolean expression = EXPRESSION_KEYWORDS.contains(word);
        regexAllowed = expression || STATEMENT_KEYWORDS.contains(word);
        objectAllowed = expression;
        return copy(from, end);
    }

    /** A run of operator characters, such as {@code ===}, {@code =>}, {@code ++} or {@code ?.}. */
    private int operator(int from) {
        int end = from;
        while (end < code.length() && OPERATOR_CHARS.indexOf(code.charAt(end)) >= 0) {
            end++;
        }
        String operator = code.substring(from, end);
        boolean postfix = operator.endsWith("++") || operator.endsWith("--");
        regexAllowed = !postfix;
        // An arrow function's body is a block: x => { ... }.
        objectAllowed = !postfix && !operator.endsWith("=>");
        return copy(from, end);
    }

    private int slash(int from) {
        if (regexAllowed && from >= noRegexBefore) {
            int end = regexEnd(from);
            if (end > 0) {
                return operand(from, end);
            }
            noRegexBefore = -end;
        }
        regexAllowed = true;
        objectAllowed = true;
        return copy(from, from + 1);
    }

    /** @return the index after the punctuation */
    private int punctuation(char c, int at) {
        switch (c) {
            case '{' -> {
                return openBrace(at);
            }
            case '}' -> closeBrace(at);
            case '(' -> {
                out.write(c);
                writer.push(Bracket.PAREN);
                regexAllowed = true;
                objectAllowed = true;
            }
            case '[' -> {
                out.write(c);
                // An array of objects or arrays gets one element per line, like JSON; [1,2] stays on its line.
                int next = Scan.skipWhitespace(code, at + 1);
                boolean list = next < code.length() && (code.charAt(next) == '{' || code.charAt(next) == '[');
                writer.push(list ? Bracket.LIST : Bracket.SQUARE);
                if (list) {
                    breakAfter(at + 1);
                }
                regexAllowed = true;
                objectAllowed = true;
            }
            case ')', ']' -> {
                if (writer.close(c) == Bracket.LIST) {
                    out.newline();
                }
                out.write(c);
                regexAllowed = false;
                objectAllowed = false;
            }
            case ';' -> {
                out.write(c);
                // Not between the parentheses of for (;;).
                if (writer.top() == null || writer.top() == Bracket.BLOCK) {
                    breakAfter(at + 1);
                }
                regexAllowed = true;
                objectAllowed = false;
            }
            case ',' -> {
                out.write(c);
                if (writer.top() == Bracket.OBJECT || writer.top() == Bracket.LIST) {
                    breakAfter(at + 1);
                }
                regexAllowed = true;
                objectAllowed = true;
            }
            case ':' -> {
                out.write(c);
                regexAllowed = true;
                // A property value or a ternary branch, but a block after a label or a case.
                objectAllowed = writer.top() != null && writer.top() != Bracket.BLOCK;
            }
            default -> {
                out.write(c);
                regexAllowed = false;
                objectAllowed = false;
            }
        }
        return at + 1;
    }

    /** @return the index after the brace, or, for empty braces, of the closing one */
    private int openBrace(int at) {
        out.write('{');
        writer.push(objectAllowed ? Bracket.OBJECT : Bracket.BLOCK);
        regexAllowed = true;
        objectAllowed = false;
        int next = Scan.skipWhitespace(code, at + 1);
        if (code.startsWith("}", next)) {
            closeBrace(next, false);
            return next + 1;
        }
        breakAfter(at + 1);
        return at + 1;
    }

    private void closeBrace(int at) {
        closeBrace(at, true);
    }

    private void closeBrace(int at, boolean ownLine) {
        Bracket closed = writer.close('}');
        if (ownLine) {
            out.newline();
        }
        out.write('}');
        boolean block = closed != Bracket.OBJECT;
        regexAllowed = block;
        objectAllowed = false;
        int next = Scan.skipBlanks(code, at + 1);
        boolean continues =
            next < code.length() &&
            (")]},;.:?=(".indexOf(code.charAt(next)) >= 0 ||
                BLOCK_CONTINUATIONS.stream().anyMatch(word -> Scan.startsWithWord(code, next, word)));
        // An object literal is part of an expression: what follows stays on its line.
        if (block && !continues) {
            breakAfter(at + 1);
        }
    }

    private void breakAfter(int from) {
        writer.breakAfter(code, from, true);
    }

    /**
     * @return the end of the regular expression literal (flags included) starting at {@code from}, or, when it
     *     reaches the end of the line first, minus the index where it stopped
     */
    private int regexEnd(int from) {
        boolean inClass = false;
        for (int i = from + 1; i < code.length(); i++) {
            char c = code.charAt(i);
            if (c == '\n') {
                return -i;
            } else if (c == '\\') {
                if (i + 1 < code.length() && code.charAt(i + 1) == '\n') {
                    return -(i + 1);
                }
                i++;
            } else if (c == '[') {
                inClass = true;
            } else if (c == ']') {
                inClass = false;
            } else if (c == '/' && !inClass) {
                return Scan.wordEnd(code, i + 1);
            }
        }
        return -code.length();
    }

    /** @return the end of the template literal starting at {@code from}, including nested {@code ${...}} */
    private int templateEnd(int from) {
        // Per nesting level: -1 inside template text, otherwise the open braces inside a ${...} expression.
        int[] stack = new int[8];
        int depth = 0;
        stack[depth++] = -1;
        int i = from + 1;
        while (i < code.length() && depth > 0) {
            char c = code.charAt(i);
            int top = stack[depth - 1];
            if (top < 0) {
                if (c == '\\') {
                    i += 2;
                    continue;
                } else if (c == '`') {
                    depth--;
                } else if (c == '$' && code.startsWith("{", i + 1)) {
                    stack = push(stack, depth++, 0);
                    i++;
                }
                i++;
            } else if (c == '"' || c == '\'') {
                i = Scan.quotedEnd(code, i);
            } else if (c == '/' && code.startsWith("*", i + 1)) {
                i = Scan.past(code, "*/", i + 2);
            } else {
                if (c == '{') {
                    stack[depth - 1]++;
                } else if (c == '}') {
                    if (top == 0) {
                        depth--;
                    } else {
                        stack[depth - 1]--;
                    }
                } else if (c == '`') {
                    stack = push(stack, depth++, -1);
                }
                i++;
            }
        }
        return Math.min(i, code.length());
    }

    private static int[] push(int[] stack, int depth, int value) {
        int[] grown = depth == stack.length ? Arrays.copyOf(stack, depth * 2) : stack;
        grown[depth] = value;
        return grown;
    }
}
