package it.unlocksecurity.burp.screenshotmode.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.Template;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TemplateStoreTest {

    private final Map<String, String> backing = new HashMap<>();
    private final PreferenceStore preferences = new PreferenceStore() {
        @Override
        public String get(String key) {
            return backing.get(key);
        }

        @Override
        public void set(String key, String value) {
            backing.put(key, value);
        }
    };
    private TemplateStore store;

    @BeforeEach
    void setUp() {
        store = new TemplateStore(preferences, message -> {});
        store.initialize();
    }

    @Test
    void startsWithTheBuiltInTemplatesOnly() {
        assertEquals(BuiltInTemplates.all().size(), store.templates().size());
        assertTrue(
            store
                .templates()
                .stream()
                .allMatch(template -> template.builtIn)
        );
        assertEquals(BuiltInTemplates.DEFAULTS_ID, store.defaultTemplateId());
        assertEquals(BuiltInTemplates.DEFAULTS_ID, store.getDefaultTemplate().id);
        assertNotNull(backing.get(TemplateStore.STORAGE_KEY));
        assertTrue(
            TemplateCodec.decode(backing.get(TemplateStore.STORAGE_KEY)).data().templates().isEmpty(),
            "built-in templates are not stored"
        );
        assertTrue(store.unreadableTemplates().isEmpty(), "a first start is not a failure");
        assertFalse(backing.containsKey(TemplateStore.UNREADABLE_KEY));
    }

    @Test
    void keepsTemplatesItCannotReadInsteadOfOverwritingThem() {
        for (String stored : new String[] { "garbage", "{}", "{\"version\": 99, \"templates\": []}" }) {
            backing.clear();
            backing.put(TemplateStore.STORAGE_KEY, stored);
            TemplateStore reloaded = new TemplateStore(preferences, message -> {});
            reloaded.initialize();
            assertEquals(stored, backing.get(TemplateStore.UNREADABLE_KEY), "the original data is kept");
            assertEquals(stored, reloaded.unreadableTemplates().orElseThrow(), "and offered for export");
            assertEquals(BuiltInTemplates.all().size(), reloaded.templates().size(), "work goes on");
            assertEquals(BuiltInTemplates.DEFAULTS_ID, reloaded.getDefaultTemplate().id);
        }
    }

    @Test
    void reloadsPersistedTemplates() {
        Template created = store.createTemplate("Report", BuiltInTemplates.defaults());
        TemplateStore reloaded = new TemplateStore(preferences, message -> {});
        reloaded.initialize();
        assertEquals("Report", reloaded.getTemplateById(created.id).orElseThrow().name);
    }

    @Test
    void createCopiesSettings() {
        Settings settings = BuiltInTemplates.defaults();
        Template created = store.createTemplate("Copy", settings);
        settings.showTimestamp = true;
        assertFalse(created.settings.showTimestamp);
        assertNotSame(settings, created.settings);
    }

    @Test
    void updatesNameAndSettingsAndNotifies() {
        AtomicInteger notifications = new AtomicInteger();
        store.addListener(notifications::incrementAndGet);
        Template created = store.createTemplate("A", null);
        Settings settings = BuiltInTemplates.defaults();
        settings.showTimestamp = true;
        store.updateTemplate(created.id, null, settings);
        store.updateTemplate(created.id, "B", null);
        Template updated = store.getTemplateById(created.id).orElseThrow();
        assertEquals("B", updated.name);
        assertTrue(updated.settings.showTimestamp);
        assertEquals(3, notifications.get());
        assertTrue(store.updateTemplate("missing", "x", null).isEmpty());
    }

    @Test
    void neverChangesOrDeletesBuiltInTemplates() {
        for (Template builtIn : BuiltInTemplates.all()) {
            assertTrue(store.updateTemplate(builtIn.id, "Renamed", new Settings()).isEmpty());
            assertFalse(store.deleteTemplate(builtIn.id));
            assertEquals(builtIn.name, store.getTemplateById(builtIn.id).orElseThrow().name);
        }
    }

    @Test
    void anyTemplateCanBeTheDefault() {
        assertTrue(store.setDefaultTemplate(BuiltInTemplates.EMPTY_ID));
        assertEquals(BuiltInTemplates.EMPTY_ID, store.getDefaultTemplate().id);
        Template other = store.createTemplate("Other", null);
        assertTrue(store.setDefaultTemplate(other.id));
        assertEquals(other.id, store.getDefaultTemplate().id);
        assertFalse(store.setDefaultTemplate("missing"));

        TemplateStore reloaded = new TemplateStore(preferences, message -> {});
        reloaded.initialize();
        assertEquals(other.id, reloaded.getDefaultTemplate().id);
    }

    @Test
    void deletingTheDefaultTemplateMakesDefaultsTheDefault() {
        Template other = store.createTemplate("Other", null);
        store.setDefaultTemplate(other.id);
        assertTrue(store.deleteTemplate(other.id));
        assertEquals(BuiltInTemplates.DEFAULTS_ID, store.defaultTemplateId());
        assertEquals(BuiltInTemplates.all().size(), store.templates().size());
    }

    @Test
    void importsExportedTemplatesWithFreshIds() {
        Template original = store.createTemplate("Report", null);
        String exported = store.exportJson();
        assertEquals(1, TemplateCodec.decode(exported).data().templates().size(), "built-in templates are left out");
        assertEquals(1, store.importTemplates(exported));
        assertEquals(BuiltInTemplates.all().size() + 2, store.templates().size());
        assertNotEquals(original.id, store.templates().getLast().id);
        assertEquals("Report", store.templates().getLast().name);
        assertThrows(IllegalArgumentException.class, () -> store.importTemplates("{\"nope\": true}"));
    }
}
