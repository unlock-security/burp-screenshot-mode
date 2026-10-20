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
    void initializesAndPersistsADefaultTemplate() {
        assertEquals(1, store.templates().size());
        assertEquals(store.templates().getFirst().id, store.defaultTemplateId());
        assertNotNull(backing.get(TemplateStore.STORAGE_KEY));
    }

    @Test
    void reloadsPersistedTemplates() {
        Template created = store.createTemplate("Report", DefaultTemplate.settings());
        TemplateStore reloaded = new TemplateStore(preferences, message -> {});
        reloaded.initialize();
        assertEquals("Report", reloaded.getTemplateById(created.id).orElseThrow().name);
    }

    @Test
    void createCopiesSettings() {
        Settings settings = DefaultTemplate.settings();
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
        Settings settings = DefaultTemplate.settings();
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
    void cannotDeleteTheDefaultTemplate() {
        assertFalse(store.deleteTemplate(store.defaultTemplateId()));
        Template other = store.createTemplate("Other", null);
        assertTrue(store.setDefaultTemplate(other.id));
        assertEquals(other.id, store.getDefaultTemplate().id);
        assertFalse(store.setDefaultTemplate("missing"));
        assertTrue(store.deleteTemplate(store.templates().getFirst().id));
        assertEquals(1, store.templates().size());
    }

    @Test
    void importsExportedTemplatesWithFreshIds() {
        String exported = store.exportJson();
        String originalId = store.templates().getFirst().id;
        assertEquals(1, store.importTemplates(exported));
        assertEquals(2, store.templates().size());
        assertNotEquals(originalId, store.templates().get(1).id);
        assertThrows(IllegalArgumentException.class, () -> store.importTemplates("{\"nope\": true}"));
    }
}
