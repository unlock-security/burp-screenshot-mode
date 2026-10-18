package it.unlocksecurity.burp.screenshotmode.model;

public enum MatchMode implements Labeled {
    REGEX("regex", "Regex"),
    STRING("string", "String");

    private final String id;
    private final String label;

    MatchMode(String id, String label) {
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
