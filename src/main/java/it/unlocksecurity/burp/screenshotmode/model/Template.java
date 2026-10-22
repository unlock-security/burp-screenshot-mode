package it.unlocksecurity.burp.screenshotmode.model;

import java.util.UUID;

public final class Template {

    public String id;
    public String name;
    public Settings settings;
    /** Bundled with the extension (see {@code storage.BuiltInTemplates}): never stored, edited or deleted. */
    public final boolean builtIn;

    public Template(String id, String name, Settings settings) {
        this(id, name, settings, false);
    }

    public Template(String id, String name, Settings settings, boolean builtIn) {
        this.id = id;
        this.name = name;
        this.settings = settings;
        this.builtIn = builtIn;
    }

    public static Template create(String name, Settings settings) {
        return new Template(UUID.randomUUID().toString(), name, settings);
    }

    public Template copy() {
        return new Template(id, name, settings.copy(), builtIn);
    }

    @Override
    public String toString() {
        return name;
    }
}
