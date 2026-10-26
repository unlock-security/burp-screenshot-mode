package it.unlocksecurity.burp.screenshotmode.storage;

import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.Template;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The built-in templates followed by the user's own, which are the only ones stored. Templates handed out are the
 * store's instances and must be treated as read-only: callers copy {@link Template#settings} before editing.
 */
public final class TemplateStore {

    static final String STORAGE_KEY = "screenshot-mode.templates";
    /** Where saved templates that could not be read are kept, so the defaults never overwrite the only copy. */
    static final String UNREADABLE_KEY = "screenshot-mode.templates.unreadable";

    private final PreferenceStore preferences;
    private final Consumer<String> log;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private final List<Template> builtIns = BuiltInTemplates.all();
    private List<Template> templates = new ArrayList<>();
    private String defaultTemplateId = BuiltInTemplates.DEFAULTS_ID;
    private String unreadable;

    public TemplateStore(PreferenceStore preferences, Consumer<String> log) {
        this.preferences = preferences;
        this.log = log;
    }

    public void initialize() {
        String stored = preferences.get(STORAGE_KEY);
        TemplateCodec.Decoded decoded;
        if (stored == null || stored.isBlank()) {
            decoded = new TemplateCodec.Decoded(empty(), true);
        } else {
            try {
                decoded = TemplateCodec.decode(stored);
            } catch (IllegalArgumentException e) {
                preferences.set(UNREADABLE_KEY, stored);
                unreadable = stored;
                log.accept(
                    "Could not read the saved templates (" +
                        e.getMessage() +
                        "). Started from the built-in templates; the original data is kept in the preference " +
                        UNREADABLE_KEY +
                        "."
                );
                decoded = new TemplateCodec.Decoded(empty(), true);
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

    /** The built-in templates first, then the user's own in the order they were created. */
    public List<Template> templates() {
        List<Template> all = new ArrayList<>(builtIns);
        all.addAll(templates);
        return List.copyOf(all);
    }

    /** The user's own templates, in the order they were created: the ones an export can hold. */
    public List<Template> userTemplates() {
        return List.copyOf(templates);
    }

    public String defaultTemplateId() {
        return defaultTemplateId;
    }

    /**
     * The template with this name, built-in ones included. Names are unique ignoring case and surrounding spaces, so
     * "report " finds "Report".
     */
    public Optional<Template> templateNamed(String name) {
        String wanted = name.strip();
        return templates()
            .stream()
            .filter(template -> template.name.strip().equalsIgnoreCase(wanted))
            .findFirst();
    }

    /** The first of "name (word)", "name (word 2)", ... that no template has. */
    public String freeName(String name, String word) {
        return freeName(name, word, candidate -> templateNamed(candidate).isPresent());
    }

    private static String freeName(String name, String word, Predicate<String> taken) {
        String candidate = name.strip() + " (" + word + ")";
        for (int i = 2; taken.test(candidate); i++) {
            candidate = name.strip() + " (" + word + " " + i + ")";
        }
        return candidate;
    }

    /** @throws IllegalArgumentException if another template already has the name */
    private void checkFree(String name, String id) {
        templateNamed(name)
            .filter(other -> !other.id.equals(id))
            .ifPresent(other -> {
                throw new IllegalArgumentException("A template named \"" + other.name + "\" already exists");
            });
    }

    /** @throws IllegalArgumentException if a template already has the name */
    public Template createTemplate(String name, Settings settings) {
        checkFree(name, null);
        Template template = Template.create(name, (settings == null ? BuiltInTemplates.defaults() : settings).copy());
        templates.add(template);
        persistAndNotify();
        return template;
    }

    /**
     * Updates the name and/or settings (either may be {@code null} to keep the current value). Built-in templates are
     * never updated.
     *
     * @throws IllegalArgumentException if another template already has the new name
     */
    public Optional<Template> updateTemplate(String id, String name, Settings settings) {
        if (name != null) {
            checkFree(name, id);
        }
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

    /** Deletes one of the user's templates; Defaults becomes the default template if it was. */
    public boolean deleteTemplate(String id) {
        boolean removed = templates.removeIf(template -> template.id.equals(id));
        if (removed) {
            if (id.equals(defaultTemplateId)) {
                defaultTemplateId = BuiltInTemplates.DEFAULTS_ID;
            }
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
        return getTemplateById(defaultTemplateId).orElse(builtIns.getFirst());
    }

    public Optional<Template> getTemplateById(String id) {
        return templates()
            .stream()
            .filter(template -> template.id.equals(id))
            .findFirst();
    }

    /** A template imported under another name, because its own was taken. */
    public record Rename(String from, String to) {}

    /** How many templates an import added, and which of them it renamed. */
    public record Imported(int count, List<Rename> renamed) {}

    /**
     * The names {@link #importTemplates} would give these templates: a name that is taken, also by an earlier one of
     * them, becomes "name (imported)", so nothing is overwritten.
     */
    public List<String> importNames(List<Template> imported) {
        Set<String> taken = new HashSet<>();
        Predicate<String> isTaken = name -> templateNamed(name).isPresent() || taken.contains(key(name));
        List<String> names = new ArrayList<>();
        for (Template template : imported) {
            String name = isTaken.test(template.name) ? freeName(template.name, "imported", isTaken) : template.name;
            taken.add(key(name));
            names.add(name);
        }
        return names;
    }

    private static String key(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    /** Adds copies of templates read from an export, named as {@link #importNames} says. */
    public Imported importTemplates(List<Template> imported) {
        List<String> names = importNames(imported);
        List<Rename> renamed = new ArrayList<>();
        for (int i = 0; i < imported.size(); i++) {
            Template copy = TemplateCodec.withNewId(imported.get(i));
            if (!names.get(i).equals(copy.name)) {
                renamed.add(new Rename(copy.name, names.get(i)));
            }
            templates.add(new Template(copy.id, names.get(i), copy.settings));
        }
        if (!imported.isEmpty()) {
            persistAndNotify();
        }
        return new Imported(imported.size(), List.copyOf(renamed));
    }

    /**
     * The templates of an exported file (any supported version).
     *
     * @throws IllegalArgumentException if the content is not a supported template export.
     */
    public static List<Template> readExport(String json) {
        return TemplateCodec.decode(json).data().templates();
    }

    /** These templates as an export. The built-in ones ship with every installation: leave them out. */
    public String exportJson(List<Template> exported) {
        return TemplateCodec.encode(new StoredData(exported, defaultTemplateId));
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private static StoredData empty() {
        return new StoredData(new ArrayList<>(), BuiltInTemplates.DEFAULTS_ID);
    }

    private void persistAndNotify() {
        persist();
        listeners.forEach(Runnable::run);
    }

    private void persist() {
        preferences.set(STORAGE_KEY, TemplateCodec.encode(new StoredData(templates, defaultTemplateId)));
    }
}
