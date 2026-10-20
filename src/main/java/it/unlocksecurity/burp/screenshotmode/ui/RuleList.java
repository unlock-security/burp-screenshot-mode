package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.HeaderCatalog;
import it.unlocksecurity.burp.screenshotmode.model.HighlightMode;
import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.Rule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.RuleType;
import it.unlocksecurity.burp.screenshotmode.render.RuleEngine;
import it.unlocksecurity.burp.screenshotmode.render.RuleStatus;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * One list of the Rules tab, highlights or redactions: a row per rule with an on/off checkbox, its name or pattern, a
 * one-line summary and what it matched in the screenshot. A row expands into the rule's editor; only one rule is open
 * at a time across both lists. Edits mutate the rule in place and then call {@code onChange}.
 */
final class RuleList<R extends Rule> extends JPanel {

    /** Which rule is open, shared by the highlight and redaction lists. */
    static final class Expansion {

        private final List<RuleList<?>> lists = new ArrayList<>();
        private Rule open;

        Rule open() {
            return open;
        }

        void open(Rule rule) {
            open = rule;
            lists.forEach(RuleList::updateExpansion);
        }
    }

    /** Builds the editor fields of an expanded rule. */
    interface Editor<T extends Rule> {
        void build(T rule, RuleList<T>.Fields fields);
    }

    private final List<R> rules;
    private final Function<RuleType, R> factory;
    private final Editor<R> editor;
    private final Expansion expansion;
    private final Runnable onChange;
    private final Font mono;
    private final String emptyText;
    private final Widgets.Stack box = new Widgets.Stack(0, false);
    private final Map<Rule, Row> rows = new IdentityHashMap<>();
    private Map<Object, RuleStatus> statuses = null;

    RuleList(
        String title,
        String emptyText,
        List<R> rules,
        Function<RuleType, R> factory,
        Editor<R> editor,
        Expansion expansion,
        Runnable onChange,
        Font mono
    ) {
        super(new BorderLayout());
        this.rules = rules;
        this.factory = factory;
        this.editor = editor;
        this.expansion = expansion;
        this.onChange = onChange;
        this.mono = mono;
        this.emptyText = emptyText;
        expansion.lists.add(this);
        setOpaque(false);

        JButton add = Widgets.iconButton(Widgets.Glyph.PLUS, "Add rule", () -> {});
        JPopupMenu types = new JPopupMenu();
        for (RuleType type : List.of(RuleType.HEADER, RuleType.PATTERN)) {
            JMenuItem item = new JMenuItem(type.label());
            item.addActionListener(event -> addRule(type));
            types.add(item);
        }
        add.addActionListener(event -> types.show(add, 0, add.getHeight()));
        add.getAccessibleContext().setAccessibleName("Add " + title.toLowerCase() + " rule");
        add(Widgets.section(title, null, add, box), BorderLayout.CENTER);
        rebuild();
    }

    /** The row of a rule, for tests. */
    Row row(Rule rule) {
        return rows.get(rule);
    }

    /**
     * Shows what each rule matched in the screenshot, or why it did not fully apply; {@code null} in the template
     * editor, which renders no message.
     */
    void showStatuses(Map<Object, RuleStatus> statuses) {
        this.statuses = statuses;
        rows.values().forEach(Row::updateStatus);
    }

    /** Opens a rule, scrolls to it and puts the focus in its first field. */
    void reveal(Rule rule) {
        expansion.open(rule);
        Row row = rows.get(rule);
        if (row != null) {
            SwingUtilities.invokeLater(() -> {
                row.scrollRectToVisible(new java.awt.Rectangle(row.getSize()));
                if (row.firstField != null) {
                    row.firstField.requestFocusInWindow();
                }
            });
        }
    }

    boolean contains(Rule rule) {
        return rows.containsKey(rule);
    }

    /** Adds a rule at the top and opens it. */
    void addFirst(R rule) {
        rules.addFirst(rule);
        rebuild();
        onChange.run();
        reveal(rule);
    }

    void addRuleForTests(RuleType type) {
        addRule(type);
    }

    private void addRule(RuleType type) {
        // At the top, right below the button, rather than at the end of a possibly long list.
        addFirst(factory.apply(type));
    }

