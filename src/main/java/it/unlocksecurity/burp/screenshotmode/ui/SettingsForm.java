package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.Disposition;
import it.unlocksecurity.burp.screenshotmode.model.HeaderCatalog;
import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.Rule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.RuleType;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.ThemeMode;
import it.unlocksecurity.burp.screenshotmode.model.UrlMode;
import it.unlocksecurity.burp.screenshotmode.model.ViewMode;
import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
import it.unlocksecurity.burp.screenshotmode.render.RuleStatus;
import it.unlocksecurity.burp.screenshotmode.storage.HeaderCatalogFile;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;

/**
 * Editors for every screenshot setting, mutating the given {@link Settings} in place, in three tabs ordered by how
 * often they are used: Rules, Headers, Appearance. Used both by the screenshot window sidebar and, {@code wide}, by
 * the template editor. Caido shows the same settings in one long column; this layout is our own.
 */
final class SettingsForm extends JPanel {

    static final int RULES = 0;
    static final int HEADERS = 1;
    static final int APPEARANCE = 2;

    private final Settings settings;
    private final Runnable onChange;
    private final Font mono;
    private final JTabbedPane tabs = new JTabbedPane();
    private final Badge rulesCount = new Badge();
    private final Badge headersCount = new Badge();
    private final RuleList.Expansion expansion = new RuleList.Expansion();
    private RuleList<HighlightRule> highlightList;
    private RuleList<RedactionRule> redactionList;
    private final HeadersTab headers;
    /** Above the rules after a jump from the Headers tab, with the way back. */
    private final JPanel notice = new JPanel(new BorderLayout(8, 0)) {
        @Override
        public java.awt.Color getBackground() {
            // Resolved at paint time, so it follows Burp's theme switches.
            return Widgets.themeColor("Table.selectionBackground", new java.awt.Color(0xcadaf0));
        }
    };
    private final JLabel noticeText = new JLabel();
    private int failedRules;

    SettingsForm(Settings settings, Runnable onChange, Font mono, boolean wide) {
        this(settings, onChange, mono, wide, null);
    }

    /** @param message the headers of the message on screen, or {@code null} in the template editor */
    SettingsForm(Settings settings, Runnable onChange, Font mono, boolean wide, HeadersTab.InMessage message) {
        super(new BorderLayout());
        this.settings = settings;
        this.mono = mono;
        this.onChange = () -> {
            updateCounts();
            onChange.run();
        };
        setOpaque(false);

        tabs.addTab("Rules", scrollable(rulesTab(wide)));
        headers = new HeadersTab(
            settings,
            HeaderCatalogFile.bundled(),
            message,
            this.onChange,
            new HeadersTab.Links() {
                @Override
                public void create(Rule rule, String text) {
                    if (rule instanceof HighlightRule highlight) {
                        highlightList.addFirst(highlight);
                    } else {
                        redactionList.addFirst((RedactionRule) rule);
                    }
                    showRule(rule);
                    showNotice(text);
                }

                @Override
                public void open(Rule rule, String text) {
                    showRule(rule);
                    showNotice(text);
                }
            }
        );
        tabs.addTab("Headers", headers);
        tabs.addTab("Appearance", scrollable(appearanceTab()));
        tabs.setTabComponentAt(RULES, tabTitle("Rules", rulesCount));
        tabs.setTabComponentAt(HEADERS, tabTitle("Headers", headersCount));
        updateCounts();
        add(tabs, BorderLayout.CENTER);
        // The notice is about the jump that just happened: leaving the Rules tab ends it.
        tabs.addChangeListener(event -> {
            if (tabs.getSelectedIndex() != RULES) {
                notice.setVisible(false);
            }
        });
    }

    HeadersTab headersTab() {
        return headers;
    }

    JPanel notice() {
        return notice;
    }

    JLabel noticeText() {
        return noticeText;
    }

    /** Takes what the rules changed in the header lines of the message on screen. */
    void showHeaderHits(Map<RuleTarget, Map<String, java.util.Set<Rule>>> hits) {
        headers.showHits(hits);
    }

    private void showNotice(String text) {
        noticeText.setText(text);
        notice.setVisible(true);
        notice.revalidate();
    }

    JTabbedPane tabs() {
        return tabs;
    }

    private void updateCounts() {
        // A failed rule turns the count into a warning, so it shows whichever tab is open.
        rulesCount.warn = failedRules > 0;
        rulesCount.setText(
            failedRules > 0
                ? "⚠ " + failedRules
                : String.valueOf(settings.highlights.size() + settings.redactions.size())
        );
        rulesCount.repaint();
        headersCount.setText(headers.hiddenCount() + " hidden");
    }

