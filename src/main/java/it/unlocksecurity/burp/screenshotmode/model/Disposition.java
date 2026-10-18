package it.unlocksecurity.burp.screenshotmode.model;

public enum Disposition implements Labeled {
    HORIZONTAL("horizontal", "Side by Side"),
    VERTICAL("vertical", "Stacked");

    private final String id;
    private final String label;

    Disposition(String id, String label) {
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
