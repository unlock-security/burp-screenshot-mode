package it.unlocksecurity.burp.screenshotmode.model;

/**
 * An enum constant with a stable storage id (written to saved templates and exports) and a UI label.
 */
public interface Labeled {
    String id();

    String label();

    static <E extends Enum<E> & Labeled> E fromId(Class<E> type, String id) {
        for (E constant : type.getEnumConstants()) {
            if (constant.id().equals(id)) {
                return constant;
            }
        }
        throw new IllegalArgumentException("Unknown " + type.getSimpleName() + ": " + id);
    }
}
