package it.unlocksecurity.burp.screenshotmode.model;

/** Whether a header, or a group of headers, shows in the screenshot. */
public enum Visibility implements Labeled {
    SHOW("show", "Shown"),
    HIDE("hide", "Hidden"),
    HIDE_IN_REQUEST("hide-in-request", "Hidden in requests"),
    HIDE_IN_RESPONSE("hide-in-response", "Hidden in responses");

    private final String id;
    private final String label;

    Visibility(String id, String label) {
        this.id = id;
        this.label = label;
    }

    /** Hidden in the given message. */
    public boolean hidesIn(RuleTarget side) {
        return switch (this) {
            case SHOW -> false;
            case HIDE -> true;
            case HIDE_IN_REQUEST -> side == RuleTarget.REQUEST;
            case HIDE_IN_RESPONSE -> side == RuleTarget.RESPONSE;
        };
    }

    /** The messages a header is hidden in, or {@code null} if it is shown. */
    public Direction hiddenIn() {
        return switch (this) {
            case SHOW -> null;
            case HIDE -> Direction.BOTH;
            case HIDE_IN_REQUEST -> Direction.REQUEST;
            case HIDE_IN_RESPONSE -> Direction.RESPONSE;
        };
    }

    public static Visibility hiddenIn(Direction direction) {
        return switch (direction) {
            case BOTH -> HIDE;
            case REQUEST -> HIDE_IN_REQUEST;
            case RESPONSE -> HIDE_IN_RESPONSE;
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
}
