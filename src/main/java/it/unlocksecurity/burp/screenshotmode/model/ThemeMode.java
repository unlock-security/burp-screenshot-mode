package it.unlocksecurity.burp.screenshotmode.model;

public enum ThemeMode implements Labeled {
    AUTO("auto", "Follow Burp"),
    DARK("dark", "Dark"),
    LIGHT("light", "Light");

    private final String id;
    private final String label;

    ThemeMode(String id, String label) {
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
