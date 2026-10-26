package it.unlocksecurity.burp.screenshotmode.model;

public enum RedactionMode implements Labeled {
    BLUR("blur", "Blur"),
    OPAQUE("opaque", "Black box"),
    REPLACE("replace", "Text");

    private final String id;
    private final String label;

    RedactionMode(String id, String label) {
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
