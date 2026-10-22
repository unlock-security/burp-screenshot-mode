package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.Template;
import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import it.unlocksecurity.burp.screenshotmode.storage.TemplateCodec;
import it.unlocksecurity.burp.screenshotmode.storage.TemplateStore;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

/** Suite tab managing screenshot templates (port of the Caido plugin's Settings page). */
public final class TemplatesTab extends JPanel {

    private static final String LIST = "list";
    private static final String EDITOR = "editor";
    private static final Color SUCCESS = new Color(0x3fa45b);

    private final TemplateStore store;
    private final Host host;
    private final CardLayout cards = new CardLayout();
    private final JPanel editorHolder = new JPanel(new BorderLayout());
    private final TemplateTableModel model = new TemplateTableModel();
    private final JTable table = new JTable(model);
    private final TableRowSorter<TemplateTableModel> sorter = new TableRowSorter<>(model);
    private final JLabel status = new JLabel(" ");
    private final Timer statusTimer = new Timer(4000, event -> status.setText(" "));
    private final JButton edit = Widgets.button("Edit");
    private final JButton duplicate = Widgets.button("Duplicate");
    private final JButton setDefault = Widgets.button("Set as default");
    private final JButton delete = Widgets.button("Delete");
    private final JButton exportButton = Widgets.button("Export…");
    /** The editor's "Based on" choice, while a new template is being edited. */
    private JComboBox<Template> basedOn;
    private Predicate<String> confirmDiscard = this::askDiscard;

    public TemplatesTab(TemplateStore store, Host host) {
        this.store = store;
        this.host = host;
        setLayout(cards);
        statusTimer.setRepeats(false);
        add(buildList(), LIST);
        add(editorHolder, EDITOR);
        store.addListener(() -> SwingUtilities.invokeLater(this::refresh));
        refresh();
    }

