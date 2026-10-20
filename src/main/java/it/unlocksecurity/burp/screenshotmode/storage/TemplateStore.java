package it.unlocksecurity.burp.screenshotmode.storage;

import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.Template;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Port of the Caido templates store. Templates handed out are the stored instances and must be treated as
 * read-only: callers copy {@link Template#settings} before editing.
 */
public final class TemplateStore {

    static final String STORAGE_KEY = "screenshot-mode.templates";
    /** Where saved templates that could not be read are kept, so the defaults never overwrite the only copy. */
    static final String UNREADABLE_KEY = "screenshot-mode.templates.unreadable";

    private final PreferenceStore preferences;
    private final Consumer<String> log;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private List<Template> templates = new ArrayList<>();
    private String defaultTemplateId = "";
    private String unreadable;

    public TemplateStore(PreferenceStore preferences, Consumer<String> log) {
        this.preferences = preferences;
        this.log = log;
    }

    public void initialize() {
        String stored = preferences.get(STORAGE_KEY);
        TemplateCodec.Decoded decoded;
        if (stored == null || stored.isBlank()) {
            decoded = new TemplateCodec.Decoded(TemplateCodec.createDefault(), true);
        } else {
            try {
                decoded = TemplateCodec.decode(stored);
            } catch (IllegalArgumentException e) {
                preferences.set(UNREADABLE_KEY, stored);
                unreadable = stored;
                log.accept(
                    "Could not read the saved templates (" +
                        e.getMessage() +
                        "). Started from the default template; the original data is kept in the preference " +
                        UNREADABLE_KEY +
                        "."
                );
                decoded = new TemplateCodec.Decoded(TemplateCodec.createDefault(), true);
            }
        }
        templates = new ArrayList<>(decoded.data().templates());
        defaultTemplateId = decoded.data().defaultTemplateId();
        if (decoded.migrated()) {
            log.accept("Initialized template storage (version " + TemplateCodec.VERSION + ")");
            persist();
        }
    }

    /** The saved templates that could not be read at startup, as stored, so they can be exported; empty if none. */
    public Optional<String> unreadableTemplates() {
        return Optional.ofNullable(unreadable);
    }

    public List<Template> templates() {
        return List.copyOf(templates);
    }

    public String defaultTemplateId() {
        return defaultTemplateId;
    }

    public Template createTemplate(String name, Settings settings) {
        Template template = Template.create(name, (settings == null ? DefaultTemplate.settings() : settings).copy());
        templates.add(template);
        persistAndNotify();
        return template;
    }

    /** Updates the name and/or settings (either may be {@code null} to keep the current value). */
    public Optional<Template> updateTemplate(String id, String name, Settings settings) {
        for (int i = 0; i < templates.size(); i++) {
            Template current = templates.get(i);
            if (current.id.equals(id)) {
                Template updated = new Template(
                    id,
                    name == null ? current.name : name,
                    settings == null ? current.settings : settings.copy()
                );
                templates.set(i, updated);
                persistAndNotify();
                return Optional.of(updated);
            }
        }
        return Optional.empty();
    }

    public boolean deleteTemplate(String id) {
        if (id.equals(defaultTemplateId)) {
            return false;
        }
        boolean removed = templates.removeIf(template -> template.id.equals(id));
        if (removed) {
            persistAndNotify();
        }
        return removed;
    }

    public boolean setDefaultTemplate(String id) {
        if (getTemplateById(id).isEmpty()) {
            return false;
        }
        defaultTemplateId = id;
        persistAndNotify();
        return true;
    }

    public Template getDefaultTemplate() {
        return getTemplateById(defaultTemplateId)
            .or(() -> templates.stream().findFirst())
            .orElseGet(() -> new Template("", "Default", DefaultTemplate.settings()));
    }

    public Optional<Template> getTemplateById(String id) {
        return templates
            .stream()
            .filter(template -> template.id.equals(id))
            .findFirst();
    }

    /**
     * Appends the templates from an exported file (any supported version) and returns how many were added.
     *
     * @throws IllegalArgumentException if the content is not a supported template export.
     */
    public int importTemplates(String json) {
        List<Template> imported = TemplateCodec.decode(json).data().templates();
        for (Template template : imported) {
            templates.add(TemplateCodec.withNewId(template));
        }
        if (!imported.isEmpty()) {
            persistAndNotify();
        }
        return imported.size();
    }

    public String exportJson() {
        return TemplateCodec.encode(new StoredData(templates, defaultTemplateId));
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void persistAndNotify() {
        persist();
        listeners.forEach(Runnable::run);
    }

    private void persist() {
        preferences.set(STORAGE_KEY, TemplateCodec.encode(new StoredData(templates, defaultTemplateId)));
    }
}
