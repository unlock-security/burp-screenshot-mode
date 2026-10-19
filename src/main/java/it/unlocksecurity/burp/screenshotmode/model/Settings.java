package it.unlocksecurity.burp.screenshotmode.model;

import java.util.ArrayList;
import java.util.List;

public final class Settings {

    public static final int DEFAULT_PIXEL_WIDTH = 800;
    public static final int MIN_PIXEL_WIDTH = 200;
    public static final int MAX_PIXEL_WIDTH = 2000;

    /*
     * Noisy headers hidden by default, split by the messages each is defined for: RFC 9110 (semantics), 9111
     * (caching) and the specs named below. Vendor headers (Cloudflare, CloudFront, S3, Fastly, DuckDuckGo, ...) are
     * added by servers and CDNs to responses: Burp, on the client side, never sees them in requests.
     *
     * Headers that can take part in request smuggling or other desync attacks are left visible, as they can be the
     * evidence: Content-Length, Transfer-Encoding, TE, Trailer, Connection, Keep-Alive, Upgrade, Expect, Host,
     * HTTP2-Settings and Proxy-Connection.
     */

    /** Allowed in both requests and responses. */
    private static final List<String> DEFAULT_HEADERS_TO_HIDE = List.of(
        // Mostly requests, but also in 415 responses (RFC 9110, section 15.5.16).
        "Accept",
        "Accept-Encoding",
        "Cache-Control",
        // RFC 9530.
        "Content-Digest",
        "Date",
        // A request field (RFC 9111, section 5.4), but servers commonly send "Pragma: no-cache" too.
        "Pragma",
        // RFC 9218, section 5.
        "Priority",
        // RFC 9530.
        "Repr-Digest",
        "Via",
        // No spec: clients and proxies send it, servers echo it.
        "X-Request-Id"
    );

    private static final List<String> DEFAULT_REQUEST_HEADERS_TO_HIDE = List.of(
        // Deprecated by browsers (RFC 9110, section 12.5.2).
        "Accept-Charset",
        "Accept-Language",
        // Do Not Track (W3C), withdrawn.
        "DNT",
        // Client hints (RFC 8942): device and network.
        "Device-Memory",
        "Downlink",
        "DPR",
        "ECT",
        "RTT",
        "Viewport-Width",
        // RFC 8470.
        "Early-Data",
        // Legacy prefetch hint; Sec-Purpose replaces it (Fetch, WHATWG).
        "Purpose",
        // Topics API (WICG).
        "Sec-Browsing-Topics",
        // User preference media features client hints (WICG).
        "Sec-CH-Prefers-Color-Scheme",
        "Sec-CH-Prefers-Reduced-Motion",
        // User-Agent Client Hints (WICG).
        "Sec-CH-UA",
        "Sec-CH-UA-Arch",
        "Sec-CH-UA-Bitness",
        "Sec-CH-UA-Form-Factors",
        "Sec-CH-UA-Full-Version",
        "Sec-CH-UA-Full-Version-List",
        "Sec-CH-UA-Mobile",
        "Sec-CH-UA-Model",
        "Sec-CH-UA-Platform",
        "Sec-CH-UA-Platform-Version",
        "Sec-CH-UA-WoW64",
        // Fetch Metadata (W3C).
        "Sec-Fetch-Dest",
        "Sec-Fetch-Mode",
        "Sec-Fetch-Site",
        "Sec-Fetch-Storage-Access",
        "Sec-Fetch-User",
        // Global Privacy Control.
        "Sec-GPC",
        // Fetch (WHATWG).
        "Sec-Purpose",
        // Upgrade Insecure Requests (W3C).
        "Upgrade-Insecure-Requests"
    );

    private static final List<String> DEFAULT_RESPONSE_HEADERS_TO_HIDE = List.of(
        // Client hints (RFC 8942) and Client Hint Reliability (IETF draft).
        "Accept-CH",
        "Critical-CH",
        "Accept-Ranges",
        "Age",
        // RFC 7838.
        "Alt-Svc",
        // RFC 9211.
        "Cache-Status",
        // RFC 9213.
        "CDN-Cache-Control",
        "CF-Cache-Status",
        "CF-RAY",
        // HTML (WHATWG) and Fetch (WHATWG).
        "Cross-Origin-Embedder-Policy",
        "Cross-Origin-Opener-Policy",
        "Cross-Origin-Resource-Policy",
        "X-Duckduckgo-Privacy-Random-Ac",
        "ETag",
        // RFC 9163.
        "Expect-CT",
        "Expires",
        "Last-Modified",
        // Network Error Logging (W3C).
        "NEL",
        // HTML (WHATWG).
        "Origin-Agent-Cluster",
        // Permissions Policy (W3C).
        "Permissions-Policy",
        // RFC 9209.
        "Proxy-Status",
        // Referrer Policy (W3C).
        "Referrer-Policy",
        // Reporting API (W3C): Reporting-Endpoints replaces Report-To.
        "Report-To",
        "Reporting-Endpoints",
        // Server Timing and Resource Timing (W3C).
        "Server-Timing",
        "Timing-Allow-Origin",
        // RFC 6797.
        "Strict-Transport-Security",
        "Vary",
        // Obsoleted by RFC 9111, section 5.5, but still sent.
        "Warning",
        "X-Amz-Cf-Id",
        "X-Amz-Cf-Pop",
        "X-Amz-Id-2",
        "X-Amz-Request-Id",
        "X-Cache",
        // Fetch (WHATWG).
        "X-Content-Type-Options",
        "X-DNS-Prefetch-Control",
        "X-Download-Options",
        // RFC 7034.
        "X-Frame-Options",
        "X-Permitted-Cross-Domain-Policies",
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
