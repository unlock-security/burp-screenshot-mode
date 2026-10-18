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
import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Arrays;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;

/**
 * Editors for every screenshot setting, mutating the given {@link Settings} in place. Used both by the
 * screenshot window sidebar (one column) and by the template editor (two columns).
 */
final class SettingsForm extends JPanel {

    private final Settings settings;
    private final Runnable onChange;
    private final Font mono;
    private boolean updatingHeaders;

    SettingsForm(Settings settings, Runnable onChange, Font mono, boolean wide) {
        super(new BorderLayout());
        this.settings = settings;
        this.onChange = onChange;
        this.mono = mono;
        setOpaque(false);

        Widgets.Stack general = new Widgets.Stack(wide ? 16 : 20, false);
        general.addItem(headersSection(wide ? 14 : 6));
        general.addItem(
            Widgets.section(
                "Layout",
                null,
                null,
                Widgets.row(
                    Widgets.segmented(Disposition.values(), settings.disposition, value -> {
                        settings.disposition = value;
                        onChange.run();
                    })
                )
            )
        );
        general.addItem(widthSection());
        general.addItem(
            Widgets.section(
                "Theme",
                "Colors of the screenshot",
                null,
                Widgets.row(
                    Widgets.segmented(ThemeMode.values(), settings.theme, value -> {
                        settings.theme = value;
                        onChange.run();
                    })
                )
            )
        );
        general.addItem(metadataSection());

        Widgets.Stack rules = new Widgets.Stack(wide ? 16 : 20, false);
        rules.addItem(
            RuleCards.section(
                "Highlights",
                wide ? "Patterns to highlight" : null,
                "No highlights configured",
                settings.highlights,
                () -> HighlightRule.create("", RuleTarget.REQUEST, MatchMode.REGEX),
                (rule, change, delete) -> RuleCards.highlightCard(rule, change, delete, mono),
                onChange
            )
        );
        rules.addItem(
            RuleCards.section(
                "Redactions",
                wide ? "Patterns to redact" : null,
                "No redactions configured",
                settings.redactions,
                () -> RedactionRule.create("", RuleTarget.REQUEST, RedactionMode.BLUR, MatchMode.REGEX),
                (rule, change, delete) -> RuleCards.redactionCard(rule, change, delete, mono),
                onChange
            )
        );

        if (wide) {
            JPanel columns = new JPanel(new GridLayout(1, 2, 24, 0));
            columns.setOpaque(false);
            columns.add(general);
            columns.add(rules);
            add(columns, BorderLayout.NORTH);
        } else {
            Widgets.Stack all = new Widgets.Stack(20, false);
            all.addItem(general);
            all.addItem(rules);
            add(all, BorderLayout.NORTH);
        }
    }

    private JPanel headersSection(int rows) {
        JTextArea text = new JTextArea(rows, 20);
        text.setFont(mono);
        HeaderHideTarget[] target = { HeaderHideTarget.BOTH };
        Runnable load = () -> {
            updatingHeaders = true;
            text.setText(String.join("\n", settings.headersToHide.get(target[0])));
            text.setCaretPosition(0);
            updatingHeaders = false;
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
            onChange.run();
        });

        JPanel targets = Widgets.segmented(HeaderHideTarget.values(), target[0], value -> {
            target[0] = value;
            load.run();
        });

        JPanel content = new JPanel(new BorderLayout(0, 6));
        content.setOpaque(false);
        content.add(targets, BorderLayout.NORTH);
        content.add(new JScrollPane(text), BorderLayout.CENTER);
        return Widgets.section("Headers to Hide", "One header per line. Prefix with ! to always show", null, content);
    }

    private JPanel widthSection() {
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
        JPanel row = Widgets.row(mode, pixels);
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
        return Widgets.section("Content Width", null, null, row);
    }

    private JPanel metadataSection() {
        JCheckBox timestamp = new JCheckBox("Show Timestamp", settings.showTimestamp);
        timestamp.setOpaque(false);
        timestamp.addActionListener(event -> {
            settings.showTimestamp = timestamp.isSelected();
            onChange.run();
        });
        JCheckBox responseInfo = new JCheckBox("Show Response Info", settings.showResponseInfo);
        responseInfo.setOpaque(false);
        responseInfo.addActionListener(event -> {
            settings.showResponseInfo = responseInfo.isSelected();
            onChange.run();
        });
        JPanel content = new JPanel(new GridLayout(2, 1, 0, 2));
        content.setOpaque(false);
        content.add(timestamp);
        content.add(responseInfo);
        return Widgets.section("Metadata", null, null, content);
    }
}
