package it.unlocksecurity.burp.screenshotmode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import it.unlocksecurity.burp.screenshotmode.render.MessageData;
import it.unlocksecurity.burp.screenshotmode.render.MessageText;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BurpMessagesTest {

    private static final String URL = "https://example.com:443/upload";

    /** The largest copy a test's {@link #bytes} handed out. */
    private int largestCopy;

    @Test
    void keyHashesTheWholeRequestInChunks() throws Exception {
        byte[] request = body(BurpMessages.HASH_CHUNK * 2 + 123, (byte) 'a');
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(URL.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(request);

        assertEquals(HexFormat.of().formatHex(digest.digest()), BurpMessages.key(URL, bytes(request)));
        assertTrue(largestCopy <= BurpMessages.HASH_CHUNK, "never copies the whole request at once");
    }

    @Test
    void anEditedBodyIsAnotherMessage() {
        byte[] request = body(BurpMessages.HASH_CHUNK + 10, (byte) 'a');
        byte[] edited = request.clone();
        edited[edited.length - 1] = 'b';

        assertNotEquals(BurpMessages.key(URL, bytes(request)), BurpMessages.key(URL, bytes(edited)));
    }

    @Test
    void copiesOnlyTheShownPartOfALargeRequest() {
        byte[] request = body(MessageText.MAX_BYTES * 3, (byte) 'a');

        MessageData data = BurpMessages.toMessageData(message(bytes(request)));

        assertTrue(largestCopy <= MessageText.MAX_BYTES, "copied " + largestCopy + " bytes at once");
        assertTrue(data.requestText().endsWith(" bytes]"), "says the request was cut");
        assertEquals(BurpMessages.key(URL, bytes(request)), data.key());
    }

    private static byte[] body(int length, byte fill) {
        byte[] head = "POST /upload HTTP/1.1\r\nHost: example.com\r\n\r\n".getBytes(StandardCharsets.US_ASCII);
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, fill);
        System.arraycopy(head, 0, bytes, 0, head.length);
        return bytes;
    }

    /** Burp's byte array, recording how much each copy takes. */
    private ByteArray bytes(byte[] data) {
        return proxy(
            ByteArray.class,
            (method, args) ->
                switch (method) {
                    case "length" -> data.length;
                    case "getBytes" -> {
                        largestCopy = Math.max(largestCopy, data.length);
                        yield data.clone();
                    }
                    case "subArray" -> bytes(Arrays.copyOfRange(data, (int) args[0], (int) args[1]));
                    default -> null;
                }
        );
    }

    private static HttpRequestResponse message(ByteArray requestBytes) {
        HttpRequest request = proxy(
            HttpRequest.class,
            (method, args) ->
                switch (method) {
                    case "toByteArray" -> requestBytes;
                    case "path" -> "/upload";
                    case "url" -> URL;
                    default -> null;
                }
        );
        return proxy(
            HttpRequestResponse.class,
            (method, args) ->
                switch (method) {
                    case "request" -> request;
                    case "timingData" -> Optional.empty();
                    default -> null;
                }
        );
    }

    private interface Answer {
        Object answer(String method, Object[] args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Answer answer) {
        return (T) Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[] { type },
            (self, method, args) ->
                switch (method.getName()) {
                    case "equals" -> self == args[0];
                    case "hashCode" -> System.identityHashCode(self);
                    case "toString" -> type.getSimpleName();
                    default -> answer.answer(method.getName(), args);
                }
        );
    }
}
