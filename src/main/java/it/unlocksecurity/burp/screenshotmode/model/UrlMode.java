package it.unlocksecurity.burp.screenshotmode.model;

/** What the URL bar above the messages shows. */
public enum UrlMode implements Labeled {
    FULL("full", "Full URL"),
    /** Scheme, host and port left out; path and query kept. */
    PATH("path", "Path only"),
    HIDDEN("hidden", "Hidden");

    private final String id;
    private final String label;

    UrlMode(String id, String label) {
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
