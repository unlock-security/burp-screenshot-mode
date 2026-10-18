package it.unlocksecurity.burp.screenshotmode.model;

public enum RuleTarget implements Labeled {
    REQUEST("request", "Request"),
    RESPONSE("response", "Response");

    private final String id;
    private final String label;

    RuleTarget(String id, String label) {
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
