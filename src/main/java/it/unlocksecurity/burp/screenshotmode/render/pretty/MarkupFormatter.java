package it.unlocksecurity.burp.screenshotmode.render.pretty;

import it.unlocksecurity.burp.screenshotmode.render.Scan;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Puts every tag, comment and text node of HTML or XML on its own line, indented by nesting depth. An element holding
 * only text stays on one line. Tags, attributes, entities and text are copied as they are: unlike an HTML parser,
 * nothing is "fixed", so broken or malicious markup shows exactly as sent. Unbalanced markup only skews the
 * indentation. In HTML, the content of {@code <script>} and {@code <style>} is formatted as JavaScript (or JSON) and
 * CSS.
 */
final class MarkupFormatter {

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
    /**
     * HTML elements whose content runs to their end tag: either not markup ({@code script}, {@code style}, text in
     * {@code textarea} and {@code title}) or whitespace-sensitive ({@code pre}).
     */
    private static final Set<String> RAW_ELEMENTS = Set.of("script", "style", "textarea", "title", "pre", "xmp");
    /** Applied to a single start tag; no nested quantifiers, so it runs in linear time. */
    private static final Pattern TYPE_ATTRIBUTE = Pattern.compile(
        "\\stype\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))",
        Pattern.CASE_INSENSITIVE
    );

    private enum Kind {
        OPEN,
        CLOSE,
        /** Self-contained: void element, self-closing tag, comment, CDATA, doctype, processing instruction. */
        LEAF,
        TEXT,
        /** An element whose content is not split: start tag, content, end tag (missing when unterminated). */
        RAW,
    }

    private enum Content {
        VERBATIM,
        JAVASCRIPT,
        JSON,
        CSS,
    }

    /**
     * @param contentFrom for {@link Kind#RAW}, the end of the start tag
     * @param contentTo   for {@link Kind#RAW}, the start of the end tag
     */
    private record Item(Kind kind, int from, int to, String name, int contentFrom, int contentTo, Content content) {
        static Item of(Kind kind, int from, int to, String name) {
            return new Item(kind, from, to, name, to, to, Content.VERBATIM);
        }
    }

    private final String body;
    private final boolean html;
    private final List<Item> items = new ArrayList<>();

    private MarkupFormatter(String body, boolean html) {
        this.body = body;
        this.html = html;
    }

    /** @return the formatted markup, or {@code null} when {@code body} has no tags */
    static String format(String body, boolean html) {
        IndentWriter out = new IndentWriter();
        return write(body, html, out) ? out.toString() : null;
    }

    /** @return {@code false}, without writing anything, when {@code body} has no tags */
    static boolean write(String body, boolean html, IndentWriter out) {
        MarkupFormatter formatter = new MarkupFormatter(body, html);
        formatter.tokenize();
        if (formatter.items.stream().allMatch(item -> item.kind() == Kind.TEXT)) {
            return false;
        }
        formatter.write(out);
        return true;
    }

    private void tokenize() {
        int length = body.length();
        int textStart = 0;
        int i = 0;
        while (i < length) {
            int lt = body.indexOf('<', i);
            if (lt < 0) {
                break;
            }
            int end = markupEnd(lt);
            if (end == lt) {
                // Not markup, like the "<" of "a < b".
                i = lt + 1;
                continue;
            }
            addText(textStart, lt);
            if (end < 0) {
                // Unterminated tag or comment: the rest is shown as it is. Stopping here keeps the scan linear.
                addText(lt, length);
                return;
            }
            addMarkup(lt, end);
            i = textStart = items.get(items.size() - 1).to();
        }
        addText(textStart, length);
    }

    /** @return the end of the markup starting at {@code lt}, {@code lt} when it isn't markup, -1 when unterminated */
    private int markupEnd(int lt) {
        if (body.startsWith("<!--", lt)) {
            return terminated(body.indexOf("-->", lt + 4), 3);
        } else if (body.startsWith("<![CDATA[", lt)) {
            return terminated(body.indexOf("]]>", lt + 9), 3);
        } else if (body.startsWith("<?", lt)) {
            return html ? terminated(body.indexOf('>', lt), 1) : terminated(body.indexOf("?>", lt + 2), 2);
        } else if (body.startsWith("<!", lt)) {
            return declarationEnd(lt + 2);
        }
        int nameStart = body.startsWith("</", lt) ? lt + 2 : lt + 1;
        if (nameStart >= body.length() || !isNameStart(body.charAt(nameStart))) {
            return lt;
        }
        return tagEnd(nameEnd(nameStart));
    }

    private static int terminated(int index, int terminatorLength) {
        return index < 0 ? -1 : index + terminatorLength;
    }

