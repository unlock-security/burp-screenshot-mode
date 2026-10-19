package it.unlocksecurity.burp.screenshotmode.render.pretty;

import it.unlocksecurity.burp.screenshotmode.render.MessageText;
import it.unlocksecurity.burp.screenshotmode.render.Scan;
import java.util.Locale;

/**
 * Pretty prints message bodies, like the "Pretty" tab of Burp's message editor: JSON, HTML and XML (SVG, RSS,
 * SOAP, ...). Only line breaks and indentation change: the tokens and the spacing within a line are
 * kept as they are, so rules written against the raw message still match, and what the screenshot shows is what was
 * sent. Bodies in other formats, or that don't parse as the format they claim, are returned unchanged.
 *
 * <p>Every formatter is a hand-written scanner that runs in linear time and never recurses, whatever the body: they
 * run on the Swing thread, on content that can come from a hostile server.
 */
public final class PrettyPrinter {

    private enum Format {
        JSON,
        HTML,
        XML,
    }

    private PrettyPrinter() {}

    /** @param text message text with {@code \n} line breaks; the start line and headers are never touched */
    public static String format(String text) {
        int headersEnd = text.indexOf("\n\n");
        if (headersEnd < 0) {
            return text;
        }
        String headers = text.substring(0, headersEnd + 2);
        String body = Scan.strip(text.substring(headersEnd + 2));
        if (body.isEmpty()) {
            return text;
        }

        String contentType = contentType(headers);
        Format declared = declared(contentType);
        Format sniffed = sniffed(body);
        if (declared == Format.XML && sniffed == Format.HTML) {
            declared = Format.HTML;
        }
        int limit = Math.max(0, MessageText.MAX_CHARS - headers.length());
        String pretty = declared == null ? null : format(declared, body, limit);
        if (pretty == null && sniffed != null && sniffed != declared) {
            pretty = format(sniffed, body, limit);
        }
        return pretty == null ? text : headers + pretty;
    }

    /** @return the formatted body, or {@code null} when it isn't valid JSON or has no markup */
    private static String format(Format format, String body, int limit) {
        IndentWriter out = new IndentWriter(limit);
        switch (format) {
            case JSON -> {
                if (!JsonFormatter.accepts(body)) {
                    return null;
                }
                JsonFormatter.write(body, out);
            }
            case HTML, XML -> {
                if (!MarkupFormatter.write(body, format == Format.HTML, out)) {
                    return null;
                }
            }
        }
        return out.toString();
    }

    /** @return the lowercase Content-Type, or an empty string */
    private static String contentType(String headers) {
        String contentType = "";
        for (String header : headers.split("\n")) {
            int colon = header.indexOf(':');
            if (colon > 0 && header.substring(0, colon).trim().equalsIgnoreCase("content-type")) {
                contentType = header
                    .substring(colon + 1)
                    .trim()
                    .toLowerCase(Locale.ROOT);
            }
        }
        return contentType;
    }

    private static Format declared(String contentType) {
        // Media type only: a parameter like "charset" could contain anything.
        String type = contentType.split(";", 2)[0];
        if (type.contains("json")) {
            return Format.JSON;
        } else if (type.contains("html")) {
            return Format.HTML;
        } else if (type.contains("xml")) {
            return Format.XML;
        }
        return null;
    }

    /** For bodies without a (correct) Content-Type: JSON and markup are recognizable from their first character. */
    private static Format sniffed(String body) {
        if (body.startsWith("{") || body.startsWith("[")) {
            return Format.JSON;
        } else if (body.startsWith("<")) {
            String start = body.substring(0, Math.min(body.length(), 20)).toLowerCase(Locale.ROOT);
            return start.startsWith("<!doctype html") || start.startsWith("<html") ? Format.HTML : Format.XML;
        }
        return null;
    }
}
