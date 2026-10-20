package it.unlocksecurity.burp.screenshotmode.model;

import java.util.ArrayList;
import java.util.List;

/** Screenshot settings. New templates start from {@code storage.DefaultTemplate}. */
public final class Settings {

    public static final int DEFAULT_PIXEL_WIDTH = 800;
    public static final int MIN_PIXEL_WIDTH = 200;
    public static final int MAX_PIXEL_WIDTH = 2000;

    public HiddenHeaders headersToHide = new HiddenHeaders();
    public Disposition disposition = Disposition.HORIZONTAL;
    public WidthMode widthMode = WidthMode.FULL;
    /** Only meaningful when {@link #widthMode} is {@link WidthMode#PIXEL}. */
    public int widthPixels = DEFAULT_PIXEL_WIDTH;
    public List<HighlightRule> highlights = new ArrayList<>();
    public List<RedactionRule> redactions = new ArrayList<>();
    public boolean showTimestamp = false;
    public boolean showResponseInfo = true;
    /** The capture palette: Swing has no CSS theme to inherit, so it is selectable. */
    public ThemeMode theme = ThemeMode.AUTO;
    /** Raw or Pretty, like the tabs of Burp's message editor. */
    public ViewMode view = ViewMode.RAW;
    public UrlMode url = UrlMode.FULL;

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
        copy.url = url;
        return copy;
    }
}
