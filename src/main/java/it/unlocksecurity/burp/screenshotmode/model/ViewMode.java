package it.unlocksecurity.burp.screenshotmode.model;

/** How message bodies are shown, like the "Raw" and "Pretty" tabs of Burp's message editor. */
public enum ViewMode implements Labeled {
    RAW("raw", "Raw"),
    PRETTY("pretty", "Pretty");

    private final String id;
    private final String label;

    ViewMode(String id, String label) {
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
