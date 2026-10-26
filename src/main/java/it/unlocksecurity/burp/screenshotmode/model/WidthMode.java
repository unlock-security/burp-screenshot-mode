package it.unlocksecurity.burp.screenshotmode.model;

/** How wide the screenshot is. The order is the order of the Width choices. */
public enum WidthMode implements Labeled {
    /** As wide as the preview. */
    FULL("full", "Full Width", null, 0, 0),
    /** As wide as the longest lines need, so nothing wraps, up to {@link Settings#MAX_PIXEL_WIDTH}. */
    CONTENT("content", "Fit to content", null, 0, 0),
    /** The page in points, one pixel each. */
    A4("a4", "A4 (595px)", "A4", 595, 842),
    LETTER("letter", "Letter (612px)", "Letter", 612, 792),
    PIXEL("pixel", "Custom (px)", null, 0, 0);

    private final String id;
    private final String label;
    private final String page;
    private final int presetWidth;
    private final int pageHeight;

    WidthMode(String id, String label, String page, int presetWidth, int pageHeight) {
        this.id = id;
        this.label = label;
        this.page = page;
        this.presetWidth = presetWidth;
        this.pageHeight = pageHeight;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String label() {
        return label;
    }

    /** Fixed width in pixels for paper presets, 0 otherwise. */
    public int presetWidth() {
        return presetWidth;
    }

    /** The paper's name, such as "A4", or {@code null} when the width is not a paper size. */
    public String page() {
        return page;
    }

    /** The page height at {@link #presetWidth}, in pixels, for paper presets; 0 otherwise. */
    public int pageHeight() {
        return pageHeight;
    }

    @Override
    public String toString() {
        return label;
    }
}
