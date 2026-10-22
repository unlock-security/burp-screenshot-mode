package it.unlocksecurity.burp.screenshotmode.render.pretty;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import it.unlocksecurity.burp.screenshotmode.render.Scan;
import java.io.IOException;
import java.io.StringReader;

/**
 * Puts every member of a JSON object or array on its own line. Gson only validates the body: the output is the
 * body itself, with line breaks and indentation added, so escapes and number literals stay exactly as sent.
 */
final class JsonFormatter {

    private JsonFormatter() {}

    /** @return {@code true} when {@code body} is a single JSON object or array */
    static boolean accepts(String body) {
        if (!body.startsWith("{") && !body.startsWith("[")) {
            return false;
        }
        try {
            JsonReader reader = new JsonReader(new StringReader(body));
            reader.setStrictness(Strictness.STRICT);
            reader.skipValue();
            return reader.peek() == JsonToken.END_DOCUMENT;
        } catch (IOException | IllegalStateException e) {
            return false;
        }
    }

    /** @return the formatted JSON, or {@code null} when {@code body} is not a single JSON object or array */
    static String format(String body) {
        if (!accepts(body)) {
            return null;
        }
        IndentWriter out = new IndentWriter();
        write(body, out);
        return out.toString();
    }

    /** Writes {@code body}, which must be {@linkplain #accepts accepted}, at the current indentation of {@code out}. */
    static void write(String body, IndentWriter out) {
        int length = body.length();
        for (int i = 0; i < length; i++) {
            char c = body.charAt(i);
            switch (c) {
                case '"' -> {
                    int end = Scan.quotedEnd(body, i);
                    out.write(body, i, end);
                    i = end - 1;
                }
                case '{', '[' -> {
                    int next = Scan.skipWhitespace(body, i + 1);
                    out.write(c);
                    if (body.charAt(next) == (c == '{' ? '}' : ']')) {
                        out.write(body.charAt(next));
                        i = next;
                    } else {
                        out.indent();
                        out.newline();
                    }
                }
                case '}', ']' -> {
                    out.dedent();
                    out.newline();
                    out.write(c);
                }
                case ',' -> {
                    out.write(c);
                    out.newline();
                }
                default -> {
                    if (Scan.isWhitespace(c)) {
                        // Spacing within a line ("key": value) is kept; existing line breaks are replaced by ours.
                        int next = Scan.skipWhitespace(body, i);
                        if (!out.atLineStart() && !Scan.hasNewline(body, i, next)) {
                            out.write(body, i, next);
                        }
                        i = next - 1;
                    } else {
                        out.write(c);
                    }
                }
            }
        }
    }
}
