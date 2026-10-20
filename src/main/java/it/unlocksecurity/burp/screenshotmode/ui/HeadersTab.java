package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.Direction;
import it.unlocksecurity.burp.screenshotmode.model.HeaderCatalog;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.Visibility;
import it.unlocksecurity.burp.screenshotmode.render.HeaderFilter;
import it.unlocksecurity.burp.screenshotmode.render.MessageData;
import it.unlocksecurity.burp.screenshotmode.storage.DefaultTemplate;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;

/**
 * The Headers tab: the headers of the catalog in groups, each with a Show checkbox, as a table whose group rows
 * expand and collapse. With a message on screen, it lists only that message's headers by default. Headers the catalog
 * doesn't know are listed first, as "Custom headers": they are the ones nobody has decided on yet.
 */
final class HeadersTab extends JPanel {

    /** The header names of the message on screen. */
    record InMessage(List<String> request, List<String> response) {
        static InMessage of(MessageData data) {
            return new InMessage(names(data.requestText()), names(data.responseText()));
        }

        private static List<String> names(String text) {
            return text == null ? List.of() : HeaderFilter.names(text);
        }

        /** The messages a header appears in, or {@code null} if in neither. */
        Direction where(String name) {
            boolean inRequest = request.stream().anyMatch(name::equalsIgnoreCase);
            boolean inResponse = response.stream().anyMatch(name::equalsIgnoreCase);
            return inRequest && inResponse
                ? Direction.BOTH
                : inRequest
                  ? Direction.REQUEST
                  : inResponse
                    ? Direction.RESPONSE
                    : null;
        }
    }

    enum View {
        MESSAGE("In this message"),
        ALL("All headers");

        private final String label;

