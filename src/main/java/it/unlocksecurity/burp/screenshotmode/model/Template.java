package it.unlocksecurity.burp.screenshotmode.model;

import java.util.UUID;

public final class Template {

    public String id;
    public String name;
    public Settings settings;

    public Template(String id, String name, Settings settings) {
        this.id = id;
        this.name = name;
        this.settings = settings;
    }

    public static Template create(String name, Settings settings) {
        return new Template(UUID.randomUUID().toString(), name, settings);
    }

    public Template copy() {
        return new Template(id, name, settings.copy());
    }

    @Override
    public String toString() {
        return name;
    }
}
