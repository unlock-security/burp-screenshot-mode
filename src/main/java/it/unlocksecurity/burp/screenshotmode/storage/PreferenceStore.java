package it.unlocksecurity.burp.screenshotmode.storage;

/** Minimal string key/value persistence, backed by Burp's preferences at runtime. */
public interface PreferenceStore {
    /** Returns the stored value, or {@code null} when absent. */
    String get(String key);

    void set(String key, String value);
}
