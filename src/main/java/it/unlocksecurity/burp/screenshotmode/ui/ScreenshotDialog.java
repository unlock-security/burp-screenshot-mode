package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.HeightMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.Template;
import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
import it.unlocksecurity.burp.screenshotmode.render.MessageData;
import it.unlocksecurity.burp.screenshotmode.render.MessageText;
import it.unlocksecurity.burp.screenshotmode.render.Palette;
import it.unlocksecurity.burp.screenshotmode.render.RuleEngine;
import it.unlocksecurity.burp.screenshotmode.render.RuleStatus;
import it.unlocksecurity.burp.screenshotmode.storage.TemplateCodec;
import it.unlocksecurity.burp.screenshotmode.storage.TemplateStore;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;

/** The screenshot window: settings on the left, live preview of the captured content on the right. */
final class ScreenshotDialog extends JDialog {

    private static final String LAST_DIRECTORY_KEY = "screenshot-mode.lastDirectory";
    /** Where the window was and how wide its sidebar, when it was last closed: "x,y,width,height" and pixels. */
    private static final String BOUNDS_KEY = "screenshot-mode.window";
    private static final String SIDEBAR_KEY = "screenshot-mode.sidebarWidth";
    /** The sidebar's width at first and at its narrowest, at Burp's default font: both grow with a larger one. */
    private static final int SIDEBAR_WIDTH = 400;
    private static final int SIDEBAR_MIN_WIDTH = 300;
    /**
     * The smallest window that shows every control at Burp's default font (13 pt), with the sidebar at its narrowest:
     * the toolbars and the preview bar wrap, the Appearance tab fits without scrolling. It grows with a larger font.
     */
    private static final Dimension MIN_SIZE = new Dimension(800, 600);
    private static final int MENU_SHORTCUT = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

    private final Host host;
    private final TemplateStore store;
    private final SessionState state;
    private final MessageData data;
    private final SettingsHistory history;

    private final ContentPanel content = new ContentPanel();
    private final PreviewArea preview = new PreviewArea(content);
    private final JScrollPane previewScroll = new JScrollPane(preview);
    private final JPanel formHolder = new JPanel(new BorderLayout());
    private JSplitPane main;
    private final JComboBox<Template> templateCombo = new JComboBox<>();
    private final JLabel status = new JLabel(" ");
    private final Timer statusTimer = new Timer(4000, event -> status.setText(" "));
    private final Timer renderTimer = new Timer(150, event -> render());
    private final JLabel modified = Widgets.chip("Modified");
    private final JButton revert = Widgets.button("Revert");
    private final JButton update = Widgets.button("Update template");
    private final JLabel output = Widgets.hint("");
    private final JLabel failedRules = new JLabel();
    private final JLabel pageNote = new JLabel();
    private final JLabel tooLarge = new JLabel("⚠ Too large to export: choose a smaller height");
    private final JLabel prettyFailed = new JLabel("⚠ Pretty view failed: showing raw");
    // Not warnings: nothing is wrong with the export. The footer can't show a time Burp doesn't have, and a very long
    // message shows only its start.
    private final JLabel noTimestamp = Widgets.hint("No timestamp available");
    private final JLabel truncated = Widgets.hint("");
    private final Runnable templatesListener = this::reloadTemplates;
    private final KeyEventDispatcher copyShortcut = this::copyShortcut;
    private int selectedTab = SettingsForm.RULES;
    private SettingsForm form;

    private boolean updatingCombo;
    private boolean rendered;
    private Integer lastMaxWidth;

