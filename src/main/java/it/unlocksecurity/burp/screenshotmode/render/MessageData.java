package it.unlocksecurity.burp.screenshotmode.render;

import java.time.ZonedDateTime;

/**
 * Everything the screenshot needs from a request/response pair, decoupled from the Montoya API.
 *
 * @param key            identifies the message across invocations, to restore per-message screenshot state
 * @param responseText   {@code null} when there is no response
 * @param responseLength response size in bytes, {@code null} when there is no response
 * @param roundtripMillis {@code null} when Burp has no timing data
 * @param requestTime    when the request was sent, {@code null} when unknown
 */
public record MessageData(
    String key,
    String url,
    String requestText,
    String responseText,
    Integer responseLength,
    Long roundtripMillis,
    ZonedDateTime requestTime
) {}
