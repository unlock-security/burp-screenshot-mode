package it.unlocksecurity.burp.screenshotmode.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HttpSyntaxTest {

    private static Map<String, HttpSyntax.TokenType> tokens(String text, boolean request) {
        return HttpSyntax.tokenize(text, request)
            .stream()
            .collect(
                Collectors.toMap(token -> text.substring(token.from(), token.to()), HttpSyntax.Token::type, (a, b) -> a)
            );
    }

    @Test
    void tokenizesRequestLineHeadersAndJsonBody() {
        String text =
            "POST /api?x=1 HTTP/2\nContent-Type: application/json\n\n{\"user\": \"bob\", \"admin\": true, \"n\": 42}";
        Map<String, HttpSyntax.TokenType> tokens = tokens(text, true);
        assertEquals(HttpSyntax.TokenType.METHOD, tokens.get("POST"));
        assertEquals(HttpSyntax.TokenType.PATH, tokens.get("/api?x=1"));
        assertEquals(HttpSyntax.TokenType.VERSION, tokens.get("HTTP/2"));
        assertEquals(HttpSyntax.TokenType.HEADER_NAME, tokens.get("Content-Type"));
        assertEquals(HttpSyntax.TokenType.KEY, tokens.get("\"user\""));
        assertEquals(HttpSyntax.TokenType.STRING, tokens.get("\"bob\""));
        assertEquals(HttpSyntax.TokenType.KEYWORD, tokens.get("true"));
        assertEquals(HttpSyntax.TokenType.NUMBER, tokens.get("42"));
    }

    @Test
    void tokenizesStatusLineAndMarkup() {
        String text = "HTTP/1.1 404 Not Found\nServer: x\n\n<a href=\"/x\">y</a><!-- c -->";
        Map<String, HttpSyntax.TokenType> tokens = tokens(text, false);
        assertEquals(HttpSyntax.TokenType.VERSION, tokens.get("HTTP/1.1"));
        assertEquals(HttpSyntax.TokenType.STATUS_CODE, tokens.get("404"));
        assertEquals(HttpSyntax.TokenType.STATUS_TEXT, tokens.get("Not Found"));
        assertEquals(HttpSyntax.TokenType.TAG, tokens.get("a"));
        assertEquals(HttpSyntax.TokenType.ATTRIBUTE, tokens.get("href"));
        assertEquals(HttpSyntax.TokenType.STRING, tokens.get("\"/x\""));
        assertEquals(HttpSyntax.TokenType.COMMENT, tokens.get("<!-- c -->"));
    }

    @Test
    void tokensStayInsideTheText() {
        String text = "GET / HTTP/1.1\nA: b\n\n<<<>>\"\n{";
        List<HttpSyntax.Token> tokens = HttpSyntax.tokenize(text, true);
        assertTrue(tokens.stream().allMatch(t -> 0 <= t.from() && t.from() <= t.to() && t.to() <= text.length()));
        assertTrue(HttpSyntax.tokenize("", true).isEmpty());
    }
}