    private JComponent rulesTab(boolean wide) {
        HeaderCatalog catalog = HeaderCatalogFile.bundled();
        highlightList = new RuleList<>(
            "Highlights",
            "No highlights yet. Add one with + or right-click selected text in the preview.",
            settings.highlights,
            type ->
                type == RuleType.HEADER
                    ? HighlightRule.forHeaders(List.of())
                    : HighlightRule.create("", RuleTarget.REQUEST, MatchMode.REGEX),
            (rule, fields) -> RuleList.highlightEditor(rule, fields, catalog),
            expansion,
            onChange,
            mono
        );
        redactionList = new RuleList<>(
            "Redactions",
            "No redactions yet. Add one with + or right-click selected text in the preview.",
            settings.redactions,
            // Replace, like right-click › Redact: blur and opaque only hide the text on screen.
            type ->
                type == RuleType.HEADER
                    ? RedactionRule.forHeaders(List.of(), RedactionMode.REPLACE)
                    : RedactionRule.create("", RuleTarget.REQUEST, RedactionMode.REPLACE, MatchMode.REGEX),
            (rule, fields) -> RuleList.redactionEditor(rule, fields, catalog),
            expansion,
            onChange,
            mono
        );
        buildNotice();
        if (wide) {
            JPanel columns = new JPanel(new GridLayout(1, 2, 24, 0));
            columns.setOpaque(false);
            columns.add(highlightList);
            columns.add(redactionList);
            JPanel wideTab = new JPanel(new BorderLayout(0, 16));
            wideTab.setOpaque(false);
            wideTab.add(notice, BorderLayout.NORTH);
            wideTab.add(columns, BorderLayout.CENTER);
            return wideTab;
        }
        Widgets.Stack stack = new Widgets.Stack(20, false);
        stack.addItem(notice());
        stack.addItem(highlightList);
        stack.addItem(redactionList);
        return stack;
    }

