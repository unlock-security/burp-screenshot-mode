package it.unlocksecurity.burp.screenshotmode.model;

public enum WidthMode implements Labeled {
    FULL("full", "Full Width", 0),
    PIXEL("pixel", "Custom (px)", 0),
    A4("a4", "A4 (595px)", 595),
    LETTER("letter", "Letter (612px)", 612);

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