    private void rebuild() {
        box.clear();
        rows.clear();
        box.setBorder(rules.isEmpty() ? null : BorderFactory.createLineBorder(Widgets.borderColor(), 1, true));
        if (rules.isEmpty()) {
            box.addItem(Widgets.wrappingHint(emptyText, 260));
        }
        for (R rule : rules) {
            Row row = new Row(rule, rows.isEmpty());
            rows.put(rule, row);
            box.addItem(row);
        }
        box.revalidate();
        box.repaint();
    }

    private void updateExpansion() {
        rows.values().forEach(row -> row.setExpanded(row.rule == expansion.open));
    }

    // --- Text shown for a rule ---

    /** The name, or else what the rule matches. */
    static String title(Rule rule) {
        if (!rule.name.isBlank()) {
            return rule.name;
        }
        if (rule.type == RuleType.HEADER) {
            return rule.headers.isEmpty() ? "No headers yet" : String.join(", ", rule.headers);
        }
        return rule.regex.isEmpty() ? "No pattern yet" : rule.regex;
    }

    /** Where the rule applies and what it does, such as "Header · X-Api-Key and 4 more · Replace". */
    static String summary(Rule rule) {
        List<String> parts = new ArrayList<>();
        if (rule.type == RuleType.HEADER) {
            parts.add("Header");
            if (!rule.name.isBlank()) {
                parts.add(
                    rule.headers.isEmpty()
                        ? "no headers"
                        : rule.headers.size() == 1
                          ? rule.headers.getFirst()
                          : rule.headers.getFirst() + " and " + (rule.headers.size() - 1) + " more"
                );
            }
        } else if (rule.targets.isEmpty()) {
            parts.add("Applies nowhere");
        } else {
            for (RuleTarget target : RuleTarget.values()) {
                if (rule.targets.contains(target)) {
                    parts.add(target.label());
                }
            }
        }
        parts.add(effect(rule));
        return String.join(" · ", parts);
    }

    private static String effect(Rule rule) {
        if (rule instanceof HighlightRule highlight) {
            if (rule.type == RuleType.HEADER) {
                return highlight.mode == HighlightMode.RECTANGLE ? "Box the whole line" : "Highlight the whole line";
            }
            return highlight.mode.label();
        }
        RedactionRule redaction = (RedactionRule) rule;
        if (redaction.mode == RedactionMode.REPLACE) {
            String text = redaction.replacementText;
            return text.isEmpty() || text.equals(RedactionRule.DEFAULT_REPLACEMENT)
                ? "Replace"
                : "Replace with " + text;
        }
        return redaction.mode.label();
    }

    // --- A row: the header line, and the editor when open ---

    final class Row extends JPanel {

        final R rule;
        private final Header header;
        private final JCheckBox enabled = new JCheckBox();
        private final JLabel title = new JLabel();
        private final JLabel summary = Widgets.hint("");
        private final JLabel swatch = new JLabel();
        private final Pill pill = new Pill();
        private final JTextArea problem = Widgets.wrappingHint("", 400);
        private JComponent editorPanel;
        private JComponent firstField;

        Row(R rule, boolean first) {
            super(new BorderLayout());
            this.rule = rule;
            setOpaque(false);
            setBorder(first ? null : BorderFactory.createMatteBorder(1, 0, 0, 0, Widgets.borderColor()));

            enabled.setSelected(rule.enabled);
            enabled.setOpaque(false);
            enabled.setToolTipText("Turn the rule on or off");
            enabled.addActionListener(event -> {
                rule.enabled = enabled.isSelected();
                refresh();
                onChange.run();
            });

            JPanel texts = new JPanel(new BorderLayout(0, 2));
            texts.setOpaque(false);
            texts.add(title, BorderLayout.NORTH);
            texts.add(summary, BorderLayout.CENTER);

            JPanel badges = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
            badges.setOpaque(false);
            badges.add(swatch);
            badges.add(pill);
            JPanel badgesHolder = new JPanel(new BorderLayout());
            badgesHolder.setOpaque(false);
            badgesHolder.add(badges, BorderLayout.NORTH);

            header = new Header();
            header.add(enabled, BorderLayout.WEST);
            header.add(texts, BorderLayout.CENTER);
            header.add(badgesHolder, BorderLayout.EAST);
            add(header, BorderLayout.NORTH);

            problem.setForeground(Widgets.errorColor());
            refresh();
            updateStatus();
            setExpanded(expansion.open == rule);
        }

        boolean isExpanded() {
            return editorPanel != null;
        }

        JComponent editorPanel() {
            return editorPanel;
        }

