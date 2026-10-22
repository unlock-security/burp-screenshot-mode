package it.unlocksecurity.burp.screenshotmode.model;

/** How tall the screenshot is. */
public enum HeightMode implements Labeled {
    /** As tall as the preview, like {@link WidthMode#FULL} for the width. */
    FULL("full", "Full Height"),
    /** As tall as the request and response need, so every line shows. */
    CONTENT("content", "Fit to content"),
    PIXEL("pixel", "Custom (px)");

    private final String id;
    private final String label;

    HeightMode(String id, String label) {
        this.id = id;
        this.label = label;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
