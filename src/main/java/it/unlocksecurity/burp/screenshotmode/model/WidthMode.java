package it.unlocksecurity.burp.screenshotmode.model;

/** How wide the screenshot is. The order is the order of the Width choices. */
public enum WidthMode implements Labeled {
    /** As wide as the preview. */
    FULL("full", "Full Width", 0),
    /** As wide as the longest lines need, so nothing wraps, up to {@link Settings#MAX_PIXEL_WIDTH}. */
    CONTENT("content", "Fit to content", 0),
    A4("a4", "A4 (595px)", 595),
    LETTER("letter", "Letter (612px)", 612),
    PIXEL("pixel", "Custom (px)", 0);

    private final String id;
    private final String label;
    private final int presetWidth;

    WidthMode(String id, String label, int presetWidth) {
        this.id = id;
        this.label = label;
        this.presetWidth = presetWidth;
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

    @Override
    public String toString() {
        return label;
    }
}
