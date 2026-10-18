package it.unlocksecurity.burp.screenshotmode.model;

import java.util.ArrayList;
import java.util.List;

public final class Settings {

    public static final int DEFAULT_PIXEL_WIDTH = 800;
    public static final int MIN_PIXEL_WIDTH = 200;
    public static final int MAX_PIXEL_WIDTH = 2000;

    private static final List<String> DEFAULT_HEADERS_TO_HIDE = List.of(
        "Accept",
        "Accept-Encoding",
        "Accept-Language",
        "Accept-Ranges",
        "Age",
        "Alt-Svc",
        "Cache-Control",
        "CF-Cache-Status",
        "CF-RAY",
        "Connection",
        "Content-Length",
        "Date",
        "ETag",
        "Expect-CT",
        "Expires",
        "Keep-Alive",
        "Last-Modified",
        "NEL",
        "Permissions-Policy",
        "Pragma",
        "Priority",
        "Report-To",
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
        "Server-Timing",
        "Strict-Transport-Security",
        "Transfer-Encoding",
        "Upgrade-Insecure-Requests",
        "Vary",
        "Via",
        "X-Amz-Cf-Id",
        "X-Amz-Cf-Pop",
        "X-Amz-Id-2",
        "X-Amz-Request-Id",
        "X-Cache",
        "X-Content-Type-Options",
        "X-Frame-Options",
        "X-Powered-By",
        "X-Request-Id",
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

    public static Settings defaults() {
        Settings settings = new Settings();
        settings.headersToHide.both = new ArrayList<>(DEFAULT_HEADERS_TO_HIDE);
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
        return copy;
    }
}
