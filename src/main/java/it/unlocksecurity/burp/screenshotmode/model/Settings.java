package it.unlocksecurity.burp.screenshotmode.model;

import java.util.ArrayList;
import java.util.List;

public final class Settings {

    public static final int DEFAULT_PIXEL_WIDTH = 800;
    public static final int MIN_PIXEL_WIDTH = 200;
    public static final int MAX_PIXEL_WIDTH = 2000;

    /*
     * Noisy headers hidden by default, split by the messages each is defined for: RFC 9110 (semantics), 9111
     * (caching), 9112 (HTTP/1.1) and the specs named below. Vendor headers (Cloudflare, CloudFront, S3, Fastly, ...)
     * are added by servers and CDNs to responses: Burp, on the client side, never sees them in requests.
     */

    /** Allowed in both requests and responses. */
    private static final List<String> DEFAULT_HEADERS_TO_HIDE = List.of(
        // Mostly requests, but also in 415 responses (RFC 9110, section 15.5.16).
        "Accept",
        "Accept-Encoding",
        "Cache-Control",
        "Connection",
        "Content-Length",
        "Date",
        // RFC 2068, section 19.7.1.1.
        "Keep-Alive",
        // A request field (RFC 9111, section 5.4), but servers commonly send "Pragma: no-cache" too.
        "Pragma",
        // RFC 9218, section 5.
        "Priority",
        "Transfer-Encoding",
        "Via",
        // No spec: clients and proxies send it, servers echo it.
        "X-Request-Id"
    );

    private static final List<String> DEFAULT_REQUEST_HEADERS_TO_HIDE = List.of(
        "Accept-Language",
        // User-Agent Client Hints (WICG) and Fetch Metadata (W3C).
        "Sec-CH-UA",
        "Sec-CH-UA-Arch",
        "Sec-CH-UA-Bitness",
        "Sec-CH-UA-Full-Version",
        "Sec-CH-UA-Full-Version-List",
        "Sec-CH-UA-Mobile",
        "Sec-CH-UA-Model",
        "Sec-CH-UA-Platform",
        "Sec-CH-UA-Platform-Version",
        "Sec-Fetch-Dest",
        "Sec-Fetch-Mode",
        "Sec-Fetch-Site",
        "Sec-Fetch-User",
        // Upgrade Insecure Requests (W3C).
        "Upgrade-Insecure-Requests"
    );

    private static final List<String> DEFAULT_RESPONSE_HEADERS_TO_HIDE = List.of(
        "Accept-Ranges",
        "Age",
        // RFC 7838.
        "Alt-Svc",
        "CF-Cache-Status",
        "CF-RAY",
        "ETag",
        // RFC 9163.
        "Expect-CT",
        "Expires",
        "Last-Modified",
        // Network Error Logging, Permissions Policy, Reporting API, Server Timing (W3C).
        "NEL",
        "Permissions-Policy",
        "Report-To",
        "Server-Timing",
        // RFC 6797.
        "Strict-Transport-Security",
        "Vary",
        "X-Amz-Cf-Id",
        "X-Amz-Cf-Pop",
        "X-Amz-Id-2",
        "X-Amz-Request-Id",
        "X-Cache",
        // Fetch (WHATWG).
        "X-Content-Type-Options",
        // RFC 7034.
        "X-Frame-Options",
        "X-Powered-By",
        "X-Served-By",
        "X-Timer",
        "X-XSS-Protection"
    );

    public HiddenHeaders headersToHide = new HiddenHeaders();
    public Disposition disposition = Disposition.HORIZONTAL;
    public WidthMode widthMode = WidthMode.FULL;
    /** Only meaningful when {@link #widthMode} is {@link WidthMode#PIXEL}. */
    public int widthPixels = DEFAULT_PIXEL_WIDTH;
    public List<HighlightRule> highlights = new ArrayList<>();
    public List<RedactionRule> redactions = new ArrayList<>();
    public boolean showTimestamp = false;
    public boolean showResponseInfo = true;
    /** Not part of the Caido plugin: Swing has no CSS theme to inherit, so the capture palette is selectable. */
    public ThemeMode theme = ThemeMode.AUTO;
    /** Not part of the Caido plugin: Caido's editors cannot pretty print, Burp's can. */
    public ViewMode view = ViewMode.RAW;

    public static Settings defaults() {
        Settings settings = new Settings();
        settings.headersToHide.both = new ArrayList<>(DEFAULT_HEADERS_TO_HIDE);
        settings.headersToHide.request = new ArrayList<>(DEFAULT_REQUEST_HEADERS_TO_HIDE);
        settings.headersToHide.response = new ArrayList<>(DEFAULT_RESPONSE_HEADERS_TO_HIDE);
        return settings;
    }

    /** Maximum width of the captured content in pixels, or {@code null} for full width. */
    public Integer maxContentWidth() {
        return switch (widthMode) {
            case FULL -> null;
            case PIXEL -> widthPixels;
            case A4, LETTER -> widthMode.presetWidth();
        };
    }

    public Settings copy() {
        Settings copy = new Settings();
        copy.headersToHide = headersToHide.copy();
        copy.disposition = disposition;
        copy.widthMode = widthMode;
        copy.widthPixels = widthPixels;
        copy.highlights = new ArrayList<>(highlights.stream().map(HighlightRule::copy).toList());
        copy.redactions = new ArrayList<>(redactions.stream().map(RedactionRule::copy).toList());
        copy.showTimestamp = showTimestamp;
        copy.showResponseInfo = showResponseInfo;
        copy.theme = theme;
        copy.view = view;
        return copy;
    }
}