    /** "‹ Back to Headers" returns to the header the user came from, still selected there. */
    private void buildNotice() {
        JButton back = Widgets.button("‹ Back to Headers");
        back.putClientProperty("JButton.buttonType", "borderless");
        back.addActionListener(event -> {
            tabs.setSelectedIndex(HEADERS);
            headers.table().requestFocusInWindow();
        });
        notice.setOpaque(true);
        notice.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 6));
        // The link moves under the text when the sidebar is too narrow for both.
        notice.add(Widgets.row(noticeText, back), BorderLayout.CENTER);
        notice.setVisible(false);
    }

    RuleList<HighlightRule> highlightList() {
        return highlightList;
    }

    RuleList<RedactionRule> redactionList() {
        return redactionList;
    }

    /** Opens a rule in its list, closing the open one. */
    void expand(Rule rule) {
        expansion.open(rule);
    }

    /** Switches to the Rules tab and opens a rule, with the focus in what it matches. */
    void showRule(Rule rule) {
        tabs.setSelectedIndex(RULES);
        for (RuleList<?> list : List.of(highlightList, redactionList)) {
            if (list.contains(rule)) {
                list.reveal(rule);
            }
        }
    }

    /**
     * Shows on each rule what it did in the screenshot: its matches, or why it did not fully apply. Rules without a
     * status (as in the template editor, which renders no message) show none.
     */
    void showRuleStatuses(Map<Object, RuleStatus> statuses) {
        highlightList.showStatuses(statuses);
        redactionList.showStatuses(statuses);
        failedRules = (int) Stream.concat(settings.highlights.stream(), settings.redactions.stream())
            .filter(rule -> rule.enabled)
            .map(statuses::get)
            .filter(status -> status != null && status.failed())
            .count();
        updateCounts();
        revalidate();
        repaint();
    }

    static String describe(RuleStatus status) {
        if (status.problem() == RuleStatus.Problem.INVALID) {
            return "⚠ Not applied: invalid regex" + (status.detail() == null ? "" : " (" + status.detail() + ")");
        }
        if (status.problem() == RuleStatus.Problem.STOPPED) {
            return "⚠ Partly applied: matching stopped early, the pattern is too slow for this message";
        }
        return switch (status.matches()) {
            case 0 -> "No matches";
            case 1 -> "1 match";
            default -> status.matches() + " matches";
        };
    }

    /** Settings that change how the screenshot looks, as a label / control grid. */
    private JComponent appearanceTab() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        int row = 0;

        SegmentedControl<Disposition> layout = Widgets.segmented(Disposition.values(), settings.disposition, value -> {
            settings.disposition = value;
            onChange.run();
        });
        addRow(grid, row++, "Layout", layout, "Request and response side by side, or one above the other");

        SegmentedControl<ViewMode> view = Widgets.segmented(ViewMode.values(), settings.view, value -> {
            settings.view = value;
            onChange.run();
        });
        addRow(grid, row++, "Body", view, "Pretty prints JSON, XML and HTML bodies");

        addRow(grid, row++, "Width", widthControl(), "Width of the captured image");

        SegmentedControl<ThemeMode> theme = Widgets.segmented(ThemeMode.values(), settings.theme, value -> {
            settings.theme = value;
            onChange.run();
        });
        addRow(grid, row++, "Theme", theme, "Colors of the screenshot");

        SegmentedControl<UrlMode> url = Widgets.segmented(UrlMode.values(), settings.url, value -> {
            settings.url = value;
            onChange.run();
        });
        addRow(grid, row++, "URL bar", url, null);

        addRow(grid, row, "Footer", footerControl(), "Metadata shown below the messages");

        Widgets.Stack stack = new Widgets.Stack(0, false);
        stack.addItem(grid);
        return stack;
    }

    private static void addRow(JPanel grid, int row, String label, JComponent control, String tooltip) {
        GridBagConstraints labelAt = new GridBagConstraints();
        labelAt.gridx = 0;
        labelAt.gridy = row;
        labelAt.anchor = GridBagConstraints.LINE_START;
        labelAt.insets = new Insets(row == 0 ? 0 : 12, 0, 0, 16);
        JLabel title = Widgets.title(label);
        title.setToolTipText(tooltip);
        title.setLabelFor(control);
        grid.add(title, labelAt);

        GridBagConstraints controlAt = new GridBagConstraints();
        controlAt.gridx = 1;
        controlAt.gridy = row;
        controlAt.weightx = 1;
        controlAt.anchor = GridBagConstraints.LINE_START;
        controlAt.insets = new Insets(row == 0 ? 0 : 12, 0, 0, 0);
        control.setToolTipText(tooltip);
        grid.add(control, controlAt);
    }

    private JComponent widthControl() {
        JSpinner pixels = new JSpinner(
            new SpinnerNumberModel(settings.widthPixels, Settings.MIN_PIXEL_WIDTH, Settings.MAX_PIXEL_WIDTH, 10)
        );
        pixels.setVisible(settings.widthMode == WidthMode.PIXEL);
        pixels.addChangeListener(event -> {
            settings.widthPixels = (Integer) pixels.getValue();
            onChange.run();
        });

        JComboBox<WidthMode> mode = new JComboBox<>(WidthMode.values());
        mode.setSelectedItem(settings.widthMode);
        JPanel row = flush(mode, pixels);
        mode.addActionListener(event -> {
            settings.widthMode = (WidthMode) mode.getSelectedItem();
            if (settings.widthMode == WidthMode.PIXEL) {
                settings.widthPixels = Settings.DEFAULT_PIXEL_WIDTH;
                pixels.setValue(Settings.DEFAULT_PIXEL_WIDTH);
            }
            pixels.setVisible(settings.widthMode == WidthMode.PIXEL);
            row.revalidate();
            onChange.run();
        });
        return row;
    }

    private JComponent footerControl() {
        JCheckBox timestamp = new JCheckBox("Timestamp", settings.showTimestamp);
        timestamp.setOpaque(false);
        timestamp.addActionListener(event -> {
            settings.showTimestamp = timestamp.isSelected();
            onChange.run();
        });
        JCheckBox responseInfo = new JCheckBox("Response info", settings.showResponseInfo);
        responseInfo.setToolTipText("Response size and round-trip time");
        responseInfo.setOpaque(false);
        responseInfo.addActionListener(event -> {
            settings.showResponseInfo = responseInfo.isSelected();
            onChange.run();
        });
        return flush(timestamp, responseInfo);
    }

    /** A row whose first component starts exactly at the left edge, aligned with the rest of the grid. */
    private static JPanel flush(JComponent... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        for (JComponent component : components) {
            if (row.getComponentCount() > 0) {
                row.add(javax.swing.Box.createHorizontalStrut(10));
            }
            row.add(component);
        }
        return row;
    }

    private static JScrollPane scrollable(JComponent content) {
        Widgets.Stack stack = new Widgets.Stack(0);
        stack.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        stack.addItem(content);
        JScrollPane scroll = new JScrollPane(stack);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private static JComponent tabTitle(String title, JLabel count) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.setOpaque(false);
        panel.add(new JLabel(title));
        panel.add(count);
        return panel;
    }

    /** A count pill for tab titles: neutral, or in the theme's warning tones (like the "Modified" chip). */
    private static final class Badge extends JLabel {

        boolean warn;

        Badge() {
            setFont(getFont().deriveFont(Font.BOLD, getFont().getSize2D() - 2));
            setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        }

        @Override
        protected void paintComponent(java.awt.Graphics graphics) {
            java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(
                warn
                    ? Widgets.themeColor("Colors.palette.warning.7", new java.awt.Color(0xf3e2c9))
                    : Widgets.themeColor("Colors.palette.mono.3", new java.awt.Color(0xe6e6e6))
            );
            g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g.dispose();
            setForeground(
                warn
                    ? Widgets.themeColor("Colors.palette.warning.2", new java.awt.Color(0x976a14))
                    : UIManager.getColor("Label.foreground")
            );
            super.paintComponent(graphics);
        }
    }
}
