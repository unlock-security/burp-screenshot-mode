package it.unlocksecurity.burp.screenshotmode;

import static org.junit.jupiter.api.Assertions.assertEquals;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScreenshotContextMenuTest {

    private final HttpRequestResponse first = message();
    private final HttpRequestResponse second = message();

    @Test
    void takesTheOneSelectedRow() {
        assertEquals(Optional.of(first), ScreenshotContextMenu.target(Optional.empty(), List.of(first)));
    }

    @Test
    void offersNothingWithSeveralRowsSelected() {
        assertEquals(
            Optional.empty(),
            ScreenshotContextMenu.target(Optional.empty(), List.of(first, second)),
            "rather than silently taking the first one"
        );
        assertEquals(Optional.empty(), ScreenshotContextMenu.target(Optional.empty(), List.of()));
    }

    @Test
    void takesARowWithOnlyAResponse() {
        HttpRequestResponse responseOnly = message(null, true);

        assertEquals(Optional.of(responseOnly), ScreenshotContextMenu.target(Optional.empty(), List.of(responseOnly)));
    }

    @Test
    void skipsARowWithNeitherMessage() {
        assertEquals(Optional.empty(), ScreenshotContextMenu.target(Optional.empty(), List.of(message(null, false))));
    }

    /** A message with a request, as a table row of Burp's has. */
    private static HttpRequestResponse message() {
        return message(proxy(HttpRequest.class, null, false), true);
    }

    private static HttpRequestResponse message(HttpRequest request, boolean hasResponse) {
        return proxy(HttpRequestResponse.class, request, hasResponse);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, HttpRequest request, boolean hasResponse) {
        return (T) Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] { type },
            (self, method, args) ->
                switch (method.getName()) {
                    case "request" -> request;
                    case "hasResponse" -> hasResponse;
                    case "equals" -> self == args[0];
                    case "hashCode" -> System.identityHashCode(self);
                    case "toString" -> type.getSimpleName();
                    default -> null;
                }
        );
    }
}
