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

    private final PreferenceStore preferences;
    private final Consumer<String> log;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private List<Template> templates = new ArrayList<>();
    private String defaultTemplateId = "";

    public TemplateStore(PreferenceStore preferences, Consumer<String> log) {
        this.preferences = preferences;
        this.log = log;
    }

    public void initialize() {
        TemplateCodec.Decoded decoded = TemplateCodec.decodeOrDefault(preferences.get(STORAGE_KEY));
        templates = new ArrayList<>(decoded.data().templates());
        defaultTemplateId = decoded.data().defaultTemplateId();
        if (decoded.migrated()) {
            log.accept("Initialized template storage (version " + TemplateCodec.VERSION + ")");
            persist();
        }
    }

    public List<Template> templates() {
        return List.copyOf(templates);
    }

    public String defaultTemplateId() {
        return defaultTemplateId;
    }

    public Template createTemplate(String name, Settings settings) {
        Template template = Template.create(name, (settings == null ? Settings.defaults() : settings).copy());
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
            .orElseGet(() -> new Template("", "Default", Settings.defaults()));
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
