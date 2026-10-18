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
import it.unlocksecurity.burp.screenshotmode.model.ViewMode;
import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.Arrays;
import java.util.List;
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
    private final JLabel rulesCount = badge();
    private final JLabel headersCount = badge();
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
        rulesCount.setText(String.valueOf(settings.highlights.size() + settings.redactions.size()));
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
            (rule, change, delete) -> RuleCards.highlightCard(rule, change, delete, mono),
            onChange
        );
        JPanel redactions = RuleCards.section(
            "Redactions",
            null,
            "No redactions yet. Add one with + or right-click selected text in the preview.",
            settings.redactions,
            () -> RedactionRule.create("", RuleTarget.REQUEST, RedactionMode.BLUR, MatchMode.REGEX),
            (rule, change, delete) -> RuleCards.redactionCard(rule, change, delete, mono),
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
            settings.headersToHide = Settings.defaults().headersToHide;
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

    /** A neutral count pill for tab titles. */
    private static JLabel badge() {
        JLabel label = new JLabel() {
            @Override
            protected void paintComponent(java.awt.Graphics graphics) {
                java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
                g.setRenderingHint(
                    java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON
                );
                g.setColor(Widgets.themeColor("Colors.palette.mono.3", new java.awt.Color(0xe6e6e6)));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        label.setFont(label.getFont().deriveFont(Font.BOLD, label.getFont().getSize2D() - 2));
        label.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        return label;
    }
}