    ScreenshotDialog(Window owner, Host host, TemplateStore store, SessionState state, MessageData data) {
        super(owner, "Screenshot Mode", ModalityType.MODELESS);
        this.host = host;
        this.store = store;
        this.state = state;
        this.data = data;
        history = new SettingsHistory(() -> state.settings);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        statusTimer.setRepeats(false);
        renderTimer.setRepeats(false);

        formHolder.setMinimumSize(new Dimension(Widgets.scaled(SIDEBAR_MIN_WIDTH), 0));
        main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, formHolder, buildPreview());
        main.setDividerLocation(savedSidebarWidth());
        main.setBorder(null);

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(main, BorderLayout.CENTER);
        // The outcome of the last action, in a strip of its own: in the top bar, a long message pushed buttons out.
        status.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Widgets.borderColor()),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)
            )
        );
        root.add(status, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().registerKeyboardAction(
            event -> dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        getRootPane().registerKeyboardAction(
            event -> takeScreenshot(false),
            KeyStroke.getKeyStroke(KeyEvent.VK_S, MENU_SHORTCUT),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        SettingsHistory.bindKeys(getRootPane(), JComponent.WHEN_IN_FOCUSED_WINDOW, () -> undo(false), () -> undo(true));
        // Text components bind Copy themselves, so a window-level key binding would never fire: dispatch it first.
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(copyShortcut);

        content.setSelectionListener((action, text, target) -> form.onSelectionAction(action, text, target));
        content.setSplitRatio(state.splitRatio);
        content.setSplitRatioListener(ratio -> state.splitRatio = ratio);
        content.setErrorListener(host.logError());
        preview.setWidthDragListener((width, done) -> {
            state.settings.widthMode = WidthMode.PIXEL;
            state.settings.widthPixels = width;
            dragged(done);
        });
        preview.setHeightDragListener((height, done) -> {
            state.settings.heightMode = HeightMode.PIXEL;
            state.settings.heightPixels = height;
            dragged(done);
        });
        content.addComponentListener(
            new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    updateOutputLabel();
                }
            }
        );

        store.addListener(templatesListener);
        reloadTemplates();
        rebuildForm();
        render();

        setMinimumSize(minimumWindowSize());
        Rectangle saved = savedBounds();
        if (saved != null) {
            setBounds(saved);
        } else {
            Rectangle area =
                owner != null && owner.isShowing()
                    ? owner.getBounds()
                    : GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            setSize(new Dimension((int) (area.width * 0.95), (int) (area.height * 0.9)));
            setLocationRelativeTo(owner);
        }
    }

    /** The bounds the window was closed with, or {@code null} if none or no longer on a screen (a monitor was unplugged). */
    private Rectangle savedBounds() {
        String value = host.preferences().get(BOUNDS_KEY);
        if (value == null || GraphicsEnvironment.isHeadless()) {
            return null;
        }
        List<Rectangle> screens = new ArrayList<>();
        for (GraphicsDevice screen : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            screens.add(screen.getDefaultConfiguration().getBounds());
        }
        return Widgets.boundsOnScreen(value, screens);
    }

    /** {@link #MIN_SIZE}, scaled with Burp's font size, and never larger than the screen. */
    private static Dimension minimumWindowSize() {
        Dimension size = new Dimension(Widgets.scaled(MIN_SIZE.width), Widgets.scaled(MIN_SIZE.height));
        if (!GraphicsEnvironment.isHeadless()) {
            Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            size.width = Math.min(size.width, screen.width);
            size.height = Math.min(size.height, screen.height);
        }
        return size;
    }

    private int savedSidebarWidth() {
        try {
            String value = host.preferences().get(SIDEBAR_KEY);
            // Saved at a smaller font, it may now be too narrow.
            return value == null
                ? Widgets.scaled(SIDEBAR_WIDTH)
                : Math.max(Widgets.scaled(SIDEBAR_MIN_WIDTH), Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return Widgets.scaled(SIDEBAR_WIDTH);
        }
    }

    @Override
    public void dispose() {
        if (isShowing()) {
            Rectangle bounds = getBounds();
            host.preferences().set(BOUNDS_KEY, bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height);
            host.preferences().set(SIDEBAR_KEY, String.valueOf(main.getDividerLocation()));
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(copyShortcut);
        store.removeListener(templatesListener);
        renderTimer.stop();
        statusTimer.stop();
        super.dispose();
    }

    /**
     * Template controls on the left, the output actions on the right. Copy to Clipboard is the primary action: the
     * usual end of the task is pasting the screenshot into a report.
     */
    private JComponent buildTopBar() {
        templateCombo.addActionListener(event -> {
            if (
                updatingCombo ||
                !(templateCombo.getSelectedItem() instanceof Template template) ||
                template.id.equals(state.templateId)
            ) {
                return;
            }
            if (hasUnsavedChanges() && !confirmSwitch(template)) {
                selectCurrentTemplate();
                return;
            }
            state.templateId = template.id;
            state.settings = template.settings.copy();
            // Undo stays within a template's settings.
            history.reset();
            selectCurrentTemplate();
            rebuildForm();
            render();
        });
        templateCombo.setPrototypeDisplayValue(new Template("", "Client report – redacted tokens", null));
        revert.setToolTipText("Discard the changes and go back to the template's settings");
        revert.addActionListener(event -> resetToTemplate());
        update.addActionListener(event -> updateTemplate());
        JButton saveAs = Widgets.button("Save as new…");
        saveAs.setToolTipText("Create a new template from the current settings");
        saveAs.addActionListener(event -> saveAsNewTemplate());

        JButton save = Widgets.button("Save PNG…");
        save.setToolTipText("Save the screenshot as a PNG file (" + Widgets.shortcut(KeyEvent.VK_S) + ")");
        save.addActionListener(event -> takeScreenshot(false));
        JButton copy = Widgets.primary(Widgets.button("Copy image"));
        copy.setToolTipText("Copy the screenshot to the clipboard (" + Widgets.shortcut(KeyEvent.VK_C) + ")");
        copy.addActionListener(event -> takeScreenshot(true));

        JLabel templateLabel = new JLabel("Template");
        templateLabel.setLabelFor(templateCombo);
        // The template actions wrap to a second row when the window is narrow, instead of disappearing.
        JPanel left = new JPanel(new WrapLayout(6, 4));
        left.add(templateLabel);
        left.add(templateCombo);
        left.add(modified);
        left.add(revert);
        left.add(update);
        left.add(saveAs);

        // Always on the first row, whatever the width.
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        right.add(save);
        right.add(copy);

        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.add(left, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        bar.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Widgets.borderColor()),
                // With the layouts' 4 px gaps above and below the buttons, 8 px as before.
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
            )
        );
        return bar;
    }

    private JComponent buildPreview() {
        previewScroll.setBorder(null);
        previewScroll.getVerticalScrollBar().setUnitIncrement(16);

        output.setToolTipText("Drag the handles on the right of and below the screenshot to change its size");

        JLabel tip = Widgets.hint("Right-click selected text to add a rule");
        tip.setToolTipText(
            "Select text in the request or response, then right-click to highlight it, redact it or hide its header"
        );
        failedRules.setForeground(Widgets.errorColor());
        failedRules.setVisible(false);

        // What is always there on the left, what comes and goes on the right: nothing moves when a warning shows.
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.add(output);
        left.add(tip);
        // One warning per line when even a row of their own is too narrow for them.
        JPanel right = new JPanel(new WrapLayout(12, 0, true));
        for (JLabel warning : new JLabel[] { prettyFailed, pageNote, tooLarge }) {
            warning.setForeground(Widgets.errorColor());
            warning.setVisible(false);
            right.add(warning);
        }
        right.add(failedRules);
        for (JLabel note : new JLabel[] { truncated, noTimestamp }) {
            note.setVisible(false);
            right.add(note);
        }
        JPanel bar = new JPanel(new PreviewBarLayout(left, right));
        bar.add(left);
        bar.add(right);
        bar.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Widgets.borderColor()),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
            )
        );

        JPanel column = new JPanel(new BorderLayout());
        column.add(bar, BorderLayout.NORTH);
        column.add(previewScroll, BorderLayout.CENTER);
        return column;
    }

    /**
     * The size on the left and the warnings on the right of one row. In a window too narrow for both, the warnings take
     * a second row rather than cutting the size off; the hint, at the end of the left side, drops first.
     */
    static final class PreviewBarLayout implements LayoutManager {

        private static final int GAP = 8;
        private final JPanel left;
        private final JPanel right;

        PreviewBarLayout(JPanel left, JPanel right) {
            this.left = left;
            this.right = right;
        }

        /** Whether the size and the warnings fit side by side; the hint doesn't count, it drops first. */
        private boolean oneRow(Container bar, int width) {
            Insets insets = bar.getInsets();
            int sizeWidth = left.getComponentCount() == 0 ? 0 : left.getComponent(0).getPreferredSize().width;
            int leftGaps = ((FlowLayout) left.getLayout()).getHgap() * 2;
            return insets.left + leftGaps + sizeWidth + GAP + right.getPreferredSize().width + insets.right <= width;
        }

        @Override
        public Dimension preferredLayoutSize(Container bar) {
            Insets insets = bar.getInsets();
            // The bar spans its parent: the parent's width is already the new one while the bar's is not.
            int width = bar.getParent() != null ? bar.getParent().getWidth() : bar.getWidth();
            Dimension l = left.getPreferredSize();
            Dimension r = right.getPreferredSize();
            int height = oneRow(bar, width) ? Math.max(l.height, r.height) : l.height + r.height;
            return new Dimension(
                insets.left + l.width + GAP + r.width + insets.right,
                insets.top + height + insets.bottom
            );
        }

        /** As narrow as the size: the rest wraps or drops, so the bar never pushes the sidebar aside. */
        @Override
        public Dimension minimumLayoutSize(Container bar) {
            Insets insets = bar.getInsets();
            int sizeWidth = left.getComponentCount() == 0 ? 0 : left.getComponent(0).getPreferredSize().width;
            int leftGaps = ((FlowLayout) left.getLayout()).getHgap() * 2;
            return new Dimension(insets.left + leftGaps + sizeWidth + insets.right, preferredLayoutSize(bar).height);
        }

        @Override
        public void layoutContainer(Container bar) {
            Insets insets = bar.getInsets();
            int width = bar.getWidth() - insets.left - insets.right;
            int rightWidth = Math.min(right.getPreferredSize().width, width);
            int leftHeight = left.getPreferredSize().height;
            int rightHeight = right.getPreferredSize().height;
            if (oneRow(bar, bar.getWidth())) {
                int height = Math.max(leftHeight, rightHeight);
                left.setBounds(insets.left, insets.top, width - rightWidth - GAP, height);
                right.setBounds(insets.left + width - rightWidth, insets.top, rightWidth, height);
            } else {
                left.setBounds(insets.left, insets.top, width, leftHeight);
                right.setBounds(insets.left + width - rightWidth, insets.top + leftHeight, rightWidth, rightHeight);
            }
        }

        @Override
        public void addLayoutComponent(String name, Component component) {}

        @Override
        public void removeLayoutComponent(Component component) {}
    }

    private void rebuildForm() {
        formHolder.removeAll();
        form = new SettingsForm(
            state.settings,
            () -> {
                history.changed();
                renderTimer.restart();
            },
            host.editorFont().get(),
            HeadersTab.InMessage.of(data)
        );
        form.setUndo(() -> undo(false));
        form.tabs().setSelectedIndex(selectedTab);
        form.tabs().addChangeListener(event -> selectedTab = form.tabs().getSelectedIndex());
        formHolder.add(form, BorderLayout.CENTER);
        formHolder.revalidate();
        formHolder.repaint();
    }

    /** Shows whether the settings differ from the selected template; Revert and Update only make sense if so. */
    private void updateTemplateState() {
        Optional<Template> template = store.getTemplateById(state.templateId);
        boolean differs = template
            .map(selected -> !TemplateCodec.sameSettings(selected.settings, state.settings))
            .orElse(false);
        // Deleted in the suite tab while this window is open: these settings now exist only here.
        boolean deleted = template.isEmpty();
        modified.setText(deleted ? "Template deleted" : "Modified");
        modified.setToolTipText(
            deleted
                ? "The template was deleted. These settings apply to this message only: save them as a new template to keep them."
                : "These settings differ from the template. They apply to this message only."
        );
        modified.setVisible(differs || deleted);
        revert.setEnabled(differs);
        boolean builtIn = template.map(selected -> selected.builtIn).orElse(false);
        update.setEnabled(differs && !builtIn);
        update.setToolTipText(
            builtIn
                ? "Built-in templates can't be changed: use Save as new… to keep these settings"
                : "Save the current settings into the selected template"
        );
    }

    /** Whether switching template would lose settings: changed for this message, or left by a deleted template. */
    private boolean hasUnsavedChanges() {
        return store
            .getTemplateById(state.templateId)
            .map(template -> !TemplateCodec.sameSettings(template.settings, state.settings))
            .orElse(true);
    }

    /**
     * Asks before switching template would discard this message's settings, like the template editor does before
     * discarding its edits. Keep editing is the default.
     *
     * @return whether to switch to {@code target}
     */
    private boolean confirmSwitch(Template target) {
        JButton discard = Widgets.button("Discard and switch");
        // The safe choice is the primary, default one.
        JButton keep = Widgets.primary(Widgets.button("Keep editing"));
        JOptionPane pane = new JOptionPane(
            "You changed the settings of this screenshot.\nSwitching to \"" +
                target.name +
                "\" discards those changes.",
            JOptionPane.WARNING_MESSAGE,
            JOptionPane.DEFAULT_OPTION,
            null,
            new Object[] { discard, keep },
            keep
        );
        // Custom option buttons don't close the pane on their own.
        discard.addActionListener(event -> pane.setValue(discard));
        keep.addActionListener(event -> pane.setValue(keep));
        JDialog dialog = pane.createDialog(this, "Switch Template");
        dialog.getRootPane().setDefaultButton(keep);
        dialog.setVisible(true);
        dialog.dispose();
        return pane.getValue() == discard;
    }

    /** Shows the session's template in the combo box without treating it as a new choice. */
    private void selectCurrentTemplate() {
        updatingCombo = true;
        try {
            templateCombo.setSelectedItem(store.getTemplateById(state.templateId).orElse(null));
        } finally {
            updatingCombo = false;
        }
    }

    /** The size of the image Copy and Save produce: the content as laid out, at the capture scale. */
    private void updateOutputLabel() {
        output.setText(ImageCapture.sizeLabel(content.getWidth(), content.getHeight()));
        String note = ImageCapture.pageNote(state.settings.widthMode, content.getHeight());
        boolean large = ImageCapture.tooLarge(content.getWidth(), content.getHeight());
        pageNote.setText(note == null ? "" : "⚠ " + note);
        // An image too large to export is taller than a page anyway: one warning is enough.
        pageNote.setVisible(note != null && !large);
        tooLarge.setVisible(large);
    }

    /**
     * Copy (⌘C / Ctrl+C) copies the screenshot, unless the focused text component has a selection: then it copies
     * that text as usual, so selecting text in the preview or in a pattern field keeps working.
     */
    private boolean copyShortcut(KeyEvent e) {
        if (
            e.getID() != KeyEvent.KEY_PRESSED ||
            e.getKeyCode() != KeyEvent.VK_C ||
            e.getModifiersEx() != MENU_SHORTCUT ||
            !isActive()
        ) {
            return false;
        }
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focus instanceof JTextComponent text && text.getSelectionStart() != text.getSelectionEnd()) {
            return false;
        }
        e.consume();
        // Later, not inside the key dispatch: the screenshot may first open a confirmation dialog.
        SwingUtilities.invokeLater(() -> takeScreenshot(true));
        return true;
    }

    private void render() {
        renderTimer.stop();
        Settings settings = state.settings;
        Palette palette = host.palette(settings.theme);
        try {
            content.render(
                data,
                settings,
                palette,
                host.editorFont().get(),
                host.displayFont().get(),
                settings.showTimestamp ? ContentPanel.timestamp(data) : null,
                settings.showResponseInfo ? ContentPanel.responseInfo(data) : null
            );
        } catch (RuntimeException | StackOverflowError e) {
            // Runs on the Swing thread: report it in the extension's error log rather than Burp's.
            host.logError().accept("Could not render the screenshot: " + e);
            showStatus("Could not render the screenshot, see the extension's errors", true);
        }
        prettyFailed.setVisible(content.prettyFailed());
        noTimestamp.setVisible(settings.showTimestamp && data.requestTime() == null);
        truncated.setText(truncationNote(content.truncated()));
        truncated.setVisible(!content.truncated().isEmpty());

        // Like Caido, a new content width resets the request/response split.
        Integer maxWidth = settings.maxContentWidth();
        if (rendered && !Objects.equals(maxWidth, lastMaxWidth)) {
            state.splitRatio = 0.5;
            content.setSplitRatio(0.5);
        }
        lastMaxWidth = maxWidth;
        rendered = true;

        preview.setWidth(settings.widthMode, maxWidth);
        preview.setHeight(settings.heightMode, settings.heightPixels);
        form.showRuleStatuses(content.ruleStatuses());
        form.showHeaderHits(content.headerHits());
        int failed = failedRedactions().size();
        failedRules.setText("⚠ " + failed + (failed == 1 ? " redaction rule failed" : " redaction rules failed"));
        failedRules.setVisible(failed > 0);
        updateTemplateState();
        updateOutputLabel();
        preview.setBackground(palette.canvas());
        preview.setHandleColors(palette.canvas(), palette.muted());
        previewScroll.getViewport().setBackground(palette.canvas());
    }

    /**
     * The redaction rules that did not fully apply in the last render, or that redact nothing (only some capture groups,
     * none chosen): what they should hide may show.
     */
    private List<RedactionRule> failedRedactions() {
        return state.settings.redactions
            .stream()
            .filter(rule -> {
                RuleStatus status = content.ruleStatuses().get(rule);
                return (status != null && status.failed()) || RuleEngine.redactsNothing(rule);
            })
            .toList();
    }

    /** Opens the first redaction that failed, or just the Rules tab. */
    private void showRules() {
        List<RedactionRule> failed = failedRedactions();
        if (failed.isEmpty()) {
            form.tabs().setSelectedIndex(SettingsForm.RULES);
        } else {
            form.showRule(failed.getFirst());
        }
    }

    /**
     * Asks before exporting a screenshot in which some redactions did not fully apply: what they should hide may show.
     * The user decides; nothing is blocked.
     */
    private boolean confirmFailedRedactions(boolean toClipboard) {
        if (failedRedactions().isEmpty()) {
            return true;
        }
        String message =
            "One or more redaction rules did not fully apply.\nWhat they should hide may be visible in the screenshot.";
        JButton export = Widgets.button(toClipboard ? "Copy anyway" : "Save anyway");
        // The safe choice is the primary, default one.
        JButton show = Widgets.primary(Widgets.button("Show rules"));
        JOptionPane pane = new JOptionPane(
            message,
            JOptionPane.WARNING_MESSAGE,
            JOptionPane.DEFAULT_OPTION,
            null,
            new Object[] { export, show },
            show
        );
        // Custom option buttons don't close the pane on their own.
        export.addActionListener(event -> pane.setValue(export));
        show.addActionListener(event -> pane.setValue(show));
        JDialog dialog = pane.createDialog(this, toClipboard ? "Copy Screenshot" : "Save Screenshot");
        dialog.getRootPane().setDefaultButton(show);
        dialog.setVisible(true);
        dialog.dispose();
        if (pane.getValue() == show) {
            showRules();
        }
        return pane.getValue() == export;
    }

    private void flushRender() {
        if (renderTimer.isRunning()) {
            render();
        }
        getRootPane().validate();
        preview.settle();
    }

    private void reloadTemplates() {
        updatingCombo = true;
        try {
            templateCombo.removeAllItems();
            Template selected = null;
            for (Template template : store.templates()) {
                templateCombo.addItem(template);
                if (template.id.equals(state.templateId)) {
                    selected = template;
                }
            }
            templateCombo.setSelectedItem(selected);
        } finally {
            updatingCombo = false;
        }
        // The template may have been edited in the suite tab while this window is open.
        updateTemplateState();
    }

    private void resetToTemplate() {
        store.getTemplateById(state.templateId).ifPresentOrElse(
            template -> {
                state.settings = template.settings.copy();
                history.record();
                rebuildForm();
                render();
                showStatus("Reverted to \"" + template.name + "\"", false);
            },
            () -> showStatus("Select a template first", true)
        );
    }

    private void updateTemplate() {
        if (store.updateTemplate(state.templateId, null, state.settings).isPresent()) {
            updateTemplateState();
            showStatus("Template updated", false);
        } else {
            showStatus("Failed to update template: select a template first", true);
        }
    }

    private void saveAsNewTemplate() {
        String name = Widgets.askText(this, "Template name:", "Save As New Template", text ->
            TemplatesTab.nameProblem(store, text, null)
        );
        if (name == null || name.isBlank()) {
            return;
        }
        Template template = store.createTemplate(name.trim(), state.settings);
        state.templateId = template.id;
        reloadTemplates();
        updateTemplateState();
        showStatus("Template \"" + template.name + "\" created", false);
    }

    /** Shows a size set with a drag handle in the form; the whole drag is one step. */
    private void dragged(boolean done) {
        form.showSize();
        if (done) {
            history.record();
            updateTemplateState();
        }
    }

    /** Undoes or redoes the last change to the settings, showing where it was. */
    private void undo(boolean redo) {
        Settings before = redo ? history.redo() : history.undo();
        if (before == null) {
            UIManager.getLookAndFeel().provideErrorFeedback(getRootPane());
            return;
        }
        form.reload(before);
        render();
    }

    private void takeScreenshot(boolean toClipboard) {
        flushRender();
        if (ImageCapture.tooLarge(content.getWidth(), content.getHeight())) {
            // The preview bar says why and what to do.
            showStatus("Too large to export", true);
            return;
        }
        if (!confirmFailedRedactions(toClipboard)) {
            return;
        }
        try {
            BufferedImage image = ImageCapture.render(content);
            if (toClipboard) {
                ImageCapture.copyToClipboard(image);
                showStatus("Image copied", false);
                return;
            }

            File file = chooseFile();
            if (file != null) {
                ImageCapture.save(image, file);
                host.preferences().set(LAST_DIRECTORY_KEY, file.getParent());
                showStatus("Saved " + file.getName(), false);
            }
        } catch (Exception e) {
            host.logError().accept("Screenshot failed: " + e);
            showStatus("Screenshot failed: " + e.getMessage(), true);
        }
    }

    private File chooseFile() {
        String lastDirectory = host.preferences().get(LAST_DIRECTORY_KEY);
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setDialogTitle("Save Screenshot");
        chooser.setFileFilter(new FileNameExtensionFilter("PNG images", "png"));
        MessageView url = content.urlView();
        String name = ImageCapture.fileName(
            data.requestText(),
            url.copyText(0, url.text().length()),
            data.responseText(),
            state.settings.messages
        );
        chooser.setSelectedFile(ImageCapture.freeFile(chooser.getCurrentDirectory(), name));
        return Widgets.chooseSaveFile(this, chooser, "png");
    }

    /** Says which parts were too long to show whole, such as "Response shortened to 300,000 characters". */
    static String truncationNote(Set<RuleTarget> parts) {
        String which =
            parts.contains(RuleTarget.REQUEST) && parts.contains(RuleTarget.RESPONSE)
                ? "Request and response"
                : parts.contains(RuleTarget.REQUEST)
                  ? "Request"
                  : "Response";
        return (
            which + " shortened to " + NumberFormat.getIntegerInstance().format(MessageText.MAX_CHARS) + " characters"
        );
    }

    /** Shows the outcome of an action. A success clears after a few seconds; an error stays until the next status. */
    private void showStatus(String message, boolean error) {
        status.setForeground(error ? Widgets.errorColor() : Widgets.successColor());
        status.setText(message);
        if (error) {
            statusTimer.stop();
        } else {
            statusTimer.restart();
        }
    }
}
