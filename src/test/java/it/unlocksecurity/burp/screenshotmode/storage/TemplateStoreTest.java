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
import java.util.List;
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
        assertEquals(List.of(original), store.userTemplates(), "built-in templates are left out");
        String exported = store.exportJson(store.userTemplates());
        assertEquals(1, store.importTemplates(TemplateStore.readExport(exported)).count());
        assertEquals(BuiltInTemplates.all().size() + 2, store.templates().size());
        assertNotEquals(original.id, store.templates().getLast().id);
        assertThrows(IllegalArgumentException.class, () -> TemplateStore.readExport("{\"nope\": true}"));
    }

    @Test
    void namesAreUniqueIgnoringCaseAndSpaces() {
        Template report = store.createTemplate("Report", null);
        assertEquals(report.id, store.templateNamed("  report ").orElseThrow().id);
        assertEquals(BuiltInTemplates.DEFAULTS_ID, store.templateNamed("DEFAULTS").orElseThrow().id, "built-in too");

        assertThrows(IllegalArgumentException.class, () -> store.createTemplate("REPORT ", null));
        assertThrows(IllegalArgumentException.class, () -> store.createTemplate("Empty", null));
        Template other = store.createTemplate("Other", null);
        assertThrows(IllegalArgumentException.class, () -> store.updateTemplate(other.id, "report", null));
        assertEquals("Other", store.getTemplateById(other.id).orElseThrow().name);
        assertTrue(store.updateTemplate(report.id, "REPORT", null).isPresent(), "its own name, recased");
        assertTrue(store.updateTemplate(other.id, null, BuiltInTemplates.defaults()).isPresent(), "name kept");
    }

    @Test
    void findsAFreeName() {
        store.createTemplate("Report", null);
        assertEquals("Report (copy)", store.freeName("Report", "copy"));
        store.createTemplate("report (COPY)", null);
        assertEquals("Report (copy 2)", store.freeName("Report", "copy"));
    }

    @Test
    void importsTemplatesWithTakenNamesUnderNewOnes() {
        store.createTemplate("Report", null);
        store.createTemplate("Other", null);
        store.createTemplate("Mine", null);
        // A file with the same name twice ("Report", "report") and a built-in one's ("defaults").
        String file = store
            .exportJson(store.userTemplates())
            .replace("\"Other\"", "\"report\"")
            .replace("\"Mine\"", "\"defaults\"");

        TemplateStore fresh = new TemplateStore(new InMemory(), message -> {});
        fresh.initialize();
        List<Template> read = TemplateStore.readExport(file);
        assertEquals(List.of("Report", "report (imported)", "defaults (imported)"), fresh.importNames(read));
        assertEquals(List.of("report"), fresh.importNames(read.subList(1, 2)), "free without the first one");
        TemplateStore.Imported imported = fresh.importTemplates(read);
        assertEquals(3, imported.count());
        assertEquals(
            List.of(
                new TemplateStore.Rename("report", "report (imported)"),
                new TemplateStore.Rename("defaults", "defaults (imported)")
            ),
            imported.renamed()
        );
        assertEquals("Report", fresh.templates().get(BuiltInTemplates.all().size()).name, "a free name is kept");

        imported = fresh.importTemplates(read);
        assertEquals("Report (imported 2)", imported.renamed().getFirst().to());
        assertEquals(3, imported.renamed().size());
        long distinct = fresh
            .templates()
            .stream()
            .map(template -> template.name.toLowerCase())
            .distinct()
            .count();
        assertEquals(fresh.templates().size(), distinct);
    }

    private static final class InMemory implements PreferenceStore {

        private final Map<String, String> values = new HashMap<>();

        @Override
        public String get(String key) {
            return values.get(key);
        }

        @Override
        public void set(String key, String value) {
            values.put(key, value);
        }
    }
}
