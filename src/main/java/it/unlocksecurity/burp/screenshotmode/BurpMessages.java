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
 * Converts Montoya request/response pairs, or a response alone, into {@link MessageData}. Hashing and copying a large
 * message takes a while: call it off the Swing thread.
 */
final class BurpMessages {

    /** How much of the request {@link #key} copies out of Burp at a time. */
    static final int HASH_CHUNK = 1 << 20;

    private BurpMessages() {}

    static MessageData toMessageData(HttpRequestResponse requestResponse) {
        HttpRequest request = requestResponse.request();
        HttpResponse response = requestResponse.response();
        String url = url(request, requestResponse.httpService());
        ByteArray requestBytes = request == null ? null : request.toByteArray();
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
            requestBytes != null ? key(url, requestBytes) : responseKey(url, responseBytes),
            url,
            requestBytes == null ? null : text(requestBytes),
            responseBytes == null ? null : text(responseBytes),
            responseBytes == null ? null : responseBytes.length(),
            roundtrip,
            requestTime
        );
    }

    /**
     * scheme://host:port/path?query, with the port always shown. Without a request there is no path: the service
     * alone, or nothing when Burp doesn't know it either.
     */
    private static String url(HttpRequest request, HttpService service) {
        if (service == null && request != null) {
            service = request.httpService();
        }
        String path;
        try {
            path = request == null ? "" : request.path();
        } catch (RuntimeException e) {
            path = "";
        }
        if (service == null) {
            try {
                return request == null ? "" : request.url();
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
        return hash(url, (byte) 0, request);
    }

    /**
     * Identifies a message that has no request by its response, which can't be taken for a request with the same
     * bytes.
     */
    static String responseKey(String url, ByteArray response) {
        return hash(url, (byte) 1, response);
    }

    private static String hash(String url, byte separator, ByteArray message) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(url.getBytes(StandardCharsets.UTF_8));
            digest.update(separator);
            int length = message == null ? 0 : message.length();
            for (int start = 0; start < length; start += HASH_CHUNK) {
                digest.update(message.subArray(start, Math.min(length, start + HASH_CHUNK)).getBytes());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
