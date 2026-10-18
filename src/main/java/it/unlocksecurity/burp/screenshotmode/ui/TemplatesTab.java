package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.Template;
import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
import it.unlocksecurity.burp.screenshotmode.storage.TemplateStore;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
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
    private final JButton setDefault = Widgets.button("Set as Default");
    private final JButton delete = Widgets.button("Delete");

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
        JLabel title = new JLabel("Screenshot Mode");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 4));
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.add(title, BorderLayout.NORTH);
        header.add(
            Widgets.hint(
                "Templates for pretty request/response screenshots. Right-click a request anywhere in Burp " +
                    "and choose Extensions › Screenshot Mode › Screenshot, or press Ctrl+Shift+S."
            ),
            BorderLayout.CENTER
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

        JButton create = Widgets.button("Create Template");
        create.addActionListener(event -> openEditor(null, false));
        edit.addActionListener(event -> selected().ifPresent(template -> openEditor(template, false)));
        duplicate.setToolTipText("Create a new template starting from the selected one");
        duplicate.addActionListener(event -> selected().ifPresent(template -> openEditor(template, true)));
        setDefault.addActionListener(event -> selected().ifPresent(this::makeDefault));
        delete.addActionListener(event -> selected().ifPresent(this::delete));
        JButton importButton = Widgets.button("Import…");
        importButton.setToolTipText("Add templates from a JSON export (this extension's or the Caido plugin's format)");
        importButton.addActionListener(event -> importTemplates());
        JButton exportButton = Widgets.button("Export…");
        exportButton.addActionListener(event -> exportTemplates());

        JPanel toolbar = Widgets.row(search, create, edit, duplicate, setDefault, delete, importButton, exportButton);

        table.setRowSorter(sorter);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getSelectionModel().addListSelectionListener(event -> updateButtons());
        table.getColumnModel().getColumn(TemplateTableModel.DEFAULT).setMaxWidth(70);
        DefaultTableCellRenderer centered = new DefaultTableCellRenderer();
        centered.setHorizontalAlignment(SwingConstants.CENTER);
        for (int column : new int[] {
            TemplateTableModel.DEFAULT,
            TemplateTableModel.HIGHLIGHTS,
            TemplateTableModel.REDACTIONS,
        }) {
            table.getColumnModel().getColumn(column).setCellRenderer(centered);
        }
        table.addMouseListener(
            new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && table.rowAtPoint(e.getPoint()) >= 0) {
                        selected().ifPresent(template -> openEditor(template, false));
                    }
                }
            }
        );

        JPanel top = new JPanel(new BorderLayout(0, 10));
        top.add(header, BorderLayout.NORTH);
        top.add(toolbar, BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 12, 16));
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * @param template  the template to edit or duplicate, {@code null} to create one from the default settings
     * @param duplicate create a new template starting from {@code template}'s settings (not in the Caido plugin)
     */
    private JComponent buildEditor(Template template, boolean duplicate) {
        boolean editing = template != null && !duplicate;
        Settings working = template != null ? template.settings.copy() : Settings.defaults();

        JTextField name = new JTextField(editing ? template.name : duplicate ? copyName(template.name) : "", 30);
        Widgets.placeholder(name, "Enter template name");
        JButton save = Widgets.button("Save");
        save.setEnabled(!name.getText().isBlank());
        Widgets.onTextChange(name, text -> save.setEnabled(!text.isBlank()));
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
        JButton back = Widgets.button("Back");
        back.addActionListener(event -> showList());

        JLabel title = new JLabel(
            editing ? "Edit Template" : duplicate ? "Duplicate \"" + template.name + "\"" : "Create Template"
        );
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 3));
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.add(Widgets.row(back, title), BorderLayout.CENTER);
        bar.add(save, BorderLayout.EAST);
        bar.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Widgets.borderColor()),
                BorderFactory.createEmptyBorder(0, 0, 12, 0)
            )
        );

        Widgets.Stack body = new Widgets.Stack(20);
        body.setBorder(BorderFactory.createEmptyBorder(16, 0, 16, 0));
        body.addItem(Widgets.section("Template Name", null, null, Widgets.row(name)));
        body.addItem(new SettingsForm(working, () -> {}, host.editorFont().get(), true));
        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 0, 16));
        panel.add(bar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
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

    private void openEditor(Template template, boolean duplicate) {
        editorHolder.removeAll();
        editorHolder.add(buildEditor(template, duplicate), BorderLayout.CENTER);
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
        edit.setEnabled(selected.isPresent());
        duplicate.setEnabled(selected.isPresent());
        setDefault.setEnabled(selected.isPresent() && !isDefault);
        delete.setEnabled(selected.isPresent() && !isDefault);
        delete.setToolTipText(isDefault ? "The default template cannot be deleted" : null);
    }

    private void makeDefault(Template template) {
        if (store.setDefaultTemplate(template.id)) {
            showStatus("\"" + template.name + "\" is now the default template", false);
        }
    }

    private void delete(Template template) {
        if (template.id.equals(store.defaultTemplateId())) {
            showStatus("Cannot delete the default template", true);
            return;
        }
        int answer = JOptionPane.showConfirmDialog(
            this,
            "Are you sure you want to delete \"" + template.name + "\"?",
            "Delete Template",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (answer == JOptionPane.YES_OPTION && store.deleteTemplate(template.id)) {
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
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Templates");
        chooser.setFileFilter(new FileNameExtensionFilter("JSON files", "json"));
        chooser.setSelectedFile(new File("screenshot-mode-templates.json"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase().endsWith(".json")) {
            file = new File(file.getParentFile(), file.getName() + ".json");
        }
        try {
            Files.writeString(file.toPath(), store.exportJson(), StandardCharsets.UTF_8);
            showStatus("Exported " + store.templates().size() + " templates to " + file.getName(), false);
        } catch (IOException e) {
            host.logError().accept("Template export failed: " + e);
            JOptionPane.showMessageDialog(
                this,
                "Could not export templates:\n" + e.getMessage(),
                "Export Templates",
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private void showStatus(String message, boolean error) {
        status.setForeground(error ? Widgets.errorColor() : SUCCESS);
        status.setText(message);
        statusTimer.restart();
    }

    private static final class TemplateTableModel extends AbstractTableModel {

        static final int DEFAULT = 0;
        static final int NAME = 1;
        static final int LAYOUT = 2;
        static final int WIDTH = 3;
        static final int HIGHLIGHTS = 4;
        static final int REDACTIONS = 5;
        private static final String[] COLUMNS = { "Default", "Name", "Layout", "Width", "Highlights", "Redactions" };

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
