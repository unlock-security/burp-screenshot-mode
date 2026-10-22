package it.unlocksecurity.burp.screenshotmode.render.pretty;

import it.unlocksecurity.burp.screenshotmode.render.Scan;
import it.unlocksecurity.burp.screenshotmode.render.pretty.CodeWriter.Bracket;

/**
 * Puts every rule and declaration of a style sheet on its own line, indented by block nesting ({@code @media}, CSS
 * nesting). Only line breaks and indentation change: {@code a{color:red}} becomes three lines, with no space added.
 */
final class CssFormatter {

    private CssFormatter() {}

    static String format(String css) {
        IndentWriter out = new IndentWriter();
        write(css, out);
        return out.toString();
    }

    /** Writes {@code css} at the current indentation of {@code out}. */
    static void write(String css, IndentWriter out) {
        CodeWriter code = new CodeWriter(out);
        int length = css.length();
        int i = 0;
        while (i < length) {
            char c = css.charAt(i);
            if (Scan.isWhitespace(c)) {
                i = code.whitespace(css, i);
                continue;
            }
            int end = i + 1;
            if (c == '/' && css.startsWith("*", i + 1)) {
                end = Scan.past(css, "*/", i + 2);
                code.comment(css, i, end, false);
            } else if (c == '"' || c == '\'') {
                end = Scan.quotedEnd(css, i);
                out.write(css, i, end);
            } else if (c == '\\') {
                // An escape such as \{ or \; is part of an identifier, not punctuation.
                end = Math.min(length, i + 2);
                out.write(css, i, end);
            } else if (c == '{') {
                out.write(c);
                int next = Scan.skipWhitespace(css, end);
                if (css.startsWith("}", next)) {
                    out.write('}');
                    end = next + 1;
                    code.breakAfter(css, end, false);
                } else {
                    code.push(Bracket.BLOCK);
                    code.breakAfter(css, end, false);
                }
            } else if (c == '}') {
                code.close('}');
                out.newline();
                out.write(c);
                code.breakAfter(css, end, false);
            } else if (c == ';') {
                out.write(c);
                // Not inside parentheses: url(data:image/png;base64,...).
                if (code.top() == null || code.top() == Bracket.BLOCK) {
                    code.breakAfter(css, end, false);
                }
            } else if (c == '(' || c == '[') {
                out.write(c);
                code.push(c == '(' ? Bracket.PAREN : Bracket.SQUARE);
            } else if (c == ')' || c == ']') {
                code.close(c);
                out.write(c);
            } else {
                out.write(c);
            }
            i = end;
        }
    }
}
