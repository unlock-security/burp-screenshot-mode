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

    private static final Pattern JSON = Pattern.compile(
        "(?<key>\"(?:[^\"\\\\\\n]|\\\\.)*\"(?=\\s*:))" +
            "|(?<string>\"(?:[^\"\\\\\\n]|\\\\.)*\")" +
            "|(?<number>-?\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b)" +
            "|(?<keyword>\\b(?:true|false|null)\\b)"
    );
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

    private static void tokenizeBody(String text, int start, String contentType, List<Token> tokens) {
        String body = text.substring(start);
        String sniff = body.stripLeading();
        if (contentType.contains("json") || sniff.startsWith("{") || sniff.startsWith("[")) {
            tokenizeJson(body, start, tokens);
        } else if (contentType.contains("xml") || contentType.contains("html") || sniff.startsWith("<")) {
            tokenizeMarkup(body, start, tokens);
        } else if (contentType.contains("x-www-form-urlencoded")) {
            tokenizeForm(body, start, tokens);
        }
    }

    private static void tokenizeJson(String body, int offset, List<Token> tokens) {
        Matcher matcher = JSON.matcher(body);
        while (matcher.find()) {
            TokenType type =
                matcher.group("key") != null
                    ? TokenType.KEY
                    : matcher.group("string") != null
                      ? TokenType.STRING
                      : matcher.group("number") != null
                        ? TokenType.NUMBER
                        : TokenType.KEYWORD;
            tokens.add(new Token(offset + matcher.start(), offset + matcher.end(), type));
        }
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
