package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.Disposition;
import it.unlocksecurity.burp.screenshotmode.model.Template;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import it.unlocksecurity.burp.screenshotmode.storage.PreferenceStore;
import it.unlocksecurity.burp.screenshotmode.storage.TemplateStore;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class TemplatesTabTest {

    @Test
    void duplicatesTheSelectedTemplateUnderAFreeName() throws Exception {
        TemplateStore store = store();
        Template original = store.createTemplate("Report", RenderPreviewTest.sampleSettings());
        store.createTemplate(original.name + " (copy)", null);

        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            AbstractButton duplicate = button(tab, "Duplicate");
            assertFalse(duplicate.isEnabled(), "nothing selected yet");

            select(tab, original);
            duplicate.doClick();
            JTextField name = find(tab, JTextField.class, field -> field.getText().startsWith(original.name)).get();
            assertEquals(original.name + " (copy 2)", name.getText());
            button(tab, "Save").doClick();
        });

        List<Template> templates = store.templates();
        assertEquals(BuiltInTemplates.all().size() + 3, templates.size());
        Template copy = templates.getLast();
        Template source = store.getTemplateById(original.id).get();
        assertEquals(original.name + " (copy 2)", copy.name);
        assertNotEquals(source.id, copy.id);
        assertEquals(BuiltInTemplates.DEFAULTS_ID, store.defaultTemplateId(), "the copy is not the default");
        assertEquals(source.settings.highlights.size(), copy.settings.highlights.size());
        assertEquals(source.settings.redactions.size(), copy.settings.redactions.size());
        assertTrue(copy.settings.highlights.size() > 0);
        assertEquals(BuiltInTemplates.DEFAULTS_ID, copy.settings.basedOn, "same built-in parent");

        copy.settings.disposition = Disposition.VERTICAL;
        copy.settings.highlights.clear();
        assertEquals(Disposition.HORIZONTAL, source.settings.disposition, "settings are not shared");
        assertFalse(source.settings.highlights.isEmpty(), "rules are not shared");
    }

    @Test
    void cancelDiscardsAnUntouchedDuplicate() throws Exception {
        TemplateStore store = store();
        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            select(tab, store.getDefaultTemplate());
            button(tab, "Duplicate").doClick();
            button(tab, "Cancel").doClick();
        });
        assertEquals(BuiltInTemplates.all().size(), store.templates().size());
    }

    @Test
    void everyButtonIsGreyedWhenDisabled() throws Exception {
        TemplateStore store = store();
        Template custom = store.createTemplate("Report", RenderPreviewTest.sampleSettings());
        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            assertAllStyled(tab);
            select(tab, custom);
            button(tab, "Edit").doClick();
            assertAllStyled(tab);
        });
    }

    @Test
    void alignsHeadersLikeTheirValues() throws Exception {
        TemplateStore store = store();
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(new TemplatesTab(store, host()), JTable.class, t -> true).get();
            for (int column = 0; column < table.getColumnCount(); column++) {
                int expected = table.getColumnName(column).equals("Name")
                    ? javax.swing.SwingConstants.LEADING
                    : javax.swing.SwingConstants.CENTER;
                javax.swing.JLabel cell = (javax.swing.JLabel) table.prepareRenderer(
                    table.getCellRenderer(0, column),
                    0,
                    column
                );
                javax.swing.JLabel header = (javax.swing.JLabel) table
                    .getColumnModel()
                    .getColumn(column)
                    .getHeaderRenderer()
                    .getTableCellRendererComponent(table, table.getColumnName(column), false, false, -1, column);
                assertEquals(expected, cell.getHorizontalAlignment(), table.getColumnName(column) + " values");
                assertEquals(expected, header.getHorizontalAlignment(), table.getColumnName(column) + " header");
            }
        });
    }

    private static void assertAllStyled(Container root) {
        List<AbstractButton> buttons = new java.util.ArrayList<>();
        collect(root, buttons);
        assertTrue(buttons.size() > 5, "found " + buttons.size() + " buttons");
        for (AbstractButton button : buttons) {
            assertTrue(
                button.getClientProperty("FlatLaf.style") != null,
                "not created through Widgets: " + button.getText() + " " + button.getToolTipText()
            );
        }
    }

    private static void collect(Container root, List<AbstractButton> buttons) {
        for (Component child : root.getComponents()) {
            // Exact classes: subclasses are look-and-feel parts such as scroll bar and combo box arrows.
            if (child.getClass() == javax.swing.JButton.class || child.getClass() == javax.swing.JToggleButton.class) {
                buttons.add((AbstractButton) child);
            }
            if (child instanceof Container container) {
                collect(container, buttons);
            }
        }
    }

    @Test
    void cancelAsksBeforeDiscardingChanges() throws Exception {
        TemplateStore store = store();
        Template original = store.createTemplate("Report", null);
        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            List<String> asked = new java.util.ArrayList<>();
            boolean[] discard = { false };
            tab.setDiscardConfirmation(name -> {
                asked.add(name);
                return discard[0];
            });
            select(tab, original);
            button(tab, "Edit").doClick();
            button(tab, "Cancel").doClick();
            assertTrue(asked.isEmpty(), "nothing to discard yet");
            assertTrue(find(tab, JTable.class, t -> isShowing(t, tab)).isPresent(), "back to the list");

            button(tab, "Edit").doClick();
            JTextField name = find(tab, JTextField.class, f -> f.getText().equals(original.name)).get();
            name.setText("Renamed");
            button(tab, "Cancel").doClick();
            assertEquals(List.of("Your changes to \"" + original.name + "\" haven't been saved."), asked);
            button(tab, "Save"); // still editing: throws if the Save button is not showing

            discard[0] = true;
            button(tab, "Cancel").doClick();
            assertTrue(find(tab, JTable.class, t -> isShowing(t, tab)).isPresent(), "back to the list");
        });
        assertEquals(original.name, store.getTemplateById(original.id).orElseThrow().name, "nothing was saved");
    }

    @Test
    void builtInTemplatesCanOnlyBeDuplicatedOrMadeDefault() throws Exception {
        TemplateStore store = store();
        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            JTable table = find(tab, JTable.class, t -> true).get();
            for (Template builtIn : BuiltInTemplates.all()) {
                select(tab, builtIn);
                assertEquals("Built-in", table.getValueAt(table.getSelectedRow(), 2), "says so in words");
                assertFalse(button(tab, "Edit").isEnabled());
                assertFalse(button(tab, "Delete").isEnabled());
                assertTrue(button(tab, "Duplicate").isEnabled());
            }
            assertTrue(button(tab, "Set as default").isEnabled(), "Empty can be the default");
            assertFalse(button(tab, "Export…").isEnabled(), "nothing of the user's to export");
        });
    }

    @Test
    void newTemplateStartsFromTheChosenBuiltInTemplate() throws Exception {
        TemplateStore store = store();
        List<String> asked = new java.util.ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            tab.setDiscardConfirmation(message -> {
                asked.add(message);
                return false;
            });
            button(tab, "New template").doClick();
            JComboBox<?> basedOn = find(tab, JComboBox.class, c -> c.getSelectedItem() instanceof Template).get();
            assertEquals(BuiltInTemplates.DEFAULTS_ID, ((Template) basedOn.getSelectedItem()).id);

            // Unchanged settings: switching asks nothing and keeps the name typed so far.
            find(tab, JTextField.class, f -> f.getText().isEmpty() && isShowing(f, tab))
                .get()
                .setText("Clean");
            basedOn.setSelectedIndex(1);
            assertTrue(asked.isEmpty());
            JComboBox<?> rebuilt = find(tab, JComboBox.class, c -> c.getSelectedItem() instanceof Template).get();
            assertEquals(BuiltInTemplates.EMPTY_ID, ((Template) rebuilt.getSelectedItem()).id);
            assertTrue(find(tab, JTextField.class, f -> f.getText().equals("Clean") && isShowing(f, tab)).isPresent());

            // Changed settings: switching asks first, and keeping them keeps the choice.
            find(tab, AbstractButton.class, b -> "Stacked".equals(b.getText()))
                .get()
                .doClick();
            rebuilt.setSelectedIndex(0);
            assertEquals(1, asked.size());
            assertEquals(BuiltInTemplates.EMPTY_ID, ((Template) rebuilt.getSelectedItem()).id);

            button(tab, "Save").doClick();
        });
        Template created = store.templates().getLast();
        assertEquals("Clean", created.name);
        assertFalse(created.builtIn);
        assertTrue(created.settings.redactions.isEmpty(), "started from Empty");
        assertEquals(null, created.settings.basedOn, "and follows it");
        assertEquals(Disposition.VERTICAL, created.settings.disposition);
    }

    @Test
    void newTemplateIsThePrimaryAction() throws Exception {
        TemplateStore store = store();
        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            assertEquals("primary", button(tab, "New template").getClientProperty("FlatLaf.styleClass"));
        });
    }

    static TemplateStore store() {
        Map<String, String> values = new HashMap<>();
        TemplateStore store = new TemplateStore(preferences(values), message -> {});
        store.initialize();
        return store;
    }

    private static PreferenceStore preferences(Map<String, String> values) {
        return new PreferenceStore() {
            @Override
            public String get(String key) {
                return values.get(key);
            }

            @Override
            public void set(String key, String value) {
                values.put(key, value);
            }
        };
    }

    static Host host() {
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 13);
        return new Host(
            () -> font,
            () -> font,
            () -> false,
            () -> null,
            m -> {},
            m -> {},
            preferences(new HashMap<>())
        );
    }

    private static void select(TemplatesTab tab, Template template) {
        JTable table = find(tab, JTable.class, t -> true).get();
        for (int row = 0; row < table.getRowCount(); row++) {
            if (template.name.equals(table.getValueAt(row, 1))) {
                table.setRowSelectionInterval(row, row);
                return;
            }
        }
        throw new AssertionError("no row for " + template.name);
    }

    /** The visible button with this text: the list and the editor are cards, only one of them is shown. */
    private static AbstractButton button(Container root, String text) {
        return find(root, AbstractButton.class, b -> text.equals(b.getText()) && isShowing(b, root)).orElseThrow(() ->
            new AssertionError("no visible button " + text)
        );
    }

    private static boolean isShowing(Component component, Container root) {
        for (Component c = component; c != null && c != root; c = c.getParent()) {
            if (!c.isVisible()) {
                return false;
            }
        }
        return true;
    }

    private static <T extends Component> Optional<T> find(Container root, Class<T> type, Predicate<T> filter) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && filter.test(type.cast(child))) {
                return Optional.of(type.cast(child));
            }
            if (child instanceof Container container) {
                Optional<T> found = find(container, type, filter);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }
}
