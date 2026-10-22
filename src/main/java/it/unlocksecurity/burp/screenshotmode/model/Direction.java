package it.unlocksecurity.burp.screenshotmode.model;

/** Which messages a header is used in. */
public enum Direction implements Labeled {
    BOTH("both", "Both"),
    REQUEST("request", "Request"),
    RESPONSE("response", "Response");

    private final String id;
    private final String label;

    Direction(String id, String label) {
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
