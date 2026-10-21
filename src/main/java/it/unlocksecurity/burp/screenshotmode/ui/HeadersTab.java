package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.Direction;
import it.unlocksecurity.burp.screenshotmode.model.HeaderCatalog;
import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.Rule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.RuleType;
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
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
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

    /** What the tab asks of the Rules tab, where every rule is edited. */
    interface Links {
        Links NONE = new Links() {
            @Override
            public void create(Rule rule, String notice) {}

            @Override
            public void open(Rule rule, String notice) {}
        };

        /** Adds a new rule at the top of its list and opens it, saying so with {@code notice}. */
        void create(Rule rule, String notice);

        /** Opens an existing rule, saying why with {@code notice}. */
        void open(Rule rule, String notice);
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
    private final Links links;
    /** The rules that changed each header line of the message on screen, by message and header name. */
    private Map<RuleTarget, Map<String, Set<Rule>>> hits = Map.of();
    private boolean openedGroupsWithRules;
    /** In the screenshot window only. */
    private final SegmentedControl<View> views;
    private View view;

    /** @param message the headers of the message on screen, or {@code null} in the template editor */
    HeadersTab(Settings settings, HeaderCatalog catalog, InMessage message, Runnable onChange) {
        this(settings, catalog, message, onChange, Links.NONE);
    }

    HeadersTab(Settings settings, HeaderCatalog catalog, InMessage message, Runnable onChange, Links links) {
        super(new BorderLayout(0, 10));
        this.links = links;
        this.settings = settings;
        this.catalog = catalog;
        this.message = message;
        this.onChange = onChange;
        this.view = message != null ? View.MESSAGE : View.ALL;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        Widgets.placeholder(filter, "Filter headers");
        Widgets.searchField(filter);
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

    /**
     * Takes what the rules changed in the message on screen, after each render. The first time, the groups of the
     * headers a rule changed open, as those are the ones to check.
     */
    void showHits(Map<RuleTarget, Map<String, Set<Rule>>> hits) {
        this.hits = hits;
        if (!openedGroupsWithRules && message != null) {
            openedGroupsWithRules = true;
            for (List<String> names : List.of(message.request(), message.response())) {
                for (String name : names) {
                    if (!rulesFor(name, true).isEmpty() || !rulesFor(name, false).isEmpty()) {
                        expanded.add(catalog.groupOf(name).map(HeaderCatalog.Group::id).orElse(CUSTOM));
                    }
                }
            }
        }
        refresh();
    }

    /**
     * The rules that redact (or highlight) a header: the header rules naming it, and the rules that changed its line
     * in the message on screen. Rules that are off don't count.
     */
    List<Rule> rulesFor(String name, boolean redactions) {
        List<? extends Rule> rules = redactions ? settings.redactions : settings.highlights;
        List<Rule> found = new ArrayList<>();
        for (Rule rule : rules) {
            boolean names = rule.type == RuleType.HEADER && rule.headers.stream().anyMatch(name::equalsIgnoreCase);
            boolean changed = hits
                .values()
                .stream()
                .anyMatch(side -> side.getOrDefault(name, Set.of()).contains(rule));
            if (rule.enabled && (names || changed)) {
                found.add(rule);
            }
        }
        return found;
    }

    /**
     * The rule that already redacts (or highlights) these headers, if any: for one header, any rule changing it; for
     * a group, a header rule naming all of them. Acting on a header opens that rule instead of adding a duplicate.
     */
    Rule existingRule(List<String> names, boolean redactions) {
        if (names.size() == 1) {
            List<Rule> rules = rulesFor(names.getFirst(), redactions);
            return rules.isEmpty() ? null : rules.getFirst();
        }
        List<? extends Rule> rules = redactions ? settings.redactions : settings.highlights;
        for (Rule rule : rules) {
            if (
                rule.enabled &&
                rule.type == RuleType.HEADER &&
                names.stream().allMatch(name -> rule.headers.stream().anyMatch(name::equalsIgnoreCase))
            ) {
                return rule;
            }
        }
        return null;
    }

    /** Opens the rule that redacts (or highlights) these headers, or adds a header rule for them. */
    void act(List<String> names, String label, boolean redaction) {
        Rule existing = existingRule(names, redaction);
        if (existing != null) {
            links.open(existing, "This rule " + (redaction ? "redacts " : "highlights ") + label + ".");
        } else {
            links.create(
                redaction ? RedactionRule.forHeaders(names, RedactionMode.REPLACE) : HighlightRule.forHeaders(names),
                (redaction ? "Redaction" : "Highlight") + " created for " + label + "."
            );
        }
    }

    /** The headers an action on a row applies to: one header, or all the headers of a group. */
    private List<String> headersOf(Object row) {
        if (row instanceof HeaderRow header) {
            return List.of(header.name());
        }
        GroupRow group = (GroupRow) row;
        if (group.isCustom()) {
            return group.headers.stream().map(HeaderRow::name).toList();
        }
        return catalog.group(group.id).orElseThrow().headers().stream().map(HeaderCatalog.Header::name).toList();
    }

    private static String labelOf(Object row) {
        return row instanceof HeaderRow header ? header.name() : ((GroupRow) row).label;
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
                public void mousePressed(MouseEvent e) {
                    popup(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    popup(e);
                }

                private void popup(MouseEvent e) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (e.isPopupTrigger() && row >= 0) {
                        selectRow(row);
                        showMenu(model.rows.get(row), table, e.getX(), e.getY());
                    }
                }

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
        Runnable menuKey = () ->
            selectedRow().ifPresent(row -> {
                Rectangle cell = table.getCellRect(table.getSelectedRow(), 0, false);
                showMenu(row, table, cell.x + indent(), cell.y + cell.height);
            });
        bind("CONTEXT_MENU", "showMenu", menuKey);
        bind("shift F10", "showMenuShiftF10", menuKey);
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
            Widgets.Stack lower = new Widgets.Stack(4, true);
            JComponent actions = headerActions(header);
            if (actions != null) {
                lower.addItem(actions);
            }
            lower.addItem(ruleActions(header));
            content.add(lower, BorderLayout.SOUTH);
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
            content.add(ruleActions(group), BorderLayout.SOUTH);
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

    /**
     * For each kind of rule, the rules that already redact or highlight the row, each with "Edit ›" to open it, or
     * else a button adding one. Rules are edited in the Rules tab only.
     */
    private JComponent ruleActions(Object row) {
        Widgets.Stack stack = new Widgets.Stack(2, true);
        List<Component> buttons = new ArrayList<>();
        for (boolean redaction : List.of(true, false)) {
            List<Rule> rules =
                row instanceof HeaderRow header
                    ? rulesFor(header.name(), redaction)
                    : Optional.ofNullable(existingRule(headersOf(row), redaction))
                          .stream()
                          .toList();
            for (Rule rule : rules) {
                JLabel by = new JLabel((redaction ? "Redacted by “" : "Highlighted by “") + RuleList.title(rule) + "”");
                by.setToolTipText(by.getText());
                JButton edit = Widgets.button("Edit ›");
                edit.putClientProperty("JButton.buttonType", "borderless");
                edit.getAccessibleContext().setAccessibleName("Edit the rule " + RuleList.title(rule));
                edit.addActionListener(event ->
                    links.open(rule, "This rule " + (redaction ? "redacts " : "highlights ") + labelOf(row) + ".")
                );
                JPanel line = new JPanel(new BorderLayout(8, 0));
                line.setOpaque(false);
                line.add(by, BorderLayout.CENTER);
                line.add(edit, BorderLayout.EAST);
                stack.addItem(line);
            }
            if (rules.isEmpty()) {
                String text = redaction ? (row instanceof GroupRow ? "Redact values…" : "Redact value…") : "Highlight…";
                JButton add = Widgets.button(text);
                add.setToolTipText(
                    "Add a header rule that " +
                        (redaction ? "redacts the value of " : "highlights ") +
                        (row instanceof GroupRow ? "every header in the group" : "this header")
                );
                add.addActionListener(event -> act(headersOf(row), labelOf(row), redaction));
                buttons.add(add);
            }
        }
        if (!buttons.isEmpty()) {
            stack.addItem(Widgets.row(buttons.toArray(Component[]::new)));
        }
        return stack;
    }

    /** The row's actions, on right-click or with the context menu key: show or hide, and its rules. */
    private void showMenu(Object row, Component invoker, int x, int y) {
        JPopupMenu menu = new JPopupMenu();
        boolean shown =
            row instanceof HeaderRow header ? state(header) == State.SHOWN : state((GroupRow) row) == State.SHOWN;
        JMenuItem visibility = new JMenuItem(shown ? "Hide" : "Show");
        visibility.addActionListener(event -> toggle(row));
        menu.add(visibility);
        menu.addSeparator();
        for (boolean redaction : List.of(true, false)) {
            Rule existing = existingRule(headersOf(row), redaction);
            JMenuItem item = new JMenuItem(
                existing != null
                    ? redaction
                        ? "Edit redaction rule"
                        : "Edit highlight rule"
                    : redaction
                      ? row instanceof GroupRow
                          ? "Redact values…"
                          : "Redact value…"
                      : "Highlight…"
            );
            item.addActionListener(event -> act(headersOf(row), labelOf(row), redaction));
            menu.add(item);
        }
        menu.show(invoker, x, y);
    }

    /** Asks for a header name and hides it, then shows its row. */
    private void addHeader() {
        String input = Widgets.askText(this, "Header name:", "Hide a Header");
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

        /** Where it is used, then in words what hides or changes it: "request · redacted". */
        private String where(HeaderRow header, State state) {
            List<String> parts = new ArrayList<>();
            if (header.entry() != null || view == View.MESSAGE) {
                parts.add(header.sides().id());
            }
            if (state == State.MIXED) {
                Direction hiddenIn = settings.headers.of(header.name(), catalog).hiddenIn();
                parts.add("hidden in " + (hiddenIn == Direction.REQUEST ? "requests" : "responses"));
            }
            List<String> effects = new ArrayList<>();
            if (!rulesFor(header.name(), true).isEmpty()) {
                effects.add("redacted");
            }
            if (!rulesFor(header.name(), false).isEmpty()) {
                effects.add("highlighted");
            }
            if (!effects.isEmpty()) {
                parts.add(String.join(", ", effects));
            }
            return String.join(" · ", parts);
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
