package it.unlocksecurity.burp.screenshotmode.model;

/** Which messages the screenshot shows: one alone needs no cropping. */
public enum MessageMode implements Labeled {
    BOTH("both", "Both"),
    REQUEST("request", "Request"),
    RESPONSE("response", "Response");

    private final String id;
    private final String label;

    MessageMode(String id, String label) {
        this.id = id;
        this.label = label;
    }

    /** Whether the message of {@code target} is shown; the URL bar has a setting of its own. */
    public boolean shows(RuleTarget target) {
        return switch (target) {
            case REQUEST -> this != RESPONSE;
            case RESPONSE -> this != REQUEST;
            case URL -> true;
        };
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
