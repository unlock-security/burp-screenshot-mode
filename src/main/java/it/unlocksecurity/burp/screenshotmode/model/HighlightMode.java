package it.unlocksecurity.burp.screenshotmode.model;

public enum HighlightMode implements Labeled {
    HIGHLIGHT("highlight", "Highlight"),
    RECTANGLE("rectangle", "Rectangle");

    private final String id;
    private final String label;

    HighlightMode(String id, String label) {
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