    /** {@code <!DOCTYPE ...>}, whose XML internal subset in square brackets may contain {@code >}. */
    private int declarationEnd(int from) {
        int brackets = 0;
        for (int i = from; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"' || c == '\'') {
                int close = body.indexOf(c, i + 1);
                if (close < 0) {
                    return -1;
                }
                i = close;
            } else if (c == '[') {
                brackets++;
            } else if (c == ']') {
                brackets = Math.max(0, brackets - 1);
            } else if (c == '>' && (brackets == 0 || html)) {
                return i + 1;
            }
        }
        return -1;
    }

    /** End of a start or end tag, after its name: quotes only matter around attribute values, as in HTML. */
    private int tagEnd(int from) {
        for (int i = from; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '>') {
                return i + 1;
            } else if (c == '=') {
                int value = Scan.skipWhitespace(body, i + 1);
                if (value < body.length() && (body.charAt(value) == '"' || body.charAt(value) == '\'')) {
                    int close = body.indexOf(body.charAt(value), value + 1);
                    if (close < 0) {
                        return -1;
                    }
                    i = close;
                } else {
                    i = value - 1;
                }
            }
        }
        return -1;
    }

    private void addMarkup(int from, int end) {
        if (body.charAt(from + 1) == '!' || body.charAt(from + 1) == '?') {
            items.add(Item.of(Kind.LEAF, from, end, null));
            return;
        }
        boolean closing = body.charAt(from + 1) == '/';
        int nameStart = closing ? from + 2 : from + 1;
        String name = body.substring(nameStart, nameEnd(nameStart));
        if (closing) {
            items.add(Item.of(Kind.CLOSE, from, end, name));
            return;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (body.startsWith("/>", end - 2) || (html && VOID_ELEMENTS.contains(lower))) {
            items.add(Item.of(Kind.LEAF, from, end, name));
        } else if (html && RAW_ELEMENTS.contains(lower)) {
            addRaw(from, end, lower);
        } else {
            items.add(Item.of(Kind.OPEN, from, end, name));
        }
    }

    private void addRaw(int from, int startTagEnd, String name) {
        int endTag = findEndTag(name, startTagEnd);
        int to = body.length();
        if (endTag < 0) {
            endTag = to;
        } else {
            int tagEnd = tagEnd(endTag + 2 + name.length());
            to = tagEnd < 0 ? to : tagEnd;
        }
        Content content = switch (name) {
            case "script" -> scriptContent(type(from, startTagEnd));
            case "style" -> {
                String type = type(from, startTagEnd);
                yield type == null || type.isBlank() || type.contains("css") ? Content.CSS : Content.VERBATIM;
            }
            default -> Content.VERBATIM;
        };
        items.add(new Item(Kind.RAW, from, to, name, startTagEnd, endTag, content));
    }

    private static Content scriptContent(String type) {
        if (type == null || type.isBlank() || type.equals("module")) {
            return Content.JAVASCRIPT;
        } else if (type.contains("json")) {
            return Content.JSON;
        }
        // text/template and the like hold markup or data, not code.
        return type.contains("javascript") || type.contains("ecmascript") ? Content.JAVASCRIPT : Content.VERBATIM;
    }

    /** @return the start of {@code </name}, ignoring case, or -1 */
    private int findEndTag(String name, int from) {
        int i = from;
        while ((i = body.indexOf("</", i)) >= 0) {
            int nameEnd = i + 2 + name.length();
            if (
                body.regionMatches(true, i + 2, name, 0, name.length()) &&
                (nameEnd == body.length() || !isNameChar(body.charAt(nameEnd)))
            ) {
                return i;
            }
            i += 2;
        }
        return -1;
    }

    /** @return the lowercase {@code type} attribute of the start tag between {@code from} and {@code to}, or null */
    private String type(int from, int to) {
        Matcher matcher = TYPE_ATTRIBUTE.matcher(body).region(from, to);
        if (!matcher.find()) {
            return null;
        }
        String value =
            matcher.group(1) != null
                ? matcher.group(1)
                : matcher.group(2) != null
                  ? matcher.group(2)
                  : matcher.group(3);
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private void addText(int from, int to) {
        int start = Math.min(Scan.skipWhitespace(body, from), to);
        int end = to;
        while (end > start && Scan.isWhitespace(body.charAt(end - 1))) {
            end--;
        }
        if (start < end) {
            items.add(Item.of(Kind.TEXT, start, end, null));
        }
    }

    private void write(IndentWriter out) {
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            switch (item.kind()) {
                case CLOSE -> {
                    out.dedent();
                    out.newline();
                    write(item, out);
                }
                case OPEN -> {
                    out.newline();
                    write(item, out);
                    if (
                        i + 2 < items.size() && items.get(i + 1).kind() == Kind.TEXT && closes(items.get(i + 2), item)
                    ) {
                        write(items.get(i + 1), out);
                        write(items.get(i + 2), out);
                        i += 2;
                    } else {
                        out.indent();
                    }
                }
                case LEAF, TEXT -> {
                    out.newline();
                    write(item, out);
                }
                case RAW -> {
                    out.newline();
                    writeRaw(item, out);
                }
            }
        }
    }

    private static boolean closes(Item close, Item open) {
        return close.kind() == Kind.CLOSE && close.name().equalsIgnoreCase(open.name());
    }

    private void write(Item item, IndentWriter out) {
        out.write(body, item.from(), item.to());
    }

    private void writeRaw(Item item, IndentWriter out) {
        out.write(body, item.from(), item.contentFrom());
        String content = Scan.strip(body.substring(item.contentFrom(), item.contentTo()));
        boolean formatted =
            !content.isEmpty() &&
            (item.content() == Content.JAVASCRIPT ||
                item.content() == Content.CSS ||
                (item.content() == Content.JSON && JsonFormatter.accepts(content)));
        if (formatted) {
            out.indent();
            out.newline();
            switch (item.content()) {
                case JAVASCRIPT -> JavaScriptFormatter.write(content, out);
                case CSS -> CssFormatter.write(content, out);
                case JSON -> JsonFormatter.write(content, out);
                case VERBATIM -> throw new IllegalStateException();
            }
            out.dedent();
            out.newline();
        } else {
            out.write(body, item.contentFrom(), item.contentTo());
        }
        out.write(body, item.contentTo(), item.to());
    }

    private int nameEnd(int from) {
        int i = from;
        while (i < body.length() && isNameChar(body.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_' || c == ':';
    }

    private static boolean isNameChar(char c) {
        return !Scan.isWhitespace(c) && c != '/' && c != '>' && c != '<' && c != '=';
    }
}
