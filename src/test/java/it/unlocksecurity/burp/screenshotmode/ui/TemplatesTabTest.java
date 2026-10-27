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
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
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
    void showsTheNameAndBasedOnWholeInANarrowEditor() throws Exception {
        TemplatesTab[] tab = new TemplatesTab[1];
        SwingUtilities.invokeAndWait(() -> {
            tab[0] = new TemplatesTab(store(), host());
            button(tab[0], "New template").doClick();
            tab[0].setSize(400, 800);
        });
        WrapLayoutTest.layOut(tab[0]);
        SwingUtilities.invokeAndWait(() -> {
            JComboBox<?> base = find(tab[0], JComboBox.class, c -> c.getSelectedItem() instanceof Template).get();
            JTextField name = find(tab[0], JTextField.class, f -> f.getText().isEmpty() && isShowing(f, tab[0])).get();
            assertTrue(WrapLayoutTest.inView(name, tab[0]), "the name field in view");
            assertTrue(WrapLayoutTest.inView(base, tab[0]), "Based on in view");
        });
    }

    @Test
    void savesAndKeepsEditingWhatItSaved() throws Exception {
        TemplateStore store = store();
        List<String> asked = new java.util.ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            tab.setDiscardConfirmation(message -> {
                asked.add(message);
                return false;
            });
            button(tab, "New template").doClick();
            JTextField name = find(tab, JTextField.class, f -> f.getText().isEmpty() && isShowing(f, tab)).get();
            name.setText("Report");
            assertTrue(find(tab, JComboBox.class, c -> c.getSelectedItem() instanceof Template).isPresent());
            button(tab, "Save").doClick();

            assertEquals(BuiltInTemplates.all().size() + 1, store.templates().size(), "created");
            Template created = store.templateNamed("Report").get();
            assertTrue(isShowing(button(tab, "Save"), tab), "still editing");
            assertTrue(isShowing(label(tab, "Edit \"Report\""), tab), "now an edit of the saved template");
            assertTrue(isShowing(label(tab, "Template created"), tab));
            assertFalse(label(tab, "Unsaved").isVisible());
            assertFalse(button(tab, "Save").isEnabled(), "nothing left to save");
            assertFalse(
                find(tab, JComboBox.class, c -> c.getSelectedItem() instanceof Template).isPresent(),
                "Based on is fixed once the template exists"
            );

            name.setText("Report A4");
            assertTrue(label(tab, "Unsaved").isVisible());
            assertTrue(button(tab, "Save").isEnabled());
            name.setText("Report");
            assertFalse(button(tab, "Save").isEnabled(), "back as saved");
            name.setText("Report A4");
            button(tab, "Save").doClick();
            assertEquals(BuiltInTemplates.all().size() + 1, store.templates().size(), "saved again, not created again");
            assertEquals("Report A4", store.getTemplateById(created.id).get().name);
            assertTrue(isShowing(label(tab, "Template saved"), tab));

            button(tab, "Close").doClick();
            assertEquals(List.of(), asked, "nothing unsaved to ask about");
            assertTrue(isShowing(button(tab, "New template"), tab), "back to the list");
        });
    }

    @Test
    void savesWithTheMenuShortcut() throws Exception {
        TemplateStore store = store();
        Template custom = store.createTemplate("Report", RenderPreviewTest.sampleSettings());
        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            select(tab, custom);
            button(tab, "Edit").doClick();
            JTextField name = find(tab, JTextField.class, field -> field.getText().equals("Report")).get();
            assertFalse(button(tab, "Save").isEnabled(), "an untouched template has nothing to save");
            name.setText("Renamed");
            javax.swing.KeyStroke key = javax.swing.KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_S,
                Widgets.menuShortcutMask()
            );
            JComponent editor = (JComponent) SwingUtilities.getAncestorOfClass(JComponent.class, name);
            while (editor.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(key) == null) {
                editor = (JComponent) editor.getParent();
            }
            Object action = editor.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(key);
            editor.getActionMap().get(action).actionPerformed(null);
            assertEquals("Renamed", store.getTemplateById(custom.id).get().name);
            assertTrue(isShowing(button(tab, "Save"), tab), "still editing");
        });
    }

    @Test
    void namesTheExportAfterItsTemplate() {
        Template report = Template.create("Report A4", null);
        Template letter = Template.create("Letter", null);
        assertEquals("screenshot-mode-report-a4-template.json", TemplatesTab.exportFileName(List.of(report)));
        assertEquals("screenshot-mode-templates.json", TemplatesTab.exportFileName(List.of(report, letter)), "several");
        assertEquals(
            "screenshot-mode-client-citta-report-v2-template.json",
            TemplatesTab.exportFileName(List.of(Template.create(" Client: Città / report_v2? ", null)))
        );
        assertEquals(
            "screenshot-mode-templates.json",
            TemplatesTab.exportFileName(List.of(Template.create("報告", null))),
            "nothing left of the name"
        );
    }

    /** The label with this text. */
    private static JLabel label(java.awt.Container root, String text) {
        return find(root, JLabel.class, label -> text.equals(label.getText())).orElseThrow(() ->
            new AssertionError("no label " + text)
        );
    }

    @Test
    void closeDiscardsAnUntouchedDuplicate() throws Exception {
        TemplateStore store = store();
        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            select(tab, store.getDefaultTemplate());
            button(tab, "Duplicate").doClick();
            button(tab, "Close").doClick();
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
    void closeAsksBeforeDiscardingChanges() throws Exception {
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
            button(tab, "Close").doClick();
            assertTrue(asked.isEmpty(), "nothing to discard yet");
            assertTrue(find(tab, JTable.class, t -> isShowing(t, tab)).isPresent(), "back to the list");

            button(tab, "Edit").doClick();
            JTextField name = find(tab, JTextField.class, f -> f.getText().equals(original.name)).get();
            name.setText("Renamed");
            button(tab, "Close").doClick();
            assertEquals(List.of("Your changes to \"" + original.name + "\" haven't been saved."), asked);
            button(tab, "Save"); // still editing: throws if the Save button is not showing

            discard[0] = true;
            button(tab, "Close").doClick();
            assertTrue(find(tab, JTable.class, t -> isShowing(t, tab)).isPresent(), "back to the list");
        });
        assertEquals(original.name, store.getTemplateById(original.id).orElseThrow().name, "nothing was saved");
    }

    @Test
    void templatesInTheHeadingLeadBackToTheListLikeClose() throws Exception {
        TemplateStore store = store();
        Template original = store.createTemplate("Report", null);
        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            List<String> asked = new java.util.ArrayList<>();
            tab.setDiscardConfirmation(name -> {
                asked.add(name);
                return false;
            });
            select(tab, original);
            button(tab, "Edit").doClick();
            AbstractButton back = button(tab, "Templates");
            assertEquals("Back to templates", back.getAccessibleContext().getAccessibleName());
            back.doClick();
            assertTrue(find(tab, JTable.class, t -> isShowing(t, tab)).isPresent(), "back to the list");

            button(tab, "Edit").doClick();
            find(tab, JTextField.class, f -> f.getText().equals(original.name))
                .get()
                .setText("Renamed");
            button(tab, "Templates").doClick();
            assertEquals(1, asked.size(), "asks before discarding the changes");
            button(tab, "Save"); // still editing
        });
    }

    @Test
    void wrapsTheToolbarRatherThanHidingButtons() throws Exception {
        TemplateStore store = store();
        for (int width : new int[] { 1024, 800, 640, 400 }) {
            TemplatesTab tab = new TemplatesTab(store, host());
            tab.setSize(width, 500);
            WrapLayoutTest.layOut(tab);
            JTextField search = find(tab, JTextField.class, field ->
                "Search templates...".equals(placeholder(field))
            ).get();
            assertTrue(WrapLayoutTest.inView(search, tab), "search in view at " + width + " px");
            for (String name : List.of(
                "Edit",
                "Duplicate",
                "Set as default",
                "Delete",
                "Import…",
                "Export…",
                "New template"
            )) {
                assertTrue(WrapLayoutTest.inView(button(tab, name), tab), name + " in view at " + width + " px");
            }
        }
    }

    @Test
    void builtInTemplatesCanOnlyBeDuplicatedOrMadeDefault() throws Exception {
        TemplateStore store = store();
        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            JTable table = find(tab, JTable.class, t -> true).get();
            for (Template builtIn : BuiltInTemplates.all()) {
                select(tab, builtIn);
                Component name = table.prepareRenderer(
                    table.getCellRenderer(table.getSelectedRow(), 1),
                    table.getSelectedRow(),
                    1
                );
                assertEquals(
                    builtIn.name + ", built-in",
                    name.getAccessibleContext().getAccessibleName(),
                    "says so in words, not only with the lock"
                );
                assertFalse(button(tab, "Edit").isEnabled());
                assertFalse(button(tab, "Delete").isEnabled());
                assertTrue(button(tab, "Duplicate").isEnabled());
                String why = "\"" + builtIn.name + "\" is built in, so it can't be edited or deleted.";
                assertTrue(
                    find(tab, JLabel.class, label -> label.getText().startsWith(why)).isPresent(),
                    "says why on screen, not only in tooltips"
                );
            }
            assertTrue(button(tab, "Set as default").isEnabled(), "Empty can be the default");
            assertFalse(button(tab, "Export…").isEnabled(), "nothing of the user's to export");
        });
    }

    @Test
    void selectsSeveralTemplatesOrAllWithCtrlA() throws Exception {
        TemplateStore store = store();
        store.createTemplate("Report", null);
        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            JTable table = find(tab, JTable.class, t -> true).get();
            for (KeyStroke key : List.of(
                KeyStroke.getKeyStroke(KeyEvent.VK_A, java.awt.event.InputEvent.CTRL_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_A, Widgets.menuShortcutMask())
            )) {
                table.clearSelection();
                Object action = table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(key);
                table
                    .getActionMap()
                    .get(action)
                    .actionPerformed(new java.awt.event.ActionEvent(table, 0, "selectAll"));
                assertEquals(store.templates().size(), table.getSelectedRowCount(), key + " selects all");
            }
            assertFalse(button(tab, "Edit").isEnabled(), "one at a time");
            assertFalse(button(tab, "Duplicate").isEnabled());
            assertFalse(button(tab, "Set as default").isEnabled());
            assertTrue(button(tab, "Delete").isEnabled(), "deletes the user's templates, keeps the built-in ones");
            assertTrue(
                find(tab, JLabel.class, label ->
                    label.getText().equals("Built-in templates can't be edited, deleted or exported.")
                ).isPresent()
            );
        });
    }

    @Test
    void exportTicksTheSelectedTemplatesOrAllWhenNoneIs() throws Exception {
        TemplateStore store = store();
        Template report = store.createTemplate("Report", null);
        Template other = store.createTemplate("Other", null);
        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            assertEquals(List.of(report, other), tab.exportPicker().chosen(), "nothing selected: all");
            select(tab, other);
            TemplatePicker picker = tab.exportPicker();
            assertEquals(2, picker.boxes().size(), "lists them all");
            assertEquals(List.of(other), picker.chosen(), "ticks the selected one");
            select(tab, BuiltInTemplates.all().get(0));
            assertEquals(List.of(report, other), tab.exportPicker().chosen(), "built-in ones can't be exported");
        });
    }

    @Test
    void marksBuiltInTemplatesWithALockAndShowsEveryColumnLine() throws Exception {
        TemplateStore store = store();
        store.createTemplate("Report", null);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(new TemplatesTab(store, host()), JTable.class, t -> true).get();
            JLabel builtIn = (JLabel) table.prepareRenderer(table.getCellRenderer(0, 1), 0, 1);
            assertEquals(Widgets.Glyph.LOCK, builtIn.getIcon(), "a lock on the built-in template");
            assertEquals(table.getForeground(), builtIn.getForeground(), "in normal text: it isn't disabled");
            JLabel custom = (JLabel) table.prepareRenderer(table.getCellRenderer(2, 1), 2, 1);
            assertEquals(null, custom.getIcon());
            assertEquals(
                null,
                ((JLabel) table.prepareRenderer(table.getCellRenderer(0, 2), 0, 2)).getIcon(),
                "on the name only"
            );
            // As in Burp's theme: no gap between cells, and a shaded (here, selected) row painted over the lines.
            table.setIntercellSpacing(new java.awt.Dimension(0, 0));
            table.setShowVerticalLines(true);
            table.setGridColor(java.awt.Color.RED);
            table.setRowSelectionInterval(1, 1);
            table.setSize(700, table.getRowHeight() * table.getRowCount());
            table.doLayout();
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                700,
                table.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_RGB
            );
            java.awt.Graphics2D g = image.createGraphics();
            table.paint(g);
            g.dispose();
            int x = table.getColumnModel().getColumn(0).getWidth() - 1;
            int y = table.getRowHeight() + table.getRowHeight() / 2;
            assertEquals(0xff0000, image.getRGB(x, y) & 0xffffff, "the column line shows on the selected row");
        });
    }

    @Test
    void listsAOneMessageTemplateByTheMessageItShowsInsteadOfItsLayout() throws Exception {
        TemplateStore store = store();
        it.unlocksecurity.burp.screenshotmode.model.Settings settings = BuiltInTemplates.defaults();
        settings.messages = it.unlocksecurity.burp.screenshotmode.model.MessageMode.RESPONSE;
        store.createTemplate("Responses", settings);
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(new TemplatesTab(store, host()), JTable.class, t -> true).get();
            assertEquals("Response only", table.getValueAt(2, table.getColumn("Layout").getModelIndex()));
        });
    }

    @Test
    void listsWhatEachTemplateLooksLikeInTheOrderOfTheAppearanceTab() throws Exception {
        TemplateStore store = store();
        it.unlocksecurity.burp.screenshotmode.model.Settings settings = BuiltInTemplates.defaults();
        settings.widthMode = it.unlocksecurity.burp.screenshotmode.model.WidthMode.A4;
        settings.heightMode = it.unlocksecurity.burp.screenshotmode.model.HeightMode.PIXEL;
        settings.heightPixels = 900;
        settings.disposition = Disposition.VERTICAL;
        settings.theme = it.unlocksecurity.burp.screenshotmode.model.ThemeMode.LIGHT;
        settings.view = it.unlocksecurity.burp.screenshotmode.model.ViewMode.PRETTY;
        store.createTemplate("Report", settings);
        it.unlocksecurity.burp.screenshotmode.model.HeaderCatalog catalog =
            it.unlocksecurity.burp.screenshotmode.storage.HeaderCatalogFile.bundled();
        String hidden = settings.headers.hiddenCount(catalog) + " of " + catalog.headerCount();
        SwingUtilities.invokeAndWait(() -> {
            JTable table = find(new TemplatesTab(store, host()), JTable.class, t -> true).get();
            List<String> columns = new java.util.ArrayList<>();
            List<String> report = new java.util.ArrayList<>();
            for (int column = 0; column < table.getColumnCount(); column++) {
                columns.add(table.getColumnName(column));
                report.add(String.valueOf(table.getValueAt(2, column)));
            }
            assertEquals(
                List.of(
                    "Default",
                    "Name",
                    "Based on",
                    "Width",
                    "Height",
                    "Layout",
                    "Theme",
                    "Body",
                    "Hidden headers",
                    "Highlights",
                    "Redactions"
                ),
                columns
            );
            assertEquals(
                List.of(
                    "",
                    "Report",
                    "Defaults",
                    "A4 (595px)",
                    "900px",
                    "Stacked",
                    "Light",
                    "Pretty",
                    hidden,
                    "0",
                    "10"
                ),
                report
            );
            assertEquals("—", table.getValueAt(0, 2), "a built-in template is based on nothing");
            assertEquals(
                "0 of " + catalog.headerCount(),
                String.valueOf(table.getValueAt(1, 8)),
                "Empty hides nothing"
            );
            assertEquals(Integer.class, table.getColumnClass(9), "counts sort as numbers");

            for (int column = 0; column < table.getColumnCount(); column++) {
                int needed = table
                    .getTableHeader()
                    .getDefaultRenderer()
                    .getTableCellRendererComponent(table, table.getColumnName(column), false, false, -1, column)
                    .getPreferredSize()
                    .width;
                for (int row = 0; row < table.getRowCount(); row++) {
                    needed = Math.max(
                        needed,
                        table.prepareRenderer(table.getCellRenderer(row, column), row, column).getPreferredSize().width
                    );
                }
                assertTrue(
                    table.getColumnModel().getColumn(column).getMinWidth() >= needed,
                    table.getColumnName(column) + " is never cut"
                );
            }
            javax.swing.JViewport viewport = (javax.swing.JViewport) table.getParent();
            viewport.setSize(300, 200);
            assertFalse(table.getScrollableTracksViewportWidth(), "too narrow: scrolls sideways");
            viewport.setSize(5000, 200);
            assertTrue(table.getScrollableTracksViewportWidth(), "wide enough: fills the width");
        });
    }

    @Test
    void saysWhyOnlyForBuiltInTemplates() throws Exception {
        TemplateStore store = store();
        Template custom = store.createTemplate("Report", null);
        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            select(tab, BuiltInTemplates.all().get(0));
            JLabel hint = find(tab, JLabel.class, label -> label.getText().contains("is built in")).get();
            int height = hint.getPreferredSize().height;
            select(tab, custom);
            assertEquals(" ", hint.getText());
            assertEquals(height, hint.getPreferredSize().height, "keeps its line, so the table doesn't move");
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

    @Test
    void undoesInTheEditorWithTheMenuShortcut() throws Exception {
        TemplateStore store = store();
        Template template = store.createTemplate("Report", BuiltInTemplates.defaults());
        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            select(tab, template);
            button(tab, "Edit").doClick();
            SettingsForm form = find(tab, SettingsForm.class, f -> true).get();
            JLabel unsaved = find(tab, JLabel.class, label -> "Unsaved".equals(label.getText())).get();
            int count = template.settings.redactions.size();

            find(form.redactionList(), RuleList.Row.class, row -> true)
                .get()
                .delete();
            assertTrue(unsaved.isVisible());

            KeyStroke undo = KeyStroke.getKeyStroke(KeyEvent.VK_Z, Widgets.menuShortcutMask());
            Object action = form.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).get(undo);
            form.getActionMap().get(action).actionPerformed(null);
            assertFalse(unsaved.isVisible(), "back to the saved settings");
            button(tab, "Save").doClick();
            assertEquals(count, store.getTemplateById(template.id).get().settings.redactions.size());
        });
    }

    @Test
    void saysWhenTheNameIsTakenAndWontSave() throws Exception {
        TemplateStore store = store();
        Template report = store.createTemplate("Report", null);
        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            button(tab, "New template").doClick();
            JTextField name = find(tab, JTextField.class, field -> "Template name".equals(placeholder(field))).get();
            AbstractButton save = button(tab, "Save");
            assertFalse(save.isEnabled(), "no name yet");

            name.setText(" defaults ");
            JLabel error = find(tab, JLabel.class, label -> label.getText().contains("already exists")).get();
            assertEquals("A template named \"Defaults\" already exists", error.getText());
            assertTrue(error.isVisible());
            assertEquals("error", name.getClientProperty("JComponent.outline"));
            assertFalse(save.isEnabled());

            name.setText("Mine");
            assertFalse(error.isVisible());
            assertTrue(save.isEnabled());

            // Its own name is not taken.
            tab.setDiscardConfirmation(message -> true);
            button(tab, "Close").doClick();
            select(tab, report);
            button(tab, "Edit").doClick();
            name = find(tab, JTextField.class, field -> "Template name".equals(placeholder(field))).get();
            assertFalse(button(tab, "Save").isEnabled(), "nothing changed yet");
            name.setText("REPORT");
            assertTrue(button(tab, "Save").isEnabled());
        });
    }

    @Test
    void saysWhichImportedTemplatesWereRenamed() {
        assertEquals("Imported 2 templates", TemplatesTab.importStatus(new TemplateStore.Imported(2, List.of())));
        assertEquals(
            "Imported 2 templates, 1 renamed: \"Report\" is now \"Report (imported)\"",
            TemplatesTab.importStatus(
                new TemplateStore.Imported(2, List.of(new TemplateStore.Rename("Report", "Report (imported)")))
            )
        );
        assertEquals(
            "Imported 3 templates, 2 renamed with \"(imported)\" because their names were taken",
            TemplatesTab.importStatus(
                new TemplateStore.Imported(
                    3,
                    List.of(
                        new TemplateStore.Rename("A", "A (imported)"),
                        new TemplateStore.Rename("B", "B (imported)")
                    )
                )
            )
        );
    }

    @Test
    void notesTheNameEachImportedTemplateIsAddedAs() {
        Template report = Template.create("Report", BuiltInTemplates.defaults());
        Template other = Template.create("Other", BuiltInTemplates.defaults());
        assertEquals(
            java.util.Arrays.asList("Name taken: added as \"Report (imported)\"", null),
            TemplatesTab.importNotes(List.of(report, other), List.of("Report (imported)", "Other"))
        );
    }

    @Test
    void saysWhenTheSearchMatchesNoTemplate() throws Exception {
        TemplateStore store = store();
        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            JTextField search = find(tab, JTextField.class, field ->
                "Search templates...".equals(placeholder(field))
            ).get();
            assertTrue(
                find(tab, JLabel.class, label -> label.isVisible() && label.getText().startsWith("No ")).isEmpty()
            );
            search.setText("zzz");
            JLabel noMatch = find(tab, JLabel.class, label -> label.getText().startsWith("No templates match")).get();
            assertTrue(noMatch.isVisible());
            assertEquals("No templates match \"zzz\"", noMatch.getText());
            search.setText("def");
            assertFalse(noMatch.isVisible());
        });
    }

    @Test
    void searchIsAStandardSearchField() throws Exception {
        TemplateStore store = store();
        store.createTemplate("Report", null);
        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            JTextField search = find(tab, JTextField.class, field ->
                "Search templates...".equals(placeholder(field))
            ).get();
            assertEquals(Widgets.Glyph.SEARCH, search.getClientProperty("JTextField.leadingIcon"));
            assertEquals(true, search.getClientProperty("JTextField.showClearButton"));
            assertEquals("Search templates", search.getAccessibleContext().getAccessibleName());

            JTable table = find(tab, JTable.class, t -> true).get();
            search.setText("rep");
            assertEquals(1, table.getRowCount());
            Object clear = search.getInputMap(JComponent.WHEN_FOCUSED).get(KeyStroke.getKeyStroke("ESCAPE"));
            search.getActionMap().get(clear).actionPerformed(null);
            assertEquals("", search.getText(), "Escape clears it");
            assertEquals(store.templates().size(), table.getRowCount());
        });
    }

    @Test
    void previewsTheSampleMessageInTheEditor() throws Exception {
        TemplateStore store = store();
        SwingUtilities.invokeAndWait(() -> {
            TemplatesTab tab = new TemplatesTab(store, host());
            button(tab, "New template").doClick();
            SettingsForm form = find(tab, SettingsForm.class, f -> true).get();
            TemplatePreview preview = find(tab, TemplatePreview.class, p -> true).get();

            // Each rule shows what it matched in the sample, as in the screenshot window.
            RuleList<?>.Row row = find(form.redactionList(), RuleList.Row.class, r -> true).get();
            assertTrue(row.pill().isVisible());
            assertEquals(RuleList.Pill.Style.FILLED, row.pill().style());

            form.tabs().setSelectedIndex(SettingsForm.APPEARANCE);
            assertFalse(isShowing(preview.parts(), tab), "the whole screenshot");
            form.tabs().setSelectedIndex(SettingsForm.HEADERS);
            assertTrue(isShowing(preview.parts(), tab));
        });
    }

    private static Object placeholder(JTextField field) {
        return field.getClientProperty("JTextField.placeholderText");
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
