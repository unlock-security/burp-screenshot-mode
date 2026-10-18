package it.unlocksecurity.burp.screenshotmode.render;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pretty prints JSON and markup bodies, like the "Pretty" tab of Burp's message editor. Not part of the Caido
 * plugin. Only line breaks and indentation change: the tokens and the spacing within a line are kept as they are,
 * so rules written against the raw message still match. Bodies that do not parse are returned unchanged.
 */
public final class PrettyPrinter {

    private static final String INDENT = "  ";
    /** Elements without a closing tag in HTML. */
    private static final Set<String> VOID_ELEMENTS = Set.of(
        "area",
        "base",
        "br",
        "col",
        "embed",
        "hr",
        "img",
        "input",
        "link",
        "meta",
        "param",
        "source",
        "track",
        "wbr"
    );
    /** HTML elements whose content is kept verbatim: it is either not markup or whitespace-sensitive. */
    private static final Set<String> VERBATIM_ELEMENTS = Set.of("script", "style", "pre", "textarea");
    private static final Pattern MARKUP_ITEM = Pattern.compile(
        "<!--[\\s\\S]*?-->|<!\\[CDATA\\[[\\s\\S]*?]]>|<(?:\"[^\"]*\"|'[^']*'|[^'\">])*>"
    );
    private static final Pattern TAG_NAME = Pattern.compile("^</?([A-Za-z][^\\s/>]*)");

    private PrettyPrinter() {}

    /** @param text message text with {@code \n} line breaks; the start line and headers are never touched */
    public static String format(String text) {
        int headersEnd = text.indexOf("\n\n");
        if (headersEnd < 0) {
            return text;
        }
        String contentType = "";
        for (String header : text.substring(0, headersEnd).split("\n")) {
            int colon = header.indexOf(':');
            if (colon > 0 && header.substring(0, colon).trim().equalsIgnoreCase("content-type")) {
                contentType = header
                    .substring(colon + 1)
                    .trim()
                    .toLowerCase(Locale.ROOT);
            }
        }

        String body = text.substring(headersEnd + 2);
        String pretty = switch (HttpSyntax.BodyKind.of(contentType, body)) {
            case JSON -> json(body.strip());
            case MARKUP -> markup(body.strip(), isHtml(contentType, body));
            case FORM, OTHER -> null;
        };
        return pretty == null ? text : MessageText.truncate(text.substring(0, headersEnd + 2) + pretty);
    }

    /** @return the formatted JSON, or {@code null} when {@code body} is not a single JSON object or array */
    static String json(String body) {
        if (!body.startsWith("{") && !body.startsWith("[")) {
            return null;
        }
        try {
            JsonReader reader = new JsonReader(new StringReader(body));
            reader.setStrictness(Strictness.STRICT);
            reader.skipValue();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                return null;
            }
        } catch (IOException | IllegalStateException e) {
            return null;
        }

        StringBuilder out = new StringBuilder(body.length() * 2);
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\\') {
                    out.append(body.charAt(++i));
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    inString = true;
                    out.append(c);
                }
                case '{', '[' -> {
                    int next = skipWhitespace(body, i + 1);
                    if (body.charAt(next) == (c == '{' ? '}' : ']')) {
                        out.append(c).append(body.charAt(next));
                        i = next;
                    } else {
                        out.append(c);
                        newline(out, ++depth);
                        i = next - 1;
                    }
                }
                case '}', ']' -> {
                    stripTrailingWhitespace(out);
                    newline(out, --depth);
                    out.append(c);
                }
                case ',' -> {
                    out.append(c);
                    newline(out, depth);
                    i = skipWhitespace(body, i + 1) - 1;
                }
                default -> {
                    if (Character.isWhitespace(c)) {
                        // Spacing within a line ("key": value) is kept; existing line breaks are replaced by ours.
                        int next = skipWhitespace(body, i);
                        if (body.substring(i, next).indexOf('\n') < 0) {
                            out.append(body, i, next);
                        }
                        i = next - 1;
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }

    /**
     * Puts every tag, comment and text node on its own line, indented by nesting depth. An element holding only
     * text stays on one line. Unbalanced markup (common in HTML) only skews the indentation.
     *
     * @return the formatted markup, or {@code null} when {@code body} has no tags
     */
    static String markup(String body, boolean html) {
        List<String> items = new ArrayList<>();
        Matcher matcher = MARKUP_ITEM.matcher(body);
        int position = 0;
        while (matcher.find()) {
            addText(items, body.substring(position, matcher.start()));
            String item = matcher.group();
            String name = tagName(item);
            if (html && name != null && !item.startsWith("</") && VERBATIM_ELEMENTS.contains(name)) {
                int close = body.toLowerCase(Locale.ROOT).indexOf("</" + name, matcher.end());
                if (close >= 0) {
                    int end = body.indexOf('>', close);
                    end = end < 0 ? body.length() : end + 1;
                    items.add(body.substring(matcher.start(), end));
                    position = end;
                    matcher.region(end, body.length());
                    continue;
                }
            }
            items.add(item);
            position = matcher.end();
        }
        addText(items, body.substring(position));
        if (items.stream().noneMatch(item -> item.startsWith("<"))) {
            return null;
        }

        StringBuilder out = new StringBuilder(body.length() * 2);
        int depth = 0;
        for (int i = 0; i < items.size(); i++) {
            String item = items.get(i);
            String name = tagName(item);
            boolean opens =
                name != null &&
                !item.startsWith("</") &&
                !item.endsWith("/>") &&
                !(html && VOID_ELEMENTS.contains(name)) &&
                !(html && VERBATIM_ELEMENTS.contains(name));
            if (name != null && item.startsWith("</")) {
                depth = Math.max(0, depth - 1);
            }
            if (!out.isEmpty()) {
                newline(out, depth);
            }
            out.append(item);
            if (
                opens &&
                i + 2 < items.size() &&
                !items.get(i + 1).startsWith("<") &&
                name.equalsIgnoreCase(closingTagName(items.get(i + 2)))
            ) {
                out.append(items.get(i + 1)).append(items.get(i + 2));
                i += 2;
            } else if (opens) {
                depth++;
            }
        }
        return out.toString();
    }

    private static boolean isHtml(String contentType, String body) {
        if (contentType.contains("html")) {
            return true;
        }
        String start = body.stripLeading().toLowerCase(Locale.ROOT);
        return start.startsWith("<!doctype html") || start.startsWith("<html");
    }

    private static void addText(List<String> items, String text) {
        String trimmed = text.strip();
        if (!trimmed.isEmpty()) {
            items.add(trimmed);
        }
    }

    /** Lowercase name of an opening or closing tag, or {@code null} for text, comments, CDATA and declarations. */
    private static String tagName(String item) {
        Matcher matcher = TAG_NAME.matcher(item);
        return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
    }

    private static String closingTagName(String item) {
        return item.startsWith("</") ? tagName(item) : null;
    }

    private static int skipWhitespace(String text, int from) {
        int i = from;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static void stripTrailingWhitespace(StringBuilder out) {
        int end = out.length();
        while (end > 0 && (out.charAt(end - 1) == ' ' || out.charAt(end - 1) == '\t')) {
            end--;
        }
        out.setLength(end);
    }

    private static void newline(StringBuilder out, int depth) {
        stripTrailingWhitespace(out);
        out.append('\n').append(INDENT.repeat(Math.max(0, depth)));
    }
}
