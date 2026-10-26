package it.unlocksecurity.burp.screenshotmode.model;

/** Where the size of the screenshot's text comes from. */
public enum TextSizeMode implements Labeled {
    /** Burp's editor and display fonts, as set in its settings. */
    BURP("burp", "Follow Burp"),
    /** {@link Settings#textSize} for the messages, whatever the Burp font: the same screenshot for everyone. */
    CUSTOM("custom", "Custom (pt)");

    private final String id;
    private final String label;

    TextSizeMode(String id, String label) {
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
