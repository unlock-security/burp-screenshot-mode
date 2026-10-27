package it.unlocksecurity.burp.screenshotmode;

import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.handler.TimingData;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import it.unlocksecurity.burp.screenshotmode.render.MessageData;
import it.unlocksecurity.burp.screenshotmode.render.MessageText;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Converts Montoya request/response pairs into {@link MessageData}. Hashing and copying a large message takes a
 * while: call it off the Swing thread.
 */
final class BurpMessages {

    /** How much of the request {@link #key} copies out of Burp at a time. */
    static final int HASH_CHUNK = 1 << 20;

    private BurpMessages() {}

    static MessageData toMessageData(HttpRequestResponse requestResponse) {
        HttpRequest request = requestResponse.request();
        HttpResponse response = requestResponse.response();
        String url = url(request, requestResponse.httpService());
        ByteArray requestBytes = request.toByteArray();
        ByteArray responseBytes = response == null ? null : response.toByteArray();

        Long roundtrip = null;
        ZonedDateTime requestTime = null;
        Optional<TimingData> timing = timingData(requestResponse);
        if (timing.isPresent()) {
            try {
                if (timing.get().timeBetweenRequestSentAndEndOfResponse() != null) {
                    roundtrip = timing.get().timeBetweenRequestSentAndEndOfResponse().toMillis();
                }
                requestTime = timing.get().timeRequestSent();
            } catch (RuntimeException e) {
                // Timing details are optional decoration.
            }
        }

        return new MessageData(
            key(url, requestBytes),
            url,
            text(requestBytes),
            responseBytes == null ? null : text(responseBytes),
            responseBytes == null ? null : responseBytes.length(),
            roundtrip,
            requestTime
        );
    }

    /** Same shape as the Caido plugin: scheme://host:port/path?query, with the port always shown. */
    private static String url(HttpRequest request, HttpService service) {
        if (service == null) {
            service = request.httpService();
        }
        String path;
        try {
            path = request.path();
        } catch (RuntimeException e) {
            path = "";
        }
        if (service == null) {
            try {
                return request.url();
            } catch (RuntimeException e) {
                return path;
            }
        }
        return (service.secure() ? "https" : "http") + "://" + service.host() + ":" + service.port() + path;
    }

    private static Optional<TimingData> timingData(HttpRequestResponse requestResponse) {
        try {
            return requestResponse.timingData();
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Only the part that can be shown is copied out of Burp: messages can be hundreds of megabytes. */
    private static String text(ByteArray bytes) {
        int length = bytes.length();
        byte[] head =
            length > MessageText.MAX_BYTES ? bytes.subArray(0, MessageText.MAX_BYTES).getBytes() : bytes.getBytes();
        return MessageText.fromBytes(head, length);
    }

    /** The whole request counts, so an edited body is another message, but it is copied a chunk at a time. */
    static String key(String url, ByteArray request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(url.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            int length = request.length();
            for (int start = 0; start < length; start += HASH_CHUNK) {
                digest.update(request.subArray(start, Math.min(length, start + HASH_CHUNK)).getBytes());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
