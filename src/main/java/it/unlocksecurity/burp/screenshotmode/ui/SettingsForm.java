package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.Disposition;
import it.unlocksecurity.burp.screenshotmode.model.HeaderCatalog;
import it.unlocksecurity.burp.screenshotmode.model.HeightMode;
import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.Rule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.RuleType;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.Template;
import it.unlocksecurity.burp.screenshotmode.model.ThemeMode;
import it.unlocksecurity.burp.screenshotmode.model.UrlMode;
import it.unlocksecurity.burp.screenshotmode.model.ViewMode;
import it.unlocksecurity.burp.screenshotmode.model.Visibility;
import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
import it.unlocksecurity.burp.screenshotmode.render.RuleEngine;
import it.unlocksecurity.burp.screenshotmode.render.RuleStatus;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import it.unlocksecurity.burp.screenshotmode.storage.HeaderCatalogFile;
import it.unlocksecurity.burp.screenshotmode.storage.TemplateCodec;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
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
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Editors for every screenshot setting, mutating the given {@link Settings} in place, in three tabs ordered by how
 * often they are used: Rules, Headers, Appearance. The sidebar of both the screenshot window and the template editor.
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
    /** Above the rules after a jump from the Headers tab, with the way back, or after a deletion, with Undo. */
    private final JPanel notice = new JPanel(new BorderLayout(8, 0)) {
        @Override
        public java.awt.Color getBackground() {
            // Resolved at paint time, so it follows Burp's theme switches.
            return Widgets.themeColor("Table.selectionBackground", new java.awt.Color(0xcadaf0));
        }
    };
    private final JLabel noticeText = new JLabel();
    private final JButton noticeAction = Widgets.button("");
    private Runnable noticeRun = () -> {};
    /** Whether the notice offers Undo, which would undo another change once the settings change again. */
    private boolean undoNotice;
    /** Undoes the last change, or {@code null} where there is no undo. */
    private Runnable undo;
    private int failedRules;
    /** The built-in template the settings are based on, and what Restore defaults does in the Rules tab. */
    private final JLabel rulesBasis = Widgets.hint("");
    private final JButton restoreRules = Widgets.button("Restore defaults…");
    private final JLabel appearanceBasis = Widgets.hint("");
    /** The Width and Height rows' controls, built again with the Appearance tab. */
    private JComboBox<WidthMode> widthMode;
    private JSpinner widthPixels;
    private JComboBox<HeightMode> heightMode;
    private JSpinner heightPixels;
    private boolean showingSize;
    private final JButton restoreAppearance = Widgets.button("Restore defaults");
    private Chooser chooser = (title, message, action, safe) -> Widgets.choose(this, title, message, action, safe);

    /** Asks a two-option question, as {@link Widgets#choose}; replaced in tests. */
    interface Chooser {
        String choose(String title, String message, String action, String safe);
    }

    private static final String ADD_MISSING = "Add missing defaults";
    private static final String REPLACE = "Replace with defaults";

    SettingsForm(Settings settings, Runnable onChange, Font mono) {
        this(settings, onChange, mono, null);
    }

    /** @param message the headers of the message on screen, or {@code null} for none */
    SettingsForm(Settings settings, Runnable onChange, Font mono, HeadersTab.InMessage message) {
        super(new BorderLayout());
        this.settings = settings;
        this.mono = mono;
        this.onChange = () -> {
            if (undoNotice) {
                notice.setVisible(false);
            }
            updateCounts();
            updateRestore();
            onChange.run();
        };
        setOpaque(false);

        restoreRules.addActionListener(event -> restoreRules());
        restoreAppearance.addActionListener(event -> restoreAppearance());
        tabs.addTab("Rules", withFooter(scrollable(rulesTab()), rulesBasis, restoreRules));
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
                    showBackNotice(text);
                }

                @Override
                public void open(Rule rule, String text) {
                    showRule(rule);
                    showBackNotice(text);
                }
            }
        );
        tabs.addTab("Headers", headers);
        tabs.addTab("Appearance", appearancePanel());
        tabs.setTabComponentAt(RULES, tabTitle("Rules", rulesCount));
        tabs.setTabComponentAt(HEADERS, tabTitle("Headers", headersCount));
        updateCounts();
        updateRestore();
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

    /** Takes the headers of the message on screen again, after it was edited. */
    void setMessage(HeadersTab.InMessage message) {
        headers.setMessage(message);
        updateCounts();
    }

    /**
     * The actions of the preview's right-click menu. New rules go first, like the ones added with +, and open; when a
     * rule already redacts or highlights the header, that rule is shown instead of adding a duplicate.
     */
    void onSelectionAction(MessagePane.SelectionAction action, String selected, RuleTarget target) {
        switch (action) {
            case HIGHLIGHT -> highlightList.addFirst(HighlightRule.create(selected, target, MatchMode.STRING), false);
            case REDACT -> redactionList.addFirst(
                RedactionRule.create(selected, target, RedactionMode.REPLACE, MatchMode.STRING),
                false
            );
            case HIDE_HEADER -> {
                settings.headers.set(selected, Visibility.HIDE, HeaderCatalogFile.bundled());
                headers.reload();
                onChange.run();
            }
            case REDACT_HEADER, HIGHLIGHT_HEADER -> {
                boolean redaction = action == MessagePane.SelectionAction.REDACT_HEADER;
                Rule existing = headers.existingRule(List.of(selected), redaction);
                if (existing != null) {
                    showRule(existing);
                } else if (redaction) {
                    redactionList.addFirst(RedactionRule.forHeaders(List.of(selected), RedactionMode.REPLACE), false);
                } else {
                    highlightList.addFirst(HighlightRule.forHeaders(List.of(selected)), false);
                }
            }
        }
    }

    /** Takes what the rules changed in the header lines of the message on screen. */
    void showHeaderHits(Map<RuleTarget, Map<String, java.util.Set<Rule>>> hits) {
        headers.showHits(hits);
    }

    JButton noticeAction() {
        return noticeAction;
    }

    /** "‹ Back to Headers" returns to the header the user came from, still selected there. */
    private void showBackNotice(String text) {
        showNotice(text, "‹ Back to Headers", null, false, () -> {
            tabs.setSelectedIndex(HEADERS);
            headers.table().requestFocusInWindow();
        });
    }

    /** After a rule was deleted with its trash button, where the settings can be undone. */
    private void showDeletedNotice() {
        if (undo != null) {
            showNotice("Rule deleted.", "Undo", "Undo (" + Widgets.shortcut(KeyEvent.VK_Z) + ")", true, undo);
        }
    }

    private void showNotice(String text, String action, String tooltip, boolean undoes, Runnable run) {
        noticeText.setText(text);
        noticeAction.setText(action);
        noticeAction.setToolTipText(tooltip);
        noticeRun = run;
        undoNotice = undoes;
        notice.setVisible(true);
        notice.revalidate();
        // The first layout wraps the row for the width it had while hidden: lay it out again at its real width.
        SwingUtilities.invokeLater(notice::revalidate);
    }

    /** Offers Undo after a deletion: the settings have an undo history. */
    void setUndo(Runnable undo) {
        this.undo = undo;
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

    private JComponent rulesTab() {
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
        highlightList.setDeleteListener(this::showDeletedNotice);
        redactionList.setDeleteListener(this::showDeletedNotice);
        buildNotice();
        Widgets.Stack stack = new Widgets.Stack(20, false);
        stack.addItem(notice());
        stack.addItem(highlightList);
        stack.addItem(redactionList);
        return stack;
    }

    private void buildNotice() {
        noticeAction.putClientProperty("JButton.buttonType", "borderless");
        noticeAction.addActionListener(event -> noticeRun.run());
        notice.setOpaque(true);
        notice.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 6));
        // The link moves under the text when the sidebar is too narrow for both.
        notice.add(Widgets.row(noticeText, noticeAction), BorderLayout.CENTER);
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
     * Shows the settings again after undo or redo changed them under the form, and where they changed: their tab, if
     * the open one didn't change, and the rule that changed or came back, open.
     *
     * @param before the settings as they were
     */
    void reload(Settings before) {
        String open = expansion.open() == null ? null : expansion.open().id;
        highlightList.reload();
        redactionList.reload();
        headers.reload();
        int selected = tabs.getSelectedIndex();
        // The controls show the settings they were built with: build them again.
        tabs.setComponentAt(APPEARANCE, appearancePanel());
        notice.setVisible(false);
        updateCounts();
        updateRestore();

        Rule changed = ruleChangedSince(before);
        boolean rules =
            changed != null ||
            settings.highlights.size() != before.highlights.size() ||
            settings.redactions.size() != before.redactions.size();
        Settings sameHeaders = settings.copy();
        sameHeaders.headers = before.headers;
        List<Integer> tabsChanged = new ArrayList<>();
        if (rules) {
            tabsChanged.add(RULES);
        }
        if (!TemplateCodec.sameSettings(settings, sameHeaders)) {
            tabsChanged.add(HEADERS);
        }
        if (settings.appearanceChanges(before) > 0) {
            tabsChanged.add(APPEARANCE);
        }
        tabs.setSelectedIndex(
            tabsChanged.isEmpty() || tabsChanged.contains(selected) ? selected : tabsChanged.getFirst()
        );

        if (changed != null && tabs.getSelectedIndex() == RULES) {
            showRule(changed);
        } else if (changed != null) {
            expand(changed);
        } else {
            // The rule open before, if it is still there: its row was built again.
            Stream.concat(settings.highlights.stream(), settings.redactions.stream())
                .filter(rule -> rule.id.equals(open))
                .findFirst()
                .ifPresent(this::expand);
        }
        revalidate();
        repaint();
    }

    /** The first rule that {@code before} doesn't have, or has with other settings. */
    private Rule ruleChangedSince(Settings before) {
        Map<String, Rule> old = new HashMap<>();
        Stream.concat(before.highlights.stream(), before.redactions.stream()).forEach(rule -> old.put(rule.id, rule));
        return Stream.concat(settings.highlights.stream(), settings.redactions.stream())
            .filter(rule -> !old.containsKey(rule.id) || !sameRule(rule, old.get(rule.id)))
            .findFirst()
            .orElse(null);
    }

    private static boolean sameRule(Rule a, Rule b) {
        return a.getClass() == b.getClass() && TemplateCodec.sameSettings(holding(a), holding(b));
    }

    private static Settings holding(Rule rule) {
        Settings settings = new Settings();
        if (rule instanceof HighlightRule highlight) {
            settings.highlights.add(highlight);
        } else {
            settings.redactions.add((RedactionRule) rule);
        }
        return settings;
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
            .filter(rule -> {
                RuleStatus status = statuses.get(rule);
                return (
                    (status != null && status.failed()) ||
                    (rule instanceof RedactionRule redaction && RuleEngine.redactsNothing(redaction))
                );
            })
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

    /** The built-in template the settings are based on: a new copy, free to edit. */
    private Template parent() {
        return BuiltInTemplates.byId(settings.basedOn);
    }

    /** A tab's content with, pinned below it, which built-in template it follows and its Restore defaults. */
    private static JComponent withFooter(JComponent content, JLabel basis, JButton restore) {
        restore.putClientProperty("JButton.buttonType", "borderless");
        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(6, 14, 8, 8));
        footer.add(basis, BorderLayout.CENTER);
        footer.add(restore, BorderLayout.EAST);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.add(content, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent appearancePanel() {
        return withFooter(scrollable(appearanceTab()), appearanceBasis, restoreAppearance);
    }

    /** Says what Restore defaults would change in the Rules and Appearance tabs, and enables it only if anything. */
    private void updateRestore() {
        Template parent = parent();
        int total = parent.settings.highlights.size() + parent.settings.redactions.size();
        int missing = missingRules(parent.settings);
        rulesBasis.setText(
            "Based on " +
                parent.name +
                " · " +
                (total == 0
                    ? "no rules"
                    : missing == 0
                      ? "no rules missing"
                      : missing == 1
                        ? "1 rule missing"
                        : missing + " rules missing")
        );
        Settings same = settings.copy();
        same.highlights = parent.settings.highlights;
        same.redactions = parent.settings.redactions;
        restoreRules.setEnabled(!TemplateCodec.sameSettings(settings, same));
        restoreRules.setToolTipText("Add back the rules of " + parent.name + ", or replace yours with them");

        int changes = settings.appearanceChanges(parent.settings);
        appearanceBasis.setText(
            "Based on " +
                parent.name +
                " · " +
                (changes == 0 ? "no changes" : changes == 1 ? "1 change" : changes + " changes")
        );
        restoreAppearance.setEnabled(changes > 0);
        restoreAppearance.setToolTipText("Use the appearance of " + parent.name + " again");
    }

    private int missingRules(Settings parent) {
        return (
            Settings.missing(settings.highlights, parent.highlights).size() +
            Settings.missing(settings.redactions, parent.redactions).size()
        );
    }

    /**
     * Adds back the built-in template's rules that are missing, or replaces all the rules with its own, as the user
     * chooses. Adding deletes nothing, so it is the default when anything is missing.
     */
    private void restoreRules() {
        Template parent = parent();
        List<HighlightRule> highlights = Settings.missing(settings.highlights, parent.settings.highlights);
        List<RedactionRule> redactions = Settings.missing(settings.redactions, parent.settings.redactions);
        int missing = highlights.size() + redactions.size();
        int total = parent.settings.highlights.size() + parent.settings.redactions.size();
        String name = "\"" + parent.name + "\"";
        String choice;
        if (missing > 0) {
            choice = chooser.choose(
                "Restore Defaults",
                (missing == 1 ? "1 rule of " + name + " is" : missing + " rules of " + name + " are") +
                    " missing from this template.\nAdd " +
                    (missing == 1 ? "it" : "them") +
                    " back, or replace all your rules with the " +
                    (total == 1 ? "rule" : total + " rules") +
                    " of " +
                    name +
                    "?",
                REPLACE,
                ADD_MISSING
            );
        } else {
            choice = chooser.choose(
                "Restore Defaults",
                total == 0
                    ? name + " has no rules: replacing removes all your rules."
                    : "Replace your rules with the " +
                          (total == 1 ? "rule" : total + " rules") +
                          " of " +
                          name +
                          "?\nYour own rules and your changes to the default ones will be lost.",
                REPLACE,
                "Cancel"
            );
        }
        if (ADD_MISSING.equals(choice)) {
            settings.highlights.addAll(highlights);
            settings.redactions.addAll(redactions);
        } else if (REPLACE.equals(choice)) {
            settings.highlights.clear();
            settings.highlights.addAll(parent.settings.highlights);
            settings.redactions.clear();
            settings.redactions.addAll(parent.settings.redactions);
        } else {
            return;
        }
        highlightList.reload();
        redactionList.reload();
        onChange.run();
    }

    /** Takes the built-in template's appearance again, after asking. */
    private void restoreAppearance() {
        Template parent = parent();
        int changes = settings.appearanceChanges(parent.settings);
        String question =
            "Use the appearance of \"" +
            parent.name +
            "\" again?\nYour " +
            (changes == 1 ? "change" : changes + " changes") +
            " to width, layout, theme, URL bar, body and footer will be lost.";
        if (!"Restore".equals(chooser.choose("Restore Defaults", question, "Restore", "Cancel"))) {
            return;
        }
        settings.copyAppearance(parent.settings);
        // The controls show the settings they were built with: build them again.
        int selected = tabs.getSelectedIndex();
        tabs.setComponentAt(APPEARANCE, appearancePanel());
        tabs.setSelectedIndex(selected);
        onChange.run();
    }

    /** Replaces the Restore defaults dialogs, for tests. */
    void setChooser(Chooser chooser) {
        this.chooser = chooser;
    }

    JButton restoreRulesButton() {
        return restoreRules;
    }

    JButton restoreAppearanceButton() {
        return restoreAppearance;
    }

    JLabel rulesBasis() {
        return rulesBasis;
    }

    JLabel appearanceBasis() {
        return appearanceBasis;
    }

    /** Settings that change how the screenshot looks, as a label / control grid. */
    /**
     * Two groups in one grid, so their labels share a column: how the image looks, then what it shows, in the order
     * it appears in the screenshot.
     */
    private JComponent appearanceTab() {
        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        int row = 0;

        addHeading(grid, row++, "Image");
        addRow(
            grid,
            row++,
            "Width",
            widthControl(),
            "Width of the captured image. Dragging the handle on the right of the screenshot sets a custom width.",
            true
        );
        addRow(
            grid,
            row++,
            "Height",
            heightControl(),
            "Height of the captured image. Dragging the handle below the screenshot sets a custom height.",
            false
        );

        SegmentedControl<Disposition> layout = Widgets.segmented(Disposition.values(), settings.disposition, value -> {
            settings.disposition = value;
            onChange.run();
        });
        addRow(grid, row++, "Layout", layout, "Request and response side by side, or one above the other", false);

        SegmentedControl<ThemeMode> theme = Widgets.segmented(ThemeMode.values(), settings.theme, value -> {
            settings.theme = value;
            onChange.run();
        });
        addRow(grid, row++, "Theme", theme, "Colors of the screenshot", false);

        addHeading(grid, row++, "Content");
        SegmentedControl<UrlMode> url = Widgets.segmented(UrlMode.values(), settings.url, value -> {
            settings.url = value;
            onChange.run();
        });
        addRow(grid, row++, "URL bar", url, null, true);

        SegmentedControl<ViewMode> view = Widgets.segmented(ViewMode.values(), settings.view, value -> {
            settings.view = value;
            onChange.run();
        });
        addRow(grid, row++, "Body", view, "Pretty prints JSON, XML and HTML bodies", false);

        addRow(grid, row, "Footer", footerControl(), "Metadata shown below the messages", false);

        Widgets.Stack stack = new Widgets.Stack(0, false);
        stack.addItem(grid);
        return stack;
    }

    /** A group title across both columns, like the section titles of the Rules tab. */
    private static void addHeading(JPanel grid, int row, String text) {
        GridBagConstraints at = new GridBagConstraints();
        at.gridx = 0;
        at.gridy = row;
        at.gridwidth = 2;
        at.anchor = GridBagConstraints.LINE_START;
        at.insets = new Insets(row == 0 ? 0 : 20, 0, 0, 0);
        grid.add(Widgets.title(text), at);
    }

    private static void addRow(JPanel grid, int row, String label, JComponent control, String tooltip, boolean first) {
        int top = first ? 8 : 12;
        GridBagConstraints labelAt = new GridBagConstraints();
        labelAt.gridx = 0;
        labelAt.gridy = row;
        labelAt.anchor = GridBagConstraints.LINE_START;
        labelAt.insets = new Insets(top, 0, 0, 16);
        JLabel title = new JLabel(label);
        title.setToolTipText(tooltip);
        title.setLabelFor(control);
        grid.add(title, labelAt);

        GridBagConstraints controlAt = new GridBagConstraints();
        controlAt.gridx = 1;
        controlAt.gridy = row;
        controlAt.weightx = 1;
        controlAt.anchor = GridBagConstraints.LINE_START;
        controlAt.insets = new Insets(top, 0, 0, 0);
        control.setToolTipText(tooltip);
        grid.add(control, controlAt);
    }

    private JComponent widthControl() {
        JSpinner pixels = new JSpinner(
            new SpinnerNumberModel(settings.widthPixels, Settings.MIN_PIXEL_WIDTH, Settings.MAX_PIXEL_WIDTH, 10)
        );
        JComboBox<WidthMode> mode = new JComboBox<>(WidthMode.values());
        JPanel row = flush(mode, pixels);
        widthMode = mode;
        widthPixels = pixels;
        showSize();
        pixels.addChangeListener(event -> {
            if (!showingSize) {
                settings.widthPixels = (Integer) pixels.getValue();
                onChange.run();
            }
        });
        mode.addActionListener(event -> {
            if (!showingSize) {
                settings.widthMode = (WidthMode) mode.getSelectedItem();
                pixels.setVisible(settings.widthMode == WidthMode.PIXEL);
                row.revalidate();
                onChange.run();
            }
        });
        return row;
    }

    private JComponent heightControl() {
        JSpinner pixels = new JSpinner(
            new SpinnerNumberModel(settings.heightPixels, Settings.MIN_PIXEL_HEIGHT, Settings.MAX_PIXEL_HEIGHT, 10)
        );
        JComboBox<HeightMode> mode = new JComboBox<>(HeightMode.values());
        JPanel row = flush(mode, pixels);
        heightMode = mode;
        heightPixels = pixels;
        showSize();
        pixels.addChangeListener(event -> {
            if (!showingSize) {
                settings.heightPixels = (Integer) pixels.getValue();
                onChange.run();
            }
        });
        mode.addActionListener(event -> {
            if (!showingSize) {
                settings.heightMode = (HeightMode) mode.getSelectedItem();
                pixels.setVisible(settings.heightMode == HeightMode.PIXEL);
                row.revalidate();
                onChange.run();
            }
        });
        return row;
    }

    /**
     * Shows a width or height set outside the form, by dragging a handle beside the screenshot, without a change of
     * its own.
     */
    void showSize() {
        showingSize = true;
        try {
            if (widthMode != null) {
                widthMode.setSelectedItem(settings.widthMode);
                widthPixels.setValue(settings.widthPixels);
                widthPixels.setVisible(settings.widthMode == WidthMode.PIXEL);
                widthPixels.getParent().revalidate();
            }
            if (heightMode != null) {
                heightMode.setSelectedItem(settings.heightMode);
                heightPixels.setValue(settings.heightPixels);
                heightPixels.setVisible(settings.heightMode == HeightMode.PIXEL);
                heightPixels.getParent().revalidate();
            }
        } finally {
            showingSize = false;
        }
        updateRestore();
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
