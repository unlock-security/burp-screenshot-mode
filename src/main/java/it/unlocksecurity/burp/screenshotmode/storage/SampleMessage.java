package it.unlocksecurity.burp.screenshotmode.storage;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The made-up message the template editor previews, from {@code sample-message.json} next to this class in the jar:
 * on example.com, with something for each rule of Defaults to match. Checked by the build
 * ({@code SampleMessageTest}).
 *
 * @param request     with {@code \n} line breaks
 * @param response    with {@code \n} line breaks
 * @param requestTime when the request was "sent", so the preview shows a timestamp
 */
public record SampleMessage(String url, String request, String response, ZonedDateTime requestTime) {
    static final String FORMAT = "screenshot-mode/sample-message";
    static final int VERSION = 1;
    private static final String RESOURCE = "sample-message.json";
    private static final SampleMessage BUNDLED = parse(json());

    public static SampleMessage bundled() {
        return BUNDLED;
    }

    /** @throws IllegalArgumentException if the file is not a valid sample message */
    static SampleMessage parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!FORMAT.equals(root.get("format").getAsString()) || root.get("version").getAsInt() != VERSION) {
                throw new IllegalArgumentException("Not a version " + VERSION + " sample message");
            }
            return new SampleMessage(
                root.get("url").getAsString(),
                lines(root.getAsJsonArray("request")),
                lines(root.getAsJsonArray("response")),
                ZonedDateTime.parse(root.get("requestTime").getAsString())
            );
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid sample message: " + e.getMessage(), e);
        }
    }

    /** One string per line in the file, so it reads like the message. */
    private static String lines(JsonArray array) {
        List<String> lines = new ArrayList<>();
        for (JsonElement line : array) {
            lines.add(line.getAsString());
        }
        return String.join("\n", lines);
    }

    private static String json() {
        try (InputStream in = SampleMessage.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is missing from the extension jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
    }
}
