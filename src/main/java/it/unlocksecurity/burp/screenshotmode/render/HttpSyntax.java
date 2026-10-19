package it.unlocksecurity.burp.screenshotmode.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lightweight HTTP syntax highlighting, approximating what Caido's editors show. */
public final class HttpSyntax {

    public enum TokenType {
        METHOD,
        PATH,
        VERSION,
        STATUS_CODE,
        STATUS_TEXT,
        HEADER_NAME,
        KEY,
        STRING,
        NUMBER,
        KEYWORD,
        TAG,
        ATTRIBUTE,
        PUNCTUATION,
        COMMENT,
    }

    public record Token(int from, int to, TokenType type) {}

    private static final Pattern MARKUP = Pattern.compile("<!--[\\s\\S]*?(?:-->|$)|<[^<>]*>");
    private static final Pattern TAG_OPEN = Pattern.compile("^(</|<\\?|<!|<)([^\\s/>?]*)");
    private static final Pattern TAG_CLOSE = Pattern.compile("(/>|\\?>|>)$");
    private static final Pattern ATTRIBUTE = Pattern.compile(
        "([^\\s=/>\"']+)(?:\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+))?"
    );
    private static final Pattern FORM = Pattern.compile("([^&=\\n]+)(?:=([^&\\n]*))?");

    private HttpSyntax() {}

    public static List<Token> tokenize(String text, boolean request) {
        List<Token> tokens = new ArrayList<>();
        if (text.isEmpty()) {
            return tokens;
        }

        int lineEnd = lineEnd(text, 0);
        tokenizeStartLine(text, lineEnd, request, tokens);

        String contentType = "";
        int position = lineEnd + 1;
        while (position < text.length()) {
            int end = lineEnd(text, position);
            if (end == position) {
                position++;
                break;
            }
            int colon = text.indexOf(':', position);
            if (colon > position && colon < end) {
                tokens.add(new Token(position, colon, TokenType.HEADER_NAME));
                if (text.substring(position, colon).trim().equalsIgnoreCase("content-type")) {
                    contentType = text
                        .substring(colon + 1, end)
                        .trim()
                        .toLowerCase(Locale.ROOT);
                }
            }
            position = end + 1;
        }

        if (position < text.length()) {
            tokenizeBody(text, position, contentType, tokens);
        }
        return tokens;
    }

    private static int lineEnd(String text, int from) {
        int end = text.indexOf('\n', from);
        return end < 0 ? text.length() : end;
    }

    private static void tokenizeStartLine(String text, int end, boolean request, List<Token> tokens) {
        String line = text.substring(0, end);
        int firstSpace = line.indexOf(' ');
        if (firstSpace <= 0) {
            return;
        }
        int secondSpace = line.indexOf(' ', firstSpace + 1);
        if (request) {
            int versionStart = line.lastIndexOf(' ');
            tokens.add(new Token(0, firstSpace, TokenType.METHOD));
            if (versionStart > firstSpace && line.startsWith("HTTP/", versionStart + 1)) {
                tokens.add(new Token(firstSpace + 1, versionStart, TokenType.PATH));
                tokens.add(new Token(versionStart + 1, end, TokenType.VERSION));
            } else {
                tokens.add(new Token(firstSpace + 1, end, TokenType.PATH));
            }
        } else if (line.startsWith("HTTP/")) {
            tokens.add(new Token(0, firstSpace, TokenType.VERSION));
            int codeEnd = secondSpace < 0 ? end : secondSpace;
            tokens.add(new Token(firstSpace + 1, codeEnd, TokenType.STATUS_CODE));
            if (secondSpace > 0 && secondSpace + 1 < end) {
                tokens.add(new Token(secondSpace + 1, end, TokenType.STATUS_TEXT));
            }
        }
    }

    /** Body formats, told apart by the Content-Type or, failing that, by the first character of the body. */
    enum BodyKind {
        JSON,
        MARKUP,
        FORM,
        OTHER;

        /** @param contentType lowercase Content-Type header value, empty when missing */
        static BodyKind of(String contentType, String body) {
            String sniff = body.stripLeading();
            if (contentType.contains("json") || sniff.startsWith("{") || sniff.startsWith("[")) {
                return JSON;
            } else if (contentType.contains("xml") || contentType.contains("html") || sniff.startsWith("<")) {
                return MARKUP;
            } else if (contentType.contains("x-www-form-urlencoded")) {
                return FORM;
            }
            return OTHER;
        }
    }

    private static void tokenizeBody(String text, int start, String contentType, List<Token> tokens) {
        String body = text.substring(start);
        switch (BodyKind.of(contentType, body)) {
            case JSON -> tokenizeJson(body, start, tokens);
            case MARKUP -> tokenizeMarkup(body, start, tokens);
            case FORM -> tokenizeForm(body, start, tokens);
            case OTHER -> {
            }
        }
    }

