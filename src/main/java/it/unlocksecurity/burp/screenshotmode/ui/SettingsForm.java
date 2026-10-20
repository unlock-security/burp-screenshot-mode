package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.Disposition;
import it.unlocksecurity.burp.screenshotmode.model.HeaderHideTarget;
import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.ThemeMode;
import it.unlocksecurity.burp.screenshotmode.model.UrlMode;
import it.unlocksecurity.burp.screenshotmode.model.ViewMode;
import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
import it.unlocksecurity.burp.screenshotmode.render.RuleStatus;
import it.unlocksecurity.burp.screenshotmode.storage.DefaultTemplate;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.IdentityHashMap;
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
import javax.swing.JTextArea;
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
    /** The status line under each rule card, keyed by the rule itself. */
    private final Map<Object, JTextArea> statusLines = new IdentityHashMap<>();
    private int failedRules;
    private boolean updatingHeaders;

    SettingsForm(Settings settings, Runnable onChange, Font mono, boolean wide) {
        super(new BorderLayout());
        this.settings = settings;
        this.mono = mono;
        this.onChange = () -> {
            updateCounts();
            onChange.run();
        };
        setOpaque(false);

        tabs.addTab("Rules", scrollable(rulesTab(wide)));
        tabs.addTab("Headers", headersTab());
        tabs.addTab("Appearance", scrollable(appearanceTab()));
        tabs.setTabComponentAt(RULES, tabTitle("Rules", rulesCount));
        tabs.setTabComponentAt(HEADERS, tabTitle("Headers", headersCount));
        updateCounts();
        add(tabs, BorderLayout.CENTER);
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
        int headers =
            settings.headersToHide.both.size() +
            settings.headersToHide.request.size() +
            settings.headersToHide.response.size();
        headersCount.setText(String.valueOf(headers));
    }

    private JComponent rulesTab(boolean wide) {
        JPanel highlights = RuleCards.section(
            "Highlights",
            null,
            "No highlights yet. Add one with + or right-click selected text in the preview.",
            settings.highlights,
            () -> HighlightRule.create("", RuleTarget.REQUEST, MatchMode.REGEX),
            (rule, change, delete) -> RuleCards.highlightCard(rule, change, delete, mono, statusLine(rule)),
            onChange
        );
        JPanel redactions = RuleCards.section(
            "Redactions",
            null,
            "No redactions yet. Add one with + or right-click selected text in the preview.",
            settings.redactions,
            // Replace, like right-click › Redact: blur and opaque only hide the text on screen.
            () -> RedactionRule.create("", RuleTarget.REQUEST, RedactionMode.REPLACE, MatchMode.REGEX),
            (rule, change, delete) -> RuleCards.redactionCard(rule, change, delete, mono, statusLine(rule)),
            onChange
        );
        if (wide) {
            JPanel columns = new JPanel(new GridLayout(1, 2, 24, 0));
            columns.setOpaque(false);
            columns.add(highlights);
            columns.add(redactions);
            return columns;
        }
        Widgets.Stack stack = new Widgets.Stack(20, false);
        stack.addItem(highlights);
        stack.addItem(redactions);
        return stack;
    }

    /** A rule card's status line, empty and hidden until {@link #showRuleStatuses} fills it. */
    private JTextArea statusLine(Object rule) {
        JTextArea line = Widgets.wrappingHint("", 260);
        line.setVisible(false);
        statusLines.put(rule, line);
        return line;
    }

    /**
     * Shows under each rule card what it did in the screenshot: its matches, or why it did not fully apply. Rules
     * without a status (as in the template editor, which renders no message) show nothing.
     */
    void showRuleStatuses(Map<Object, RuleStatus> statuses) {
        statusLines.forEach((rule, line) -> {
            RuleStatus status = statuses.get(rule);
            line.setVisible(status != null);
            if (status != null) {
                line.setText(describe(status));
                line.setForeground(status.failed() ? Widgets.errorColor() : Widgets.mutedColor());
            }
        });
        failedRules = (int) Stream.concat(settings.highlights.stream(), settings.redactions.stream())
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

    /** One list per target, picked with "Hide in"; each option shows how many headers its list hides. */
    private JComponent headersTab() {
        JTextArea text = new JTextArea(12, 20);
        text.setFont(mono);
        HeaderHideTarget[] target = { HeaderHideTarget.BOTH };
        Runnable load = () -> {
            updatingHeaders = true;
            text.setText(String.join("\n", settings.headersToHide.get(target[0])));
            text.setCaretPosition(0);
            updatingHeaders = false;
        };
        SegmentedControl<HeaderHideTarget> picker = new SegmentedControl<>(
            HeaderHideTarget.values(),
            target[0],
            this::headerListLabel,
            value -> {
                target[0] = value;
                load.run();
            }
        );
        picker.setToolTipText("Which list to edit: headers hidden in both messages, or only in one of them");
        Runnable relabelPicker = () -> {
            for (HeaderHideTarget value : HeaderHideTarget.values()) {
                picker.setLabel(value, headerListLabel(value));
            }
        };
        load.run();
        Widgets.onTextChange(text, value -> {
            if (updatingHeaders) {
                return;
            }
            List<String> headers = Arrays.stream(value.split("\n"))
                .map(String::trim)
                .filter(header -> !header.isEmpty())
                .toList();
            settings.headersToHide.set(target[0], headers);
            relabelPicker.run();
            onChange.run();
        });

        JButton restore = Widgets.button("Restore defaults");
        restore.putClientProperty("JButton.buttonType", "borderless");
        restore.setToolTipText("Replace all three lists with the default list of noisy headers");
        restore.addActionListener(event -> {
            settings.headersToHide = DefaultTemplate.settings().headersToHide;
            load.run();
            relabelPicker.run();
            onChange.run();
        });

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        top.setOpaque(false);
        top.add(Widgets.title("Hide in"));
        top.add(picker);

        // Wraps the button under the hint when the sidebar is narrow, instead of cutting the hint.
        JPanel bottom = Widgets.row(Widgets.hint("One header per line. Prefix with ! to always show it."), restore);

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        panel.add(top, BorderLayout.NORTH);
        panel.add(new JScrollPane(text), BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private String headerListLabel(HeaderHideTarget target) {
        return target.label() + " " + settings.headersToHide.get(target).size();
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