        View(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /** Whether a header shows in the messages it is listed for: a group is mixed when its headers differ. */
    enum State {
        SHOWN,
        HIDDEN,
        MIXED,
    }

    /** The id of the group of headers the catalog doesn't know. */
    private static final String CUSTOM = "";

    static final class GroupRow {

        final String id;
        final String label;
        final List<HeaderRow> headers = new ArrayList<>();

        GroupRow(String id, String label) {
            this.id = id;
            this.label = label;
        }

        boolean isCustom() {
            return id.equals(CUSTOM);
        }
    }

    /** @param sides the messages the row is about: where the header appears, or where the catalog says it is used */
    record HeaderRow(String name, HeaderCatalog.Header entry, GroupRow group, Direction sides) {}

    private final Settings settings;
    private final HeaderCatalog catalog;
    private final InMessage message;
    private final Runnable onChange;
    private final JTextField filter = new JTextField();
    private final Model model = new Model();
    private final JTable table = new Table(model);
    private final JPanel details = Widgets.card(new BorderLayout());
    private final JLabel summary = Widgets.hint("");
    private final Set<String> expanded = new HashSet<>(Set.of(CUSTOM));
    /** In the screenshot window only. */
    private final SegmentedControl<View> views;
    private View view;

    /** @param message the headers of the message on screen, or {@code null} in the template editor */
    HeadersTab(Settings settings, HeaderCatalog catalog, InMessage message, Runnable onChange) {
        super(new BorderLayout(0, 10));
        this.settings = settings;
        this.catalog = catalog;
        this.message = message;
        this.onChange = onChange;
        this.view = message != null ? View.MESSAGE : View.ALL;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        Widgets.placeholder(filter, "Filter headers");
        filter.getAccessibleContext().setAccessibleName("Filter headers");
        Widgets.onTextChange(filter, text -> refresh());

        JButton add = Widgets.iconButton(Widgets.Glyph.PLUS, "Hide a header by name", this::addHeader);
        JPanel search = new JPanel(new BorderLayout(8, 0));
        search.setOpaque(false);
        search.add(filter, BorderLayout.CENTER);
        search.add(add, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(search, BorderLayout.NORTH);
        views =
            message == null
                ? null
                : new SegmentedControl<>(View.values(), view, value -> {
                      view = value;
                      refresh();
                  });
        if (views != null) {
            JPanel bar = new JPanel(new BorderLayout());
            bar.setOpaque(false);
            bar.add(views, BorderLayout.WEST);
            top.add(bar, BorderLayout.SOUTH);
        }

        setUpTable();
        JScrollPane scroll = new JScrollPane(table);

        JButton restore = Widgets.button("Restore defaults");
        restore.putClientProperty("JButton.buttonType", "borderless");
        restore.setToolTipText("Hide and show the same headers as a new template");
        restore.addActionListener(event -> {
            settings.headers = DefaultTemplate.settings().headers;
            changed();
        });
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setOpaque(false);
        footer.add(summary, BorderLayout.CENTER);
        footer.add(restore, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);
        bottom.add(details, BorderLayout.CENTER);
        bottom.add(footer, BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        refresh();
    }

    /** How many headers are hidden: in the message on screen, or in the template when there is none. */
    int hiddenCount() {
        if (message != null) {
            int count = 0;
            for (String name : message.request()) {
                count += settings.headers.hides(name, RuleTarget.REQUEST, catalog) ? 1 : 0;
            }
            for (String name : message.response()) {
                count += settings.headers.hides(name, RuleTarget.RESPONSE, catalog) ? 1 : 0;
            }
            return count;
        }
        int count = 0;
        for (HeaderCatalog.Group group : catalog.groups()) {
            for (HeaderCatalog.Header header : group.headers()) {
                count += settings.headers.of(header.name(), catalog) != Visibility.SHOW ? 1 : 0;
            }
        }
        for (String name : customHeaders()) {
            count += settings.headers.of(name, catalog) != Visibility.SHOW ? 1 : 0;
        }
        return count;
    }

    JTable table() {
        return table;
    }

    /** The rows as listed: groups, each followed by its headers when expanded. */
    List<Object> rows() {
        return model.rows;
    }

    JTextField filter() {
        return filter;
    }

    /** Selects the row of a header or, by its label, a group; for tests. */
    void select(String name) {
        for (int i = 0; i < model.rows.size(); i++) {
            Object row = model.rows.get(i);
            if (
                (row instanceof HeaderRow header && header.name().equalsIgnoreCase(name)) ||
                (row instanceof GroupRow group && group.label.equals(name))
            ) {
                table.setRowSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                return;
            }
        }
        throw new IllegalArgumentException("Not listed: " + name);
    }

    JPanel details() {
        return details;
    }

    State state(HeaderRow row) {
        Visibility visibility = settings.headers.of(row.name(), catalog);
        boolean request = row.sides() != Direction.RESPONSE;
        boolean response = row.sides() != Direction.REQUEST;
        int sides = (request ? 1 : 0) + (response ? 1 : 0);
        int hidden =
            (request && visibility.hidesIn(RuleTarget.REQUEST) ? 1 : 0) +
            (response && visibility.hidesIn(RuleTarget.RESPONSE) ? 1 : 0);
        return hidden == 0 ? State.SHOWN : hidden == sides ? State.HIDDEN : State.MIXED;
    }

    State state(GroupRow group) {
        Set<State> states = new HashSet<>();
        group.headers.forEach(header -> states.add(state(header)));
        return states.size() > 1 ? State.MIXED : states.contains(State.HIDDEN) ? State.HIDDEN : State.SHOWN;
    }

    /** Shows a header or group that is hidden at all, and hides one that is fully shown. */
    void toggle(Object row) {
        if (row instanceof HeaderRow header) {
            Visibility visibility = state(header) == State.SHOWN ? Visibility.HIDE : Visibility.SHOW;
            settings.headers.set(header.name(), visibility, catalog);
        } else if (row instanceof GroupRow group) {
            boolean hide = state(group) == State.SHOWN;
            if (group.isCustom()) {
                Visibility visibility = hide ? Visibility.HIDE : Visibility.SHOW;
                group.headers.forEach(header -> settings.headers.set(header.name(), visibility, catalog));
            } else {
                settings.headers.setGroup(group.id, hide, catalog);
            }
        }
        changed();
    }

    private void changed() {
        onChange.run();
        refresh();
    }

    private void setUpTable() {
        table.setShowGrid(false);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(1).setMinWidth(56);
        table.getColumnModel().getColumn(1).setMaxWidth(56);
        table.getColumnModel().getColumn(0).setCellRenderer(new NameRenderer());
        table.getColumnModel().getColumn(1).setCellRenderer(new ShowRenderer());
        table.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                showDetails();
            }
        });
        table.getAccessibleContext().setAccessibleName("Headers");
        // Tab leaves the table, as from any other control: moving between its two cells is of no use.
        table.setFocusTraversalKeys(KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS, null);
        table.setFocusTraversalKeys(KeyboardFocusManager.BACKWARD_TRAVERSAL_KEYS, null);
        for (String key : List.of("TAB", "shift TAB")) {
            table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(key), "none");
        }