    /**
     * Strings (keys when a colon follows), numbers and {@code true}/{@code false}/{@code null}. Scanned by hand: a
     * regular expression for strings, {@code "(?:[^"\\]|\\.)*"}, recurses once per character and overflows the
     * stack on a string of a few thousand characters, like a JWT or an embedded image.
     */
    private static void tokenizeJson(String body, int offset, List<Token> tokens) {
        int length = body.length();
        // No string can be closed before this index: an earlier one ran into the end of its line.
        int noStringBefore = 0;
        int i = 0;
        while (i < length) {
            char c = body.charAt(i);
            boolean wordBefore = i > 0 && Scan.isWordChar(body.charAt(i - 1));
            if (c == '"' && i >= noStringBefore) {
                int end = Scan.closedQuoteEnd(body, i);
                if (end >= 0) {
                    int next = Scan.skipWhitespace(body, end);
                    boolean key = next < length && body.charAt(next) == ':';
                    tokens.add(new Token(offset + i, offset + end, key ? TokenType.KEY : TokenType.STRING));
                    i = end;
                    continue;
                }
                noStringBefore = -end - 1;
            } else if ((c == '-' && i + 1 < length && isDigit(body.charAt(i + 1))) || (isDigit(c) && !wordBefore)) {
                int end = numberEnd(body, i);
                if (end > 0) {
                    tokens.add(new Token(offset + i, offset + end, TokenType.NUMBER));
                    i = end;
                    continue;
                }
            } else if (!wordBefore && Scan.isWordChar(c)) {
                int end = Scan.wordEnd(body, i);
                String word = body.substring(i, end);
                if (word.equals("true") || word.equals("false") || word.equals("null")) {
                    tokens.add(new Token(offset + i, offset + end, TokenType.KEYWORD));
                }
                i = end;
                continue;
            }
            i++;
        }
    }

    /**
     * @return the end of the number starting at {@code from}, with its fraction and exponent when they are followed
     *     by a word boundary, or -1 when no form of it is
     */
    private static int numberEnd(String body, int from) {
        int integerEnd = digitsEnd(body, body.charAt(from) == '-' ? from + 1 : from);
        int fractionEnd = body.startsWith(".", integerEnd) ? digitsEnd(body, integerEnd + 1) : integerEnd;
        if (fractionEnd == integerEnd + 1) {
            fractionEnd = integerEnd;
        }
        int exponentEnd = fractionEnd;
        if (fractionEnd < body.length() && (body.charAt(fractionEnd) == 'e' || body.charAt(fractionEnd) == 'E')) {
            int digits = fractionEnd + 1;
            if (digits < body.length() && (body.charAt(digits) == '+' || body.charAt(digits) == '-')) {
                digits++;
            }
            int end = digitsEnd(body, digits);
            exponentEnd = end > digits ? end : fractionEnd;
        }
        for (int end : new int[] { exponentEnd, fractionEnd, integerEnd }) {
            if (end == body.length() || !Scan.isWordChar(body.charAt(end))) {
                return end;
            }
        }
        return -1;
    }

    private static int digitsEnd(String body, int from) {
        int i = from;
        while (i < body.length() && isDigit(body.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static void tokenizeMarkup(String body, int offset, List<Token> tokens) {
        Matcher matcher = MARKUP.matcher(body);
        while (matcher.find()) {
            int tagStart = offset + matcher.start();
            String tag = matcher.group();
            if (tag.startsWith("<!--")) {
                tokens.add(new Token(tagStart, offset + matcher.end(), TokenType.COMMENT));
                continue;
            }

            Matcher open = TAG_OPEN.matcher(tag);
            if (!open.find()) {
                continue;
            }
            tokens.add(new Token(tagStart, tagStart + open.end(1), TokenType.PUNCTUATION));
            if (open.end(2) > open.start(2)) {
                tokens.add(new Token(tagStart + open.start(2), tagStart + open.end(2), TokenType.TAG));
            }

            Matcher close = TAG_CLOSE.matcher(tag);
            int attributesEnd = close.find() ? close.start() : tag.length();
            if (attributesEnd < tag.length()) {
                tokens.add(new Token(tagStart + attributesEnd, tagStart + tag.length(), TokenType.PUNCTUATION));
            }

            Matcher attribute = ATTRIBUTE.matcher(tag).region(open.end(), Math.max(open.end(), attributesEnd));
            while (attribute.find()) {
                tokens.add(new Token(tagStart + attribute.start(1), tagStart + attribute.end(1), TokenType.ATTRIBUTE));
                if (attribute.group(2) != null) {
                    tokens.add(new Token(tagStart + attribute.start(2), tagStart + attribute.end(2), TokenType.STRING));
                }
            }
        }
    }

    private static void tokenizeForm(String body, int offset, List<Token> tokens) {
        Matcher matcher = FORM.matcher(body);
        while (matcher.find()) {
            tokens.add(new Token(offset + matcher.start(1), offset + matcher.end(1), TokenType.KEY));
            if (matcher.group(2) != null && !matcher.group(2).isEmpty()) {
                tokens.add(new Token(offset + matcher.start(2), offset + matcher.end(2), TokenType.STRING));
            }
        }
    }
}