        Header header() {
            return header;
        }

        JLabel summaryLabel() {
            return summary;
        }

        JLabel titleLabel() {
            return title;
        }

        Pill pill() {
            return pill;
        }

        /** Updates the title, summary and swatch after an edit. */
        void refresh() {
            boolean named = !rule.name.isBlank();
            boolean empty = !named && (rule.type == RuleType.HEADER ? rule.headers.isEmpty() : rule.regex.isEmpty());
            title.setText(title(rule));
            Font base = UIManager.getFont("Label.font");
            title.setFont(
                named || rule.type == RuleType.HEADER || empty
                    ? base.deriveFont(empty ? Font.PLAIN : Font.BOLD)
                    : mono.deriveFont(base.getSize2D())
            );
            title.setForeground(empty || !rule.enabled ? Widgets.mutedColor() : UIManager.getColor("Label.foreground"));
            title.setToolTipText(title.getText());
            summary.setText(summary(rule));
            if (rule instanceof HighlightRule highlight) {
                swatch.setIcon(new Widgets.SwatchIcon(RuleEngine.parseColor(highlight.color, Color.YELLOW), 14));
            }
            swatch.setVisible(rule instanceof HighlightRule);
            updateStatus();
            header.getAccessibleContext().setAccessibleName(title.getText() + ", " + summary.getText());
        }

        void updateStatus() {
            RuleStatus status = statuses == null ? null : statuses.get(rule);
            if (!rule.enabled) {
                pill.show("Off", Pill.Style.OUTLINE, "The rule is off");
            } else if (status == null) {
                pill.setVisible(false);
            } else if (status.failed()) {
                pill.show(
                    status.problem() == RuleStatus.Problem.INVALID ? "Error" : "Stopped",
                    Pill.Style.ERROR,
                    SettingsForm.describe(status)
                );
            } else {
                pill.show(
                    String.valueOf(status.matches()),
                    status.matches() == 0 ? Pill.Style.OUTLINE : Pill.Style.FILLED,
                    SettingsForm.describe(status)
                );
            }
            boolean failed = rule.enabled && status != null && status.failed();
            problem.setText(failed ? SettingsForm.describe(status) : "");
            problem.setVisible(failed);
            header.getAccessibleContext().setAccessibleDescription(pill.isVisible() ? pill.getToolTipText() : null);
        }

        void setExpanded(boolean open) {
            if (open == isExpanded()) {
                return;
            }
            if (open) {
                Fields fields = new Fields(this);
                editor.build(rule, fields);
                fields.addFull(problem);
                editorPanel = fields.panel;
                add(editorPanel, BorderLayout.CENTER);
            } else {
                remove(editorPanel);
                editorPanel = null;
                firstField = null;
            }
            header.repaint();
            revalidate();
            repaint();
        }

        void delete() {
            rules.remove(rule);
            if (expansion.open == rule) {
                expansion.open = null;
            }
            rebuild();
            onChange.run();
        }

        /** The rule's line: focusable, Enter or Space opens and closes it, like a disclosure button. */
        final class Header extends JPanel {