    private JComponent buildList() {
        // The suite tab is already named "Screenshot Mode": no title, just how to take a screenshot.
        JLabel header = Widgets.hint(
            "Templates for request/response screenshots. Right-click a request anywhere in Burp and choose " +
                "Extensions › Screenshot Mode › Screenshot, or press Ctrl+Shift+S."
        );

        JTextField search = new JTextField(24);
        Widgets.placeholder(search, "Search templates...");
        Widgets.onTextChange(search, query ->
            sorter.setRowFilter(
                query.isBlank()
                    ? null
                    : RowFilter.regexFilter("(?i)" + Pattern.quote(query.trim()), TemplateTableModel.NAME)
            )
        );

        JButton create = Widgets.primary(Widgets.button("New template"));
        create.setToolTipText("Create a template starting from a built-in one");
        create.addActionListener(event ->
            openEditor(store.getTemplateById(BuiltInTemplates.DEFAULTS_ID).orElseThrow(), Mode.NEW, "")
        );
        edit.addActionListener(event -> selected().ifPresent(this::editOrExplain));
        duplicate.setToolTipText("Create a new template starting from the selected one");
        duplicate.addActionListener(event ->
            selected().ifPresent(template -> openEditor(template, Mode.DUPLICATE, null))
        );
        setDefault.addActionListener(event -> selected().ifPresent(this::makeDefault));
        delete.addActionListener(event -> selected().ifPresent(this::delete));
        JButton importButton = Widgets.button("Import…");
        importButton.setToolTipText("Add templates from a JSON export");
        importButton.addActionListener(event -> importTemplates());
        exportButton.addActionListener(event -> exportTemplates());
        exportButton.setToolTipText("Save your templates to a JSON file (built-in ones are not included)");

        // Actions on the selected template together, Delete set apart from them, file actions and the primary
        // action on the right.
        JPanel selection = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        selection.add(search);
        selection.add(Widgets.separator());
        selection.add(edit);
        selection.add(duplicate);
        selection.add(setDefault);
        selection.add(Widgets.separator());
        selection.add(delete);
        JPanel global = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        global.add(importButton);
        global.add(exportButton);
        global.add(create);
        JPanel toolbar = new JPanel(new BorderLayout(12, 0));
        toolbar.add(selection, BorderLayout.CENTER);
        toolbar.add(global, BorderLayout.EAST);

        table.setRowSorter(sorter);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getSelectionModel().addListSelectionListener(event -> updateButtons());
        table.getColumnModel().getColumn(TemplateTableModel.DEFAULT).setMaxWidth(70);
        // Short values and counts are centred; the name is left-aligned, as free text reads best that way. Each
        // header is aligned like its values.
        for (int column = 0; column < model.getColumnCount(); column++) {
            int alignment = column == TemplateTableModel.NAME ? SwingConstants.LEADING : SwingConstants.CENTER;
            DefaultTableCellRenderer cells = new DefaultTableCellRenderer();
            cells.setHorizontalAlignment(alignment);
            TableColumn tableColumn = table.getColumnModel().getColumn(column);
            tableColumn.setCellRenderer(cells);
            tableColumn.setHeaderRenderer(alignedHeader(alignment));
        }
        table.addMouseListener(
            new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) >= 0) {
                        selected().ifPresent(TemplatesTab.this::editOrExplain);
                    }
                }
            }
        );

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.add(header, BorderLayout.NORTH);
        top.add(toolbar, BorderLayout.CENTER);
        store.unreadableTemplates().ifPresent(json -> {
            JPanel intro = new JPanel(new BorderLayout(0, 10));
            intro.add(unreadableNotice(json), BorderLayout.NORTH);
            intro.add(header, BorderLayout.CENTER);
            top.add(intro, BorderLayout.NORTH);
        });

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    /** What the editor does with the template it is opened on. */
    private enum Mode {
        /** Creates a template starting from a built-in one, which can be changed in "Based on". */
        NEW,
        EDIT,
        /** Creates a template starting from any template's settings. */
        DUPLICATE,
    }

    /**
     * @param template  the template to edit or duplicate, or the built-in one a new template starts from
     * @param typedName for a new template, the name already typed before switching built-in template
     */
    private JComponent buildEditor(Template template, Mode mode, String typedName) {
        boolean editing = mode == Mode.EDIT;
        Settings original = template.settings.copy();
        Settings working = original.copy();
        String originalName = switch (mode) {
            case NEW -> "";
            case EDIT -> template.name;
            case DUPLICATE -> copyName(template.name);
        };

        JTextField name = new JTextField(mode == Mode.NEW ? typedName : originalName, 30);
        Widgets.placeholder(name, "Template name");
        JLabel unsaved = Widgets.chip("Unsaved");
        unsaved.setToolTipText("This template has changes that are not saved yet");
        JButton save = Widgets.primary(Widgets.button("Save"));
        BooleanSupplier dirty = () ->
            !name.getText().trim().equals(originalName) || !TemplateCodec.sameSettings(original, working);
        Runnable updateState = () -> {
            save.setEnabled(!name.getText().isBlank());
            unsaved.setVisible(dirty.getAsBoolean());
        };
        updateState.run();
        Widgets.onTextChange(name, text -> updateState.run());
        save.addActionListener(event -> {
            String trimmed = name.getText().trim();
            if (editing) {
                store.updateTemplate(template.id, trimmed, working);
                showStatus("Template updated", false);
            } else {
                store.createTemplate(trimmed, working);
                showStatus("Template created", false);
            }
            showList();
        });
        JButton cancel = Widgets.button("Cancel");
        String displayName = originalName.isBlank() ? "this template" : "\"" + originalName + "\"";
        cancel.addActionListener(event -> {
            if (
                !dirty.getAsBoolean() || confirmDiscard.test("Your changes to " + displayName + " haven't been saved.")
            ) {
                showList();
            }
        });

        JLabel title = new JLabel(
            switch (mode) {
                case NEW -> "New template";
                case EDIT -> "Edit \"" + template.name + "\"";
                case DUPLICATE -> "Duplicate \"" + template.name + "\"";
            }
        );
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 3));
        JPanel heading = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        heading.add(Widgets.hint("Templates ›"));
        heading.add(title);
        heading.add(unsaved);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.add(cancel);
        actions.add(save);
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.add(heading, BorderLayout.CENTER);
        bar.add(actions, BorderLayout.EAST);
        bar.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Widgets.borderColor()),
                BorderFactory.createEmptyBorder(0, 0, 12, 0)
            )
        );

        JLabel nameLabel = Widgets.title("Name");
        nameLabel.setLabelFor(name);
        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        nameRow.setBorder(BorderFactory.createEmptyBorder(14, 0, 10, 0));
        nameRow.add(nameLabel);
        nameRow.add(name);
        basedOn = null;
        if (mode == Mode.NEW) {
            JComboBox<Template> base = new JComboBox<>(
                store
                    .templates()
                    .stream()
                    .filter(candidate -> candidate.builtIn)
                    .toArray(Template[]::new)
            );
            base.setSelectedItem(template);
            base.setToolTipText("The built-in template whose settings the new template starts from");
            base.addActionListener(event -> {
                if (!(base.getSelectedItem() instanceof Template chosen) || chosen == template) {
                    return;
                }
                if (
                    TemplateCodec.sameSettings(original, working) ||
                    confirmDiscard.test(
                        "Starting from \"" + chosen.name + "\" discards the changes you made to the settings."
                    )
                ) {
                    openEditor(chosen, Mode.NEW, name.getText());
                    basedOn.requestFocusInWindow();
                } else {
                    base.setSelectedItem(template);
                }
            });
            JLabel baseLabel = Widgets.title("Based on");
            baseLabel.setLabelFor(base);
            baseLabel.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
            nameRow.add(baseLabel);
            nameRow.add(base);
            basedOn = base;
        }

        JPanel body = new JPanel(new BorderLayout());
        body.add(nameRow, BorderLayout.NORTH);
        body.add(new SettingsForm(working, updateState, host.editorFont().get(), true), BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 0, 16));
        panel.add(bar, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    /** Asks before discarding edits, explained by {@code message}. Keep editing is the default. */
    private boolean askDiscard(String message) {
        return confirm("Discard changes?", message, "Discard", "Keep editing");
    }

    /**
     * Asks a two-option question whose safe choice is the primary, default one.
     *
     * @return whether the user chose {@code action}
     */
    private boolean confirm(String title, String message, String action, String safe) {
        JButton act = Widgets.button(action);
        JButton keep = Widgets.primary(Widgets.button(safe));
        JOptionPane pane = new JOptionPane(
            message,
            JOptionPane.WARNING_MESSAGE,
            JOptionPane.DEFAULT_OPTION,
            null,
            new Object[] { act, keep },
            keep
        );
        // Custom option buttons don't close the pane on their own.
        act.addActionListener(event -> pane.setValue(act));
        keep.addActionListener(event -> pane.setValue(keep));
        JDialog dialog = pane.createDialog(this, title);
        dialog.getRootPane().setDefaultButton(keep);
        dialog.setVisible(true);
        dialog.dispose();
        return pane.getValue() == act;
    }

    /** Replaces the "discard unsaved changes?" dialog (given its message), for tests. */
    void setDiscardConfirmation(Predicate<String> confirmation) {
        this.confirmDiscard = confirmation;
    }

    /** First free "name (copy)", "name (copy 2)", ... among the existing template names. */
    private String copyName(String name) {
        List<String> names = store
            .templates()
            .stream()
            .map(template -> template.name)
            .toList();
        String candidate = name + " (copy)";
        for (int i = 2; names.contains(candidate); i++) {
            candidate = name + " (copy " + i + ")";
        }
        return candidate;
    }

    /**
     * Wraps the header's current default renderer, looked up on every call so it stays the look and feel's (and
     * follows Burp's theme switches), only changing the alignment of its label.
     */
    private TableCellRenderer alignedHeader(int alignment) {
        return (table, value, isSelected, hasFocus, row, column) -> {
            Component component = this.table
                .getTableHeader()
                .getDefaultRenderer()
                .getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (component instanceof JLabel label) {
                label.setHorizontalAlignment(alignment);
            }
            return component;
        };
    }

    /** Built-in templates can't be edited: says so instead of opening them. */
    private void editOrExplain(Template template) {
        if (template.builtIn) {
            showInfo("\"" + template.name + "\" is built in and can't be edited. Duplicate it to make your own.");
        } else {
            openEditor(template, Mode.EDIT, null);
        }
    }

    private void openEditor(Template template, Mode mode, String typedName) {
        editorHolder.removeAll();
        editorHolder.add(buildEditor(template, mode, typedName), BorderLayout.CENTER);
        editorHolder.revalidate();
        cards.show(this, EDITOR);
    }

    private void showList() {
        cards.show(this, LIST);
        editorHolder.removeAll();
    }

    private void refresh() {
        String selectedId = selected()
            .map(template -> template.id)
            .orElse(null);
        model.setTemplates(store.templates(), store.defaultTemplateId());
        exportButton.setEnabled(customTemplates() > 0);
        if (selectedId != null) {
            for (int row = 0; row < model.getRowCount(); row++) {
                if (model.template(row).id.equals(selectedId)) {
                    int viewRow = table.convertRowIndexToView(row);
                    if (viewRow >= 0) {
                        table.setRowSelectionInterval(viewRow, viewRow);
                    }
                }
            }
        }
        updateButtons();
    }

    private Optional<Template> selected() {
        int row = table.getSelectedRow();
        return row < 0 ? Optional.empty() : Optional.of(model.template(table.convertRowIndexToModel(row)));
    }

    private void updateButtons() {
        Optional<Template> selected = selected();
        boolean isDefault = selected.map(template -> template.id.equals(store.defaultTemplateId())).orElse(false);
        boolean builtIn = selected.map(template -> template.builtIn).orElse(false);
        String builtInReason = builtIn ? "Built-in templates can't be changed: duplicate it to make your own" : null;
        edit.setEnabled(selected.isPresent() && !builtIn);
        edit.setToolTipText(builtInReason);
        duplicate.setEnabled(selected.isPresent());
        setDefault.setEnabled(selected.isPresent() && !isDefault);
        delete.setEnabled(selected.isPresent() && !builtIn);
        delete.setToolTipText(builtInReason);
    }

    private long customTemplates() {
        return store
            .templates()
            .stream()
            .filter(template -> !template.builtIn)
            .count();
    }

    private void makeDefault(Template template) {
        if (store.setDefaultTemplate(template.id)) {
            showStatus("\"" + template.name + "\" is now the default template", false);
        }
    }

    private void delete(Template template) {
        String message = "Are you sure you want to delete \"" + template.name + "\"?";
        if (template.id.equals(store.defaultTemplateId())) {
            Template defaults = store.getTemplateById(BuiltInTemplates.DEFAULTS_ID).orElseThrow();
            message += "\nIt is the default template: \"" + defaults.name + "\" will become the default.";
        }
        if (confirm("Delete Template", message, "Delete", "Cancel") && store.deleteTemplate(template.id)) {
            showStatus("Template deleted", false);
        }
    }

    private void importTemplates() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Templates");
        chooser.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            int count = store.importTemplates(
                Files.readString(chooser.getSelectedFile().toPath(), StandardCharsets.UTF_8)
            );
            showStatus("Imported " + count + " template" + (count == 1 ? "" : "s"), false);
        } catch (IOException | IllegalArgumentException e) {
            JOptionPane.showMessageDialog(
                this,
                "Could not import templates:\n" + e.getMessage(),
                "Import Templates",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void exportTemplates() {
        File file = writeJson("Export Templates", "screenshot-mode-templates.json", store.exportJson());
        if (file != null) {
            long count = customTemplates();
            showStatus("Exported " + count + " template" + (count == 1 ? "" : "s") + " to " + file.getName(), false);
        }
    }

    /**
     * Shown when the saved templates could not be read at startup: only the built-in templates are left, and the
     * original data can be saved to a file to recover it.
     */
    private JComponent unreadableNotice(String json) {
        JTextArea text = Widgets.wrappingHint(
            "Your saved templates could not be read, so Screenshot Mode started with the built-in templates only. " +
                "A copy of the original data was kept.",
            900
        );
        text.setForeground(Widgets.errorColor());
        JButton export = Widgets.button("Export copy…");
        export.setToolTipText("Save the templates that could not be read to a file");
        export.addActionListener(event -> {
            File file = writeJson("Export Copy", "screenshot-mode-templates-unreadable.json", json);
            if (file != null) {
                showStatus("Saved the copy to " + file.getName(), false);
            }
        });
        JPanel notice = new JPanel(new BorderLayout(10, 0));
        notice.add(text, BorderLayout.CENTER);
        JPanel holder = new JPanel(new BorderLayout());
        holder.add(export, BorderLayout.NORTH);
        notice.add(holder, BorderLayout.EAST);
        return notice;
    }

    /** Asks where to save {@code json} and writes it; {@code null} if cancelled or failed (after saying so). */
    private File writeJson(String title, String defaultName, String json) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));
        chooser.setSelectedFile(new File(defaultName));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".json")) {
            file = new File(file.getParentFile(), file.getName() + ".json");
        }
        try {
            Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
            return file;
        } catch (IOException e) {
            host.logError().accept(title + " failed: " + e);
            JOptionPane.showMessageDialog(
                this,
                "Could not save the file:\n" + e.getMessage(),
                title,
                JOptionPane.ERROR_MESSAGE
            );
            return null;
        }
    }

    private void showStatus(String message, boolean error) {
        status.setForeground(error ? Widgets.errorColor() : SUCCESS);
        status.setText(message);
        statusTimer.restart();
    }

    /** A status that is neither a success nor an error. */
    private void showInfo(String message) {
        status.setForeground(UIManager.getColor("Label.foreground"));
        status.setText(message);
        statusTimer.restart();
    }

    private static final class TemplateTableModel extends AbstractTableModel {

        static final int DEFAULT = 0;
        static final int NAME = 1;
        static final int TYPE = 2;
        static final int LAYOUT = 3;
        static final int WIDTH = 4;
        static final int HIGHLIGHTS = 5;
        static final int REDACTIONS = 6;
        private static final String[] COLUMNS = {
            "Default",
            "Name",
            "Type",
            "Layout",
            "Width",
            "Highlights",
            "Redactions",
        };

        private List<Template> templates = List.of();
        private String defaultId = "";

        void setTemplates(List<Template> templates, String defaultId) {
            this.templates = templates;
            this.defaultId = defaultId;
            fireTableDataChanged();
        }

        Template template(int row) {
            return templates.get(row);
        }

        @Override
        public int getRowCount() {
            return templates.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            Template template = templates.get(row);
            return switch (column) {
                case DEFAULT -> template.id.equals(defaultId) ? "★" : "";
                case NAME -> template.name;
                case TYPE -> template.builtIn ? "Built-in" : "Custom";
                case LAYOUT -> template.settings.disposition.label();
                case WIDTH -> template.settings.widthMode == WidthMode.PIXEL
                    ? template.settings.widthPixels + "px"
                    : template.settings.widthMode.label();
                case HIGHLIGHTS -> template.settings.highlights.size();
                case REDACTIONS -> template.settings.redactions.size();
                default -> "";
            };
        }
    }
}
