package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.HighlightMode;
import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.render.RuleEngine;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

/** Editors for highlight and redaction rules. Edits mutate the rule in place and then call {@code onChange}. */
final class RuleCards {

    private RuleCards() {}

    /** A titled list of rule cards with an "add" button (port of HighlightRulesList / RedactionRulesList). */
    static <R> JPanel section(
        String title,
        String hint,
        String emptyText,
        List<R> rules,
        Supplier<R> factory,
        CardFactory<R> cardFactory,
        Runnable onChange
    ) {
        Widgets.Stack list = new Widgets.Stack(8, false);
        Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            list.clear();
            if (rules.isEmpty()) {
                list.addItem(Widgets.wrappingHint(emptyText, 260));
            }
            for (R rule : rules) {
                list.addItem(
                    cardFactory.create(rule, onChange, () -> {
                        rules.remove(rule);
                        rebuild[0].run();
                        onChange.run();
                    })
                );
            }
            list.revalidate();
            list.repaint();
        };
        rebuild[0].run();

        JButton add = Widgets.iconButton(Widgets.Glyph.PLUS, "Add rule", () -> {
            // Caido appends new rules; we prepend so the new card shows up right below the button instead of at the
            // bottom of a possibly long, scrolled-away list.
            rules.addFirst(factory.get());
            rebuild[0].run();
            onChange.run();
        });
        return Widgets.section(title, hint, add, list);
    }

    interface CardFactory<R> {
        JComponent create(R rule, Runnable onChange, Runnable onDelete);
    }

    /**
     * Two rows: the pattern (with its regex toggle) and delete; then where and how it applies. {@code status}, below
     * them, shows what the rule did in the screenshot.
     */
    static JComponent highlightCard(
        HighlightRule rule,
        Runnable onChange,
        Runnable onDelete,
        Font mono,
        JComponent status
    ) {
        JTextField pattern = patternField(rule.regex, rule.matchMode, mono);
        Widgets.onTextChange(pattern, text -> {
            rule.regex = text;
            validate(pattern, rule.regex, rule.matchMode);
            onChange.run();
        });
        JToggleButton regex = regexToggle(rule.matchMode, value -> {
            rule.matchMode = value;
            setPlaceholder(pattern, value);
            validate(pattern, rule.regex, rule.matchMode);
            onChange.run();
        });

        JComboBox<RuleTarget> target = combo(RuleTarget.values(), rule.target, value -> {
            rule.target = value;
            onChange.run();
        });
        JComboBox<HighlightMode> mode = combo(HighlightMode.values(), rule.mode, value -> {
            rule.mode = value;
            onChange.run();
        });
        JButton color = Widgets.colorButton(rule.color, value -> {
            rule.color = value;
            onChange.run();
        });

        JPanel card = Widgets.card(new BorderLayout(0, 6));
        card.add(patternRow(pattern, regex, onDelete), BorderLayout.NORTH);
        card.add(Widgets.row(target, mode, color), BorderLayout.CENTER);
        card.add(status, BorderLayout.SOUTH);
        return card;
    }

    /** Like {@link #highlightCard}, plus a capture group row shown only while the regex has groups. */
    static JComponent redactionCard(
        RedactionRule rule,
        Runnable onChange,
        Runnable onDelete,
        Font mono,
        JComponent status
    ) {
        JTextField pattern = patternField(rule.regex, rule.matchMode, mono);
        JPanel groups = new JPanel(new WrapLayout(6, 0));
        groups.setOpaque(false);
        AtomicReference<List<String>> groupLabels = new AtomicReference<>(
            RuleEngine.groupLabels(rule.regex, rule.matchMode)
        );
        Runnable rebuildGroups = () -> rebuildGroups(groups, rule, groupLabels.get(), onChange);
        rebuildGroups.run();

        // Port of handleRegexChange: auto-select capture groups as they appear in the pattern.
        Consumer<String> patternChanged = text -> {
            List<String> newLabels = RuleEngine.groupLabels(text, rule.matchMode);
            int oldCount = groupLabels.get().size();
            int newCount = newLabels.size();
            rule.regex = text;
            if (newCount == 0) {
                rule.useCaptureGroups = false;
                rule.selectedGroups = new ArrayList<>();
            } else if (!rule.useCaptureGroups) {
                rule.useCaptureGroups = true;
                rule.selectedGroups = allGroups(1, newCount);
            } else {
                List<Integer> selected = new ArrayList<>(
                    rule.selectedGroups
                        .stream()
                        .filter(g -> g <= newCount)
                        .toList()
                );
                selected.addAll(allGroups(oldCount + 1, newCount));
                rule.selectedGroups = selected;
            }
            // Also on a renamed group, whose checkbox is labelled with the name.
            if (!newLabels.equals(groupLabels.get())) {
                groupLabels.set(newLabels);
                rebuildGroups.run();
            }
            validate(pattern, rule.regex, rule.matchMode);
            onChange.run();
        };
        Widgets.onTextChange(pattern, patternChanged);
        JToggleButton regex = regexToggle(rule.matchMode, value -> {
            rule.matchMode = value;
            setPlaceholder(pattern, value);
            patternChanged.accept(rule.regex);
        });

        JComboBox<RuleTarget> target = combo(RuleTarget.values(), rule.target, value -> {
            rule.target = value;
            onChange.run();
        });
        JButton color = Widgets.colorButton(rule.color, value -> {
            rule.color = value;
            onChange.run();
        });
        JTextField replacement = new JTextField(rule.replacementText, 11);
        replacement.setFont(mono);
        replacement.setCaretPosition(0);
        Widgets.placeholder(replacement, RedactionRule.DEFAULT_REPLACEMENT);
        Widgets.onTextChange(replacement, text -> {
            rule.replacementText = text;
            onChange.run();
        });
        Runnable updateModeFields = () -> {
            color.setVisible(rule.mode == RedactionMode.OPAQUE);
            replacement.setVisible(rule.mode == RedactionMode.REPLACE);
        };
        updateModeFields.run();

        JPanel modeRow = Widgets.row();
        JComboBox<RedactionMode> mode = combo(RedactionMode.values(), rule.mode, value -> {
            rule.mode = value;
            updateModeFields.run();
            modeRow.revalidate();
            onChange.run();
        });
        modeRow.add(target);
        modeRow.add(mode);
        modeRow.add(color);
        modeRow.add(replacement);

        JPanel lower = new JPanel(new BorderLayout(0, 6));
        lower.setOpaque(false);
        lower.add(modeRow, BorderLayout.NORTH);
        lower.add(groups, BorderLayout.CENTER);

        JPanel card = Widgets.card(new BorderLayout(0, 6));
        card.add(patternRow(pattern, regex, onDelete), BorderLayout.NORTH);
        card.add(lower, BorderLayout.CENTER);
        card.add(status, BorderLayout.SOUTH);
        return card;
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

    private static JPanel patternRow(JTextField pattern, JToggleButton regex, Runnable onDelete) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setOpaque(false);
        row.add(Widgets.withTrailing(pattern, regex), BorderLayout.CENTER);
        row.add(Widgets.flatIconButton(Widgets.Glyph.TRASH, "Delete rule", onDelete), BorderLayout.EAST);
        return row;
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
}