            Header() {
                super(new BorderLayout(8, 0));
                setOpaque(false);
                setFocusable(true);
                setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 10));
                MouseAdapter click = new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (SwingUtilities.isLeftMouseButton(e)) {
                            toggle();
                        }
                    }
                };
                addMouseListener(click);
                for (String key : List.of("ENTER", "SPACE")) {
                    getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), "toggle");
                }
                getActionMap().put(
                    "toggle",
                    new AbstractAction() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            toggle();
                        }
                    }
                );
                addFocusListener(
                    new FocusAdapter() {
                        @Override
                        public void focusGained(FocusEvent e) {
                            repaint();
                        }

                        @Override
                        public void focusLost(FocusEvent e) {
                            repaint();
                        }
                    }
                );
            }

            void toggle() {
                requestFocusInWindow();
                expansion.open(isExpanded() ? null : rule);
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                // The background of a list, like Burp's tables, under the tinted editor of the open rule.
                graphics.setColor(Widgets.themeColor("Table.background", Color.WHITE));
                graphics.fillRect(0, 0, getWidth(), getHeight());
                super.paintComponent(graphics);
                if (isFocusOwner()) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(Widgets.themeColor("Component.focusColor", new Color(0x3d7dd8)));
                    g.setStroke(new BasicStroke(2f));
                    g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 6, 6);
                    g.dispose();
                }
            }

            @Override
            public AccessibleContext getAccessibleContext() {
                if (accessibleContext == null) {
                    accessibleContext = new AccessibleJPanel() {
                        @Override
                        public AccessibleRole getAccessibleRole() {
                            return AccessibleRole.TOGGLE_BUTTON;
                        }

                        @Override
                        public AccessibleStateSet getAccessibleStateSet() {
                            AccessibleStateSet states = super.getAccessibleStateSet();
                            states.add(isExpanded() ? AccessibleState.EXPANDED : AccessibleState.COLLAPSED);
                            return states;
                        }
                    };
                }
                return accessibleContext;
            }
        }
    }

    /** The label / control grid of an open rule, on a tinted background below its line. */
    final class Fields {

        private final Row row;
        private final JPanel panel = new JPanel(new GridBagLayout()) {
            @Override
            public Color getBackground() {
                // Resolved at paint time, so it follows Burp's theme switches.
                return UIManager.getColor("Panel.background");
            }
        };
        private int y;

        Fields(Row row) {
            this.row = row;
            panel.setOpaque(true);
            panel.setBorder(
                BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, Widgets.borderColor()),
                    BorderFactory.createEmptyBorder(8, 10, 10, 10)
                )
            );
        }

        R rule() {
            return row.rule;
        }

        Font mono() {
            return mono;
        }

        /** Updates the rule's line and the screenshot after an edit. */
        void changed() {
            row.refresh();
            onChange.run();
        }

        void deleteRule() {
            row.delete();
        }

        /** The field that takes the focus when the rule is revealed: what it matches, the one field to fill. */
        void focusFirst(JComponent field) {
            row.firstField = field;
        }

        void add(String label, JComponent control) {
            GridBagConstraints labelAt = new GridBagConstraints();
            labelAt.gridx = 0;
            labelAt.gridy = y;
            labelAt.anchor = GridBagConstraints.LINE_START;
            labelAt.insets = new Insets(y == 0 ? 0 : 6, 0, 0, 10);
            JLabel title = new JLabel(label);
            title.setLabelFor(control);
            panel.add(title, labelAt);

            GridBagConstraints controlAt = new GridBagConstraints();
            controlAt.gridx = 1;
            controlAt.gridy = y++;
            controlAt.weightx = 1;
            controlAt.fill = GridBagConstraints.HORIZONTAL;
            controlAt.insets = new Insets(labelAt.insets.top, 0, 0, 0);
            panel.add(control, controlAt);
        }

        void addFull(JComponent component) {
            GridBagConstraints at = new GridBagConstraints();
            at.gridx = 0;
            at.gridy = y++;
            at.gridwidth = 2;
            at.weightx = 1;
            at.fill = GridBagConstraints.HORIZONTAL;
            at.insets = new Insets(6, 0, 0, 0);
            panel.add(component, at);
        }

        /** The control column only, under the previous control. */
        void addUnder(JComponent component) {
            GridBagConstraints at = new GridBagConstraints();
            at.gridx = 1;
            at.gridy = y++;
            at.weightx = 1;
            at.fill = GridBagConstraints.HORIZONTAL;
            at.insets = new Insets(4, 0, 0, 0);
            panel.add(component, at);
        }
    }

    // --- Editors ---

    /** Name, what to match, then how to highlight it. */
    static void highlightEditor(HighlightRule rule, RuleList<HighlightRule>.Fields fields, HeaderCatalog catalog) {
        nameField(rule, fields);
        matchFields(rule, fields, catalog, null);

        JComboBox<HighlightMode> mode = combo(HighlightMode.values(), rule.mode, value -> {
            rule.mode = value;
            fields.changed();
        });
        JButton color = Widgets.colorButton(rule.color, value -> {
            rule.color = value;
            fields.changed();
        });
        fields.add("Style", flush(mode, color));
    }

    /** Name, what to match (with the capture groups to redact), then how to redact it. */
    static void redactionEditor(RedactionRule rule, RuleList<RedactionRule>.Fields fields, HeaderCatalog catalog) {
        nameField(rule, fields);
        matchFields(rule, fields, catalog, rule);

        JButton color = Widgets.colorButton(rule.color, value -> {
            rule.color = value;
            fields.changed();
        });
        JTextField replacement = new JTextField(rule.replacementText, 11);
        replacement.setFont(fields.mono());
        replacement.setCaretPosition(0);
        Widgets.placeholder(replacement, RedactionRule.DEFAULT_REPLACEMENT);
        replacement.getAccessibleContext().setAccessibleName("Replacement text");
        Widgets.onTextChange(replacement, text -> {
            rule.replacementText = text;
            fields.changed();
        });
        Runnable updateModeFields = () -> {
            color.setVisible(rule.mode == RedactionMode.OPAQUE);
            replacement.setVisible(rule.mode == RedactionMode.REPLACE);
        };
        updateModeFields.run();
        JPanel row = flush();
        JComboBox<RedactionMode> mode = combo(RedactionMode.values(), rule.mode, value -> {
            rule.mode = value;
            updateModeFields.run();
            row.revalidate();
            fields.changed();
        });
        row.add(mode);
        row.add(javax.swing.Box.createHorizontalStrut(6));
        row.add(color);
        row.add(replacement);
        fields.add("Redact with", row);
    }

    private static void nameField(Rule rule, RuleList<?>.Fields fields) {
        JTextField name = new JTextField(rule.name, 12);
        Widgets.placeholder(name, "Optional");
        Widgets.onTextChange(name, text -> {
            rule.name = text;
            fields.changed();
        });
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.add(name, BorderLayout.CENTER);
        row.add(Widgets.flatIconButton(Widgets.Glyph.TRASH, "Delete rule", fields::deleteRule), BorderLayout.EAST);
        fields.add("Name", row);
    }

    /** Headers for a header rule; pattern, targets and (for redactions) capture groups for a pattern rule. */
    private static void matchFields(Rule rule, RuleList<?>.Fields fields, HeaderCatalog catalog, RedactionRule groups) {
        if (rule.type == RuleType.HEADER) {
            JTextField headers = new JTextField(String.join(", ", rule.headers), 12);
            Widgets.placeholder(headers, "Header names, separated by commas");
            headers.getAccessibleContext().setAccessibleName("Headers");
            fields.focusFirst(headers);
            Widgets.onTextChange(headers, text -> {
                rule.headers = new ArrayList<>(
                    Arrays.stream(text.split(","))
                        .map(String::trim)
                        .filter(name -> !name.isEmpty())
                        .toList()
                );
                fields.changed();
            });
            JButton choose = Widgets.button("Choose…");
            choose.setToolTipText("Pick headers from the list Screenshot Mode knows");
            choose.addActionListener(event -> headerMenu(rule, catalog, headers).show(choose, 0, choose.getHeight()));
            JPanel row = new JPanel(new BorderLayout(6, 0));
            row.setOpaque(false);
            row.add(headers, BorderLayout.CENTER);
            row.add(choose, BorderLayout.EAST);
            fields.add("Headers", row);
            return;
        }

        JTextField pattern = patternField(rule.regex, rule.matchMode, fields.mono());
        pattern.getAccessibleContext().setAccessibleName("Pattern");
        fields.focusFirst(pattern);
        List<Consumer<String>> patternChanged = new ArrayList<>(1);
        JToggleButton regex = regexToggle(rule.matchMode, value -> {
            rule.matchMode = value;
            setPlaceholder(pattern, value);
            patternChanged.getFirst().accept(rule.regex);
        });
        fields.add("Pattern", Widgets.withTrailing(pattern, regex));
        if (groups != null) {
            JPanel groupPanel = new JPanel(new WrapLayout(6, 0));
            groupPanel.setOpaque(false);
            AtomicReference<List<String>> labels = new AtomicReference<>(
                RuleEngine.groupLabels(rule.regex, rule.matchMode)
            );
            Runnable rebuildGroups = () -> rebuildGroups(groupPanel, groups, labels.get(), fields::changed);
            rebuildGroups.run();
            fields.addUnder(groupPanel);
            // Selects capture groups as they appear in the pattern.
            patternChanged.add(text -> {
                List<String> newLabels = RuleEngine.groupLabels(text, rule.matchMode);
                int oldCount = labels.get().size();
                int newCount = newLabels.size();
                rule.regex = text;
                if (newCount == 0) {
                    groups.useCaptureGroups = false;
                    groups.selectedGroups = new ArrayList<>();
                } else if (!groups.useCaptureGroups) {
                    groups.useCaptureGroups = true;
                    groups.selectedGroups = allGroups(1, newCount);
                } else {
                    List<Integer> selected = new ArrayList<>(
                        groups.selectedGroups
                            .stream()
                            .filter(g -> g <= newCount)
                            .toList()
                    );
                    selected.addAll(allGroups(oldCount + 1, newCount));
                    groups.selectedGroups = selected;
                }
                // Also on a renamed group, whose checkbox is labelled with the name.
                if (!newLabels.equals(labels.get())) {
                    labels.set(newLabels);
                    rebuildGroups.run();
                }
                validate(pattern, rule.regex, rule.matchMode);
                fields.changed();
            });
        } else {
            patternChanged.add(text -> {
                rule.regex = text;
                validate(pattern, rule.regex, rule.matchMode);
                fields.changed();
            });
        }
        Widgets.onTextChange(pattern, text -> patternChanged.getFirst().accept(text));

        List<JCheckBox> boxes = new ArrayList<>();
        Runnable keepOne = () -> boxes.forEach(box -> box.setEnabled(!(box.isSelected() && rule.targets.size() == 1)));
        JPanel targets = flush();
        for (RuleTarget target : RuleTarget.values()) {
            JCheckBox box = new JCheckBox(target.label(), rule.targets.contains(target));
            box.setOpaque(false);
            box.addActionListener(event -> {
                if (box.isSelected()) {
                    rule.targets.add(target);
                } else {
                    rule.targets.remove(target);
                }
                keepOne.run();
                fields.changed();
            });
            boxes.add(box);
            if (targets.getComponentCount() > 0) {
                targets.add(javax.swing.Box.createHorizontalStrut(10));
            }
            targets.add(box);
        }
        keepOne.run();
        fields.add("Applies to", targets);
    }

    /** The catalog's groups, each a submenu of its headers, checked when the rule names them. */
    private static JPopupMenu headerMenu(Rule rule, HeaderCatalog catalog, JTextField field) {
        JPopupMenu menu = new JPopupMenu();
        for (HeaderCatalog.Group group : catalog.groups()) {
            JMenu submenu = new JMenu(group.label());
            for (HeaderCatalog.Header header : group.headers()) {
                boolean named = rule.headers.stream().anyMatch(header.name()::equalsIgnoreCase);
                JCheckBoxMenuItem item = new JCheckBoxMenuItem(header.name(), named);
                item.addActionListener(event -> {
                    List<String> headers = new ArrayList<>(rule.headers);
                    headers.removeIf(header.name()::equalsIgnoreCase);
                    if (item.isSelected()) {
                        headers.add(header.name());
                    }
                    field.setText(String.join(", ", headers));
                });
                submenu.add(item);
            }
            menu.add(submenu);
        }
        return menu;
    }

    /** @param labels label of each capture group, see {@link RuleEngine#groupLabels} */
    private static void rebuildGroups(JPanel panel, RedactionRule rule, List<String> labels, Runnable onChange) {
        int count = labels.size();
        panel.removeAll();
        panel.setVisible(count > 0);
        JCheckBox enabled = new JCheckBox("Redact only", rule.useCaptureGroups);
        enabled.setToolTipText("Redact only the selected capture groups instead of the whole match");
        enabled.setOpaque(false);
        panel.add(enabled);

        List<JCheckBox> boxes = new ArrayList<>();
        for (int group = 1; group <= count; group++) {
            int index = group;
            JCheckBox box = new JCheckBox(labels.get(group - 1), rule.selectedGroups.contains(group));
            box.setToolTipText("Group " + group);
            box.setOpaque(false);
            box.setEnabled(rule.useCaptureGroups);
            box.addActionListener(event -> {
                List<Integer> selected = new ArrayList<>(rule.selectedGroups);
                selected.remove(Integer.valueOf(index));
                if (box.isSelected()) {
                    selected.add(index);
                    selected.sort(null);
                }
                rule.selectedGroups = selected;
                onChange.run();
            });
            boxes.add(box);
            panel.add(box);
        }

        enabled.addActionListener(event -> {
            rule.useCaptureGroups = enabled.isSelected();
            rule.selectedGroups = enabled.isSelected() ? allGroups(1, count) : new ArrayList<>();
            for (JCheckBox box : boxes) {
                box.setEnabled(rule.useCaptureGroups);
                box.setSelected(rule.useCaptureGroups);
            }
            onChange.run();
        });
        panel.revalidate();
        panel.repaint();
    }

    private static List<Integer> allGroups(int from, int to) {
        List<Integer> groups = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            groups.add(i);
        }
        return groups;
    }

    private static JTextField patternField(String text, MatchMode matchMode, Font mono) {
        JTextField field = new JTextField(text, 12);
        field.setFont(mono);
        setPlaceholder(field, matchMode);
        validate(field, text, matchMode);
        return field;
    }

    /**
     * The match mode as a {@code .*} toggle inside the pattern field, as in Burp's and IDEs' search boxes: on for a
     * regular expression, off for a literal string.
     */
    private static JToggleButton regexToggle(MatchMode matchMode, Consumer<MatchMode> onChange) {
        JToggleButton toggle = Widgets.greyedWhenDisabled(new JToggleButton(".*", matchMode == MatchMode.REGEX));
        toggle.putClientProperty("JButton.buttonType", "toolBarButton");
        toggle.setFont(new Font(Font.MONOSPACED, Font.BOLD, toggle.getFont().getSize()));
        toggle.setMargin(new Insets(0, 4, 0, 4));
        toggle.setFocusable(false);
        Runnable tooltip = () ->
            toggle.setToolTipText(
                toggle.isSelected()
                    ? "Regular expression (click to match the literal text)"
                    : "Literal text (click to use a regular expression)"
            );
        tooltip.run();
        toggle.addActionListener(event -> {
            tooltip.run();
            onChange.accept(toggle.isSelected() ? MatchMode.REGEX : MatchMode.STRING);
        });
        return toggle;
    }

    private static void setPlaceholder(JTextField field, MatchMode matchMode) {
        field.putClientProperty(
            "JTextField.placeholderText",
            matchMode == MatchMode.STRING ? "Literal string" : "Regex pattern"
        );
    }

    /** Flags invalid patterns (Java regex syntax) instead of silently matching nothing. */
    private static void validate(JTextField field, String pattern, MatchMode matchMode) {
        String error = RuleEngine.validate(pattern, matchMode).orElse(null);
        Color normal = UIManager.getColor("TextField.foreground");
        field.setForeground(error == null ? normal : Widgets.errorColor());
        field.putClientProperty("JComponent.outline", error == null ? null : "error");
        field.setToolTipText(
            error == null
                ? matchMode == MatchMode.STRING
                    ? "Literal string"
                    : "Java regular expression"
                : "Invalid regex: " + error
        );
    }

    private static <E> JComboBox<E> combo(E[] values, E selected, Consumer<E> onChange) {
        JComboBox<E> combo = new JComboBox<>(values);
        combo.setSelectedItem(selected);
        combo.addActionListener(event -> {
            @SuppressWarnings("unchecked")
            E value = (E) combo.getSelectedItem();
            if (value != null) {
                onChange.accept(value);
            }
        });
        return combo;
    }

    /** A row whose first component starts at the left edge of the control column. */
    private static JPanel flush(Component... components) {
        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        for (Component component : components) {
            if (row.getComponentCount() > 0) {
                row.add(javax.swing.Box.createHorizontalStrut(6));
            }
            row.add(component);
        }
        return row;
    }

    /**
     * What a rule matched: a count (outlined when 0, filled otherwise), "Error" or "Stopped" in the theme's error tones
     * when it did not fully apply, or "Off". Words and fill, not only color.
     */
    static final class Pill extends JLabel {

        enum Style {
            OUTLINE,
            FILLED,
            ERROR,
        }

        private Style style = Style.OUTLINE;

        Pill() {
            setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() - 2));
            setBorder(BorderFactory.createEmptyBorder(1, 7, 1, 7));
            setVisible(false);
        }

        Style style() {
            return style;
        }

        void show(String text, Style style, String tooltip) {
            this.style = style;
            setText(text);
            setToolTipText(tooltip);
            setVisible(true);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = getHeight();
            switch (style) {
                case OUTLINE -> {
                    g.setColor(Widgets.borderColor());
                    g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                    setForeground(Widgets.mutedColor());
                }
                case FILLED -> {
                    g.setColor(Widgets.themeColor("Colors.palette.mono.3", new Color(0xe6e6e6)));
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                    setForeground(UIManager.getColor("Label.foreground"));
                }
                case ERROR -> {
                    g.setColor(Widgets.themeColor("Colors.palette.error.7", new Color(0xf6d5d3)));
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
                    setForeground(Widgets.themeColor("Colors.palette.error.2", new Color(0x9c2b23)));
                }
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }
}