        table.addMouseListener(
            new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row < 0 || e.getButton() != MouseEvent.BUTTON1) {
                        return;
                    }
                    Object item = model.rows.get(row);
                    if (table.columnAtPoint(e.getPoint()) == 1) {
                        toggle(item);
                    } else if (item instanceof GroupRow group) {
                        Rectangle cell = table.getCellRect(row, 0, false);
                        if (e.getClickCount() == 2 || e.getX() - cell.x < indent()) {
                            setExpanded(group, !expanded.contains(group.id));
                        }
                    }
                }
            }
        );

        bind("SPACE", "toggleShow", () -> selectedRow().ifPresent(this::toggle));
        bind("LEFT", "collapse", () ->
            selectedRow().ifPresent(row -> {
                if (row instanceof GroupRow group) {
                    setExpanded(group, false);
                } else if (row instanceof HeaderRow header) {
                    selectRow(model.rows.indexOf(header.group()));
                }
            })
        );
        bind("RIGHT", "expand", () ->
            selectedRow().ifPresent(row -> {
                if (row instanceof GroupRow group) {
                    setExpanded(group, true);
                }
            })
        );
        // Typing a name searches, as in Burp's tables.
        table.addKeyListener(
            new KeyAdapter() {
                @Override
                public void keyTyped(KeyEvent e) {
                    char c = e.getKeyChar();
                    if (
                        (Character.isLetterOrDigit(c) || c == '-') &&
                        (e.getModifiersEx() & ~KeyEvent.SHIFT_DOWN_MASK) == 0
                    ) {
                        filter.requestFocusInWindow();
                        filter.replaceSelection(String.valueOf(c));
                        e.consume();
                    }
                }
            }
        );
    }

    private void bind(String key, String name, Runnable action) {
        table.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), name);
        table.getActionMap().put(
            name,
            new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    action.run();
                }
            }
        );
    }

    private Optional<Object> selectedRow() {
        int row = table.getSelectedRow();
        return row < 0 ? Optional.empty() : Optional.of(model.rows.get(row));
    }

    private void selectRow(int row) {
        if (row >= 0) {
            table.setRowSelectionInterval(row, row);
            table.scrollRectToVisible(table.getCellRect(row, 0, true));
        }
    }

    private void setExpanded(GroupRow group, boolean open) {
        if (open ? expanded.add(group.id) : expanded.remove(group.id)) {
            refresh();
        }
    }

    /** Lists the rows again after a change, keeping the selection. */
    private void refresh() {
        Object selected = selectedRow().orElse(null);
        model.rows = buildRows();
        model.fireTableDataChanged();
        int index = -1;
        for (int i = 0; i < model.rows.size() && selected != null; i++) {
            if (sameRow(model.rows.get(i), selected)) {
                index = i;
            }
        }
        if (index >= 0) {
            table.setRowSelectionInterval(index, index);
        }
        showDetails();

        int hidden = hiddenCount();
        summary.setText(
            message != null
                ? hidden == 0
                    ? "Nothing hidden in this message"
                    : hidden + " hidden in this message"
                : catalog.headerCount() + " headers in " + catalog.groups().size() + " groups, " + hidden + " hidden"
        );
    }

    private static boolean sameRow(Object a, Object b) {
        if (a instanceof GroupRow x && b instanceof GroupRow y) {
            return x.id.equals(y.id);
        }
        return a instanceof HeaderRow x && b instanceof HeaderRow y && x.name().equalsIgnoreCase(y.name());
    }

    private List<Object> buildRows() {
        GroupRow custom = new GroupRow(CUSTOM, "Custom headers");
        Map<String, GroupRow> groups = new LinkedHashMap<>();
        groups.put(CUSTOM, custom);
        catalog.groups().forEach(group -> groups.put(group.id(), new GroupRow(group.id(), group.label())));

        if (view == View.MESSAGE) {
            Set<String> seen = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for (List<String> names : List.of(message.request(), message.response())) {
                for (String name : names) {
                    if (seen.add(name)) {
                        add(groups, custom, name, message.where(name));
                    }
                }
            }
        } else {
            customHeaders().forEach(name -> add(groups, custom, name, Direction.BOTH));
            catalog
                .groups()
                .forEach(group -> group.headers().forEach(header -> add(groups, custom, header.name(), header.in())));
        }

        String query = filter.getText().trim().toLowerCase(Locale.ROOT);
        List<Object> rows = new ArrayList<>();
        for (GroupRow group : groups.values()) {
            if (!query.isEmpty() && !group.label.toLowerCase(Locale.ROOT).contains(query)) {
                group.headers.removeIf(header -> !header.name().toLowerCase(Locale.ROOT).contains(query));
            }
            if (group.headers.isEmpty()) {
                continue;
            }
            rows.add(group);
            if (expanded.contains(group.id) || !query.isEmpty()) {
                rows.addAll(group.headers);
            }
        }
        return rows;
    }

    private void add(Map<String, GroupRow> groups, GroupRow custom, String name, Direction sides) {
        HeaderCatalog.Header entry = catalog.header(name).orElse(null);
        GroupRow group = entry == null ? custom : groups.get(catalog.groupOf(name).orElseThrow().id());
        group.headers.add(new HeaderRow(entry == null ? name : entry.name(), entry, group, sides));
    }

    /** The headers the template has a setting for that the catalog doesn't know. */
    private List<String> customHeaders() {
        return settings.headers.headers
            .keySet()
            .stream()
            .filter(name -> catalog.header(name).isEmpty())
            .toList();
    }

    private void showDetails() {
        details.removeAll();
        JPanel content = new JPanel(new BorderLayout(0, 4));
        content.setOpaque(false);
        Object row = selectedRow().orElse(null);
        if (row instanceof HeaderRow header) {
            content.add(Widgets.title(header.name()), BorderLayout.NORTH);
            content.add(Widgets.wrappingHint(describe(header), 400), BorderLayout.CENTER);
            JComponent actions = headerActions(header);
            if (actions != null) {
                content.add(actions, BorderLayout.SOUTH);
            }
        } else if (row instanceof GroupRow group) {
            content.add(Widgets.title(group.label), BorderLayout.NORTH);
            content.add(
                Widgets.wrappingHint(
                    group.isCustom()
                        ? "Headers Screenshot Mode doesn't know. Its checkbox shows or hides all of them."
                        : "Its checkbox shows or hides every header in the group, including ones a later version adds.",
                    400
                ),
                BorderLayout.CENTER
            );
        } else {
            content.add(
                Widgets.wrappingHint("Uncheck a header, or a whole group, to hide it in the screenshot.", 400),
                BorderLayout.CENTER
            );
        }
        details.add(content, BorderLayout.CENTER);
        details.revalidate();
        details.repaint();
    }

    private String describe(HeaderRow header) {
        if (header.entry() == null) {
            return "Custom header";
        }
        String kind = switch (header.entry().in()) {
            case REQUEST -> "Request header";
            case RESPONSE -> "Response header";
            case BOTH -> "Request and response header";
        };
        return kind + " · " + header.entry().spec() + " · " + header.group().label;
    }

    /** "Hide in" for a hidden header that can be in both messages; "Remove" for a custom header the template lists. */
    private JComponent headerActions(HeaderRow header) {
        List<Component> actions = new ArrayList<>();
        Visibility visibility = settings.headers.of(header.name(), catalog);
        boolean bothSides = header.entry() == null || header.entry().in() == Direction.BOTH;
        if (bothSides && visibility != Visibility.SHOW) {
            SegmentedControl<Direction> scope = new SegmentedControl<>(
                Direction.values(),
                visibility.hiddenIn(),
                direction ->
                    switch (direction) {
                        case BOTH -> "Both";
                        case REQUEST -> "Requests";
                        case RESPONSE -> "Responses";
                    },
                direction -> {
                    settings.headers.set(header.name(), Visibility.hiddenIn(direction), catalog);
                    changed();
                }
            );
            JLabel label = new JLabel("Hide in");
            label.setLabelFor(scope);
            actions.add(label);
            actions.add(scope);
        }
        if (header.entry() == null && settings.headers.headers.containsKey(header.name())) {
            JButton remove = Widgets.button("Remove");
            remove.putClientProperty("JButton.buttonType", "borderless");
            remove.setToolTipText("Remove this header from the template's list");
            remove.addActionListener(event -> {
                settings.headers.headers.remove(header.name());
                changed();
            });
            actions.add(remove);
        }
        return actions.isEmpty() ? null : Widgets.row(actions.toArray(Component[]::new));
    }

    /** Asks for a header name and hides it, then shows its row. */
    private void addHeader() {
        String input = JOptionPane.showInputDialog(this, "Header name:", "Hide a Header", JOptionPane.PLAIN_MESSAGE);
        if (input == null) {
            return;
        }
        int colon = input.indexOf(':');
        String name = (colon >= 0 ? input.substring(0, colon) : input).trim();
        if (name.isEmpty()) {
            return;
        }
        if (!name.chars().allMatch(c -> c > 32 && c < 127)) {
            JOptionPane.showMessageDialog(
                this,
                "A header name can't contain spaces or non-ASCII characters.",
                "Hide a Header",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        settings.headers.set(name, Visibility.HIDE, catalog);
        String listed = catalog.header(name).map(HeaderCatalog.Header::name).orElse(name);
        String group = catalog.groupOf(name).map(HeaderCatalog.Group::id).orElse(CUSTOM);
        if (view == View.MESSAGE && message.where(name) == null) {
            view = View.ALL;
            views.setValue(View.ALL);
        }
        expanded.add(group);
        filter.setText("");
        changed();
        select(listed);
    }

    /** Where a header's name starts: after the width of a group's arrow. */
    private static int indent() {
        Icon icon = UIManager.getIcon("Tree.collapsedIcon");
        return 4 + (icon != null ? icon.getIconWidth() : 10) + 6;
    }

    private final class Model extends AbstractTableModel {

        List<Object> rows = List.of();

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Header" : "Show";
        }

        @Override
        public Object getValueAt(int row, int column) {
            return rows.get(row);
        }
    }

    /** Full header names in tooltips, as long ones are cut to fit the sidebar. */
    private final class Table extends JTable {

        Table(Model model) {
            super(model);
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int row = rowAtPoint(event.getPoint());
            if (row >= 0 && model.rows.get(row) instanceof HeaderRow header) {
                return header.name();
            }
            return null;
        }
    }

    /** A group's arrow, label and header count, or a header's indented name and where it is used. */
    private final class NameRenderer extends JPanel implements TableCellRenderer {

        private final JLabel name = new JLabel();
        private final JLabel side = new JLabel();

        NameRenderer() {
            super(new BorderLayout(8, 0));
            add(name, BorderLayout.CENTER);
            add(side, BorderLayout.EAST);
            setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 8));
        }

        @Override
        public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column
        ) {
            setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            Font font = table.getFont();
            java.awt.Color foreground = isSelected ? table.getSelectionForeground() : table.getForeground();
            side.setForeground(isSelected ? foreground : Widgets.mutedColor());
            side.setFont(font);
            if (value instanceof GroupRow group) {
                boolean open = expanded.contains(group.id) || !filter.getText().isBlank();
                name.setIcon(UIManager.getIcon(open ? "Tree.expandedIcon" : "Tree.collapsedIcon"));
                name.setIconTextGap(6);
                name.setBorder(BorderFactory.createEmptyBorder());
                name.setText(group.label);
                name.setFont(font.deriveFont(Font.BOLD));
                name.setForeground(foreground);
                side.setText(String.valueOf(group.headers.size()));
            } else if (value instanceof HeaderRow header) {
                State state = state(header);
                name.setIcon(null);
                name.setBorder(BorderFactory.createEmptyBorder(0, indent() - 4, 0, 0));
                name.setText(header.name());
                name.setFont(font);
                // Muted as well as unchecked, so a hidden header doesn't rely on the checkbox alone.
                name.setForeground(state == State.HIDDEN && !isSelected ? Widgets.mutedColor() : foreground);
                side.setText(where(header, state));
            }
            return this;
        }

        private String where(HeaderRow header, State state) {
            String sides = header.entry() == null && view == View.ALL ? "" : header.sides().id();
            if (state != State.MIXED) {
                return sides;
            }
            Direction hiddenIn = settings.headers.of(header.name(), catalog).hiddenIn();
            return sides + " · hidden in " + (hiddenIn == Direction.REQUEST ? "requests" : "responses");
        }
    }

    /** The Show checkbox; a dash when a group's headers, or a header's two messages, differ. */
    private final class ShowRenderer extends JCheckBox implements TableCellRenderer {

        ShowRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setBorderPainted(false);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(
            JTable table,
            Object value,
            boolean isSelected,
            boolean hasFocus,
            int row,
            int column
        ) {
            setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            State state;
            String name;
            if (value instanceof GroupRow group) {
                state = state(group);
                name = "Show " + group.label;
            } else {
                HeaderRow header = (HeaderRow) value;
                state = state(header);
                name = "Show " + header.name() + " header";
            }
            setSelected(state == State.SHOWN);
            putClientProperty("JButton.selectedState", state == State.MIXED ? "indeterminate" : null);
            getAccessibleContext().setAccessibleName(name);
            getAccessibleContext().setAccessibleDescription(state == State.MIXED ? "Partly shown" : null);
            return this;
        }
    }
}
