package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.Template;
import it.unlocksecurity.burp.screenshotmode.render.MessageData;
import it.unlocksecurity.burp.screenshotmode.render.Palette;
import it.unlocksecurity.burp.screenshotmode.render.RuleStatus;
import it.unlocksecurity.burp.screenshotmode.storage.TemplateCodec;
import it.unlocksecurity.burp.screenshotmode.storage.TemplateStore;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Objects;
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
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.JTextComponent;

/** The screenshot window: settings on the left, live preview of the captured content on the right. */
final class ScreenshotDialog extends JDialog {

    private static final String LAST_DIRECTORY_KEY = "screenshot-mode.lastDirectory";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);
    private static final DateTimeFormatter FILE_NAME = DateTimeFormatter.ofPattern(
        "'screenshot-'yyyyMMdd-HHmmss'.png'"
    );
    private static final Color SUCCESS = new Color(0x3fa45b);
    private static final int MENU_SHORTCUT = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();

    private final Host host;
    private final TemplateStore store;
    private final SessionState state;
    private final MessageData data;

    private final ContentPanel content = new ContentPanel();
    private final PreviewArea preview = new PreviewArea(content);
    private final JScrollPane previewScroll = new JScrollPane(preview);
    private final JPanel formHolder = new JPanel(new BorderLayout());
    private final JComboBox<Template> templateCombo = new JComboBox<>();
    private final JLabel status = new JLabel(" ");
    private final Timer statusTimer = new Timer(4000, event -> status.setText(" "));
    private final Timer renderTimer = new Timer(150, event -> render());
    private final JButton resetHeight = Widgets.button("Reset height");
    private final JLabel modified = Widgets.chip("Modified");
    private final JButton revert = Widgets.button("Revert");
    private final JButton update = Widgets.button("Update template");
    private final JLabel output = Widgets.hint("");
    private final JButton failedRules = new JButton();
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
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        statusTimer.setRepeats(false);
        renderTimer.setRepeats(false);

        formHolder.setMinimumSize(new Dimension(300, 0));
        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, formHolder, buildPreview());
        main.setDividerLocation(400);
        main.setBorder(null);

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(main, BorderLayout.CENTER);
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
        // Text components bind Copy themselves, so a window-level key binding would never fire: dispatch it first.
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(copyShortcut);

        content.setSelectionListener(this::onSelectionAction);
        content.setSplitRatio(state.splitRatio);
        content.setSplitRatioListener(ratio -> state.splitRatio = ratio);
        content.setErrorListener(host.logError());
        preview.setCropListener(height -> {
            state.cropHeight = height;
            resetHeight.setEnabled(height != null);
        });
        preview.setCropHeight(state.cropHeight);

        store.addListener(templatesListener);
        reloadTemplates();
        rebuildForm();
        render();

        Rectangle area =
            owner != null && owner.isShowing()
                ? owner.getBounds()
                : GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        setSize(new Dimension((int) (area.width * 0.95), (int) (area.height * 0.9)));
        setLocationRelativeTo(owner);
    }

    @Override
    public void dispose() {
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
                !updatingCombo &&
                templateCombo.getSelectedItem() instanceof Template template &&
                !template.id.equals(state.templateId)
            ) {
                state.templateId = template.id;
                state.settings = template.settings.copy();
                rebuildForm();
                render();
            }
        });
        templateCombo.setPrototypeDisplayValue(new Template("", "Client report – redacted tokens", null));
        modified.setToolTipText("These settings differ from the template. They apply to this message only.");
        revert.setToolTipText("Discard the changes and go back to the template's settings");
        revert.addActionListener(event -> resetToTemplate());
        update.setToolTipText("Save the current settings into the selected template");
        update.addActionListener(event -> updateTemplate());
        JButton saveAs = Widgets.button("Save as new…");
        saveAs.setToolTipText("Create a new template from the current settings");
        saveAs.addActionListener(event -> saveAsNewTemplate());

        JButton save = Widgets.button("Save PNG…");
        save.setToolTipText("Save the screenshot as a PNG file (" + shortcut(KeyEvent.VK_S) + ")");
        save.addActionListener(event -> takeScreenshot(false));
        JButton copy = Widgets.primary(Widgets.button("Copy to clipboard"));
        copy.setToolTipText("Copy the screenshot to the clipboard (" + shortcut(KeyEvent.VK_C) + ")");
        copy.addActionListener(event -> takeScreenshot(true));

        JLabel templateLabel = new JLabel("Template");
        templateLabel.setLabelFor(templateCombo);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        left.add(templateLabel);
        left.add(templateCombo);
        left.add(modified);
        left.add(revert);
        left.add(update);
        left.add(saveAs);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(status);
        right.add(save);
        right.add(copy);

        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.add(left, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
        bar.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Widgets.borderColor()),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
            )
        );
        return bar;
    }

    private JComponent buildPreview() {
        previewScroll.setBorder(null);
        previewScroll.getVerticalScrollBar().setUnitIncrement(16);

        JButton fit = Widgets.button("Fit to content");
        fit.setToolTipText("Crop the height so the whole request and response are visible");
        fit.addActionListener(event -> fitToContent());
        resetHeight.setToolTipText("Fill the window height again");
        resetHeight.addActionListener(event -> preview.setCropHeight(null));
        output.setToolTipText("Drag the handle below the screenshot to crop its height");

        JLabel tip = Widgets.hint("Right-click selected text to add a rule");
        tip.setToolTipText(
            "Select text in the request or response, then right-click to highlight it, redact it or hide its header"
        );
        failedRules.putClientProperty("JButton.buttonType", "borderless");
        failedRules.setForeground(Widgets.errorColor());
        failedRules.setToolTipText("Show the rules");
        failedRules.setVisible(false);
        failedRules.addActionListener(event -> showRules());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.add(output);
        left.add(failedRules);
        left.add(tip);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(fit);
        right.add(resetHeight);

        JPanel bar = new JPanel(new BorderLayout(8, 0));
        bar.add(left, BorderLayout.CENTER);
        bar.add(right, BorderLayout.EAST);
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

    private void rebuildForm() {
        formHolder.removeAll();
        form = new SettingsForm(state.settings, renderTimer::restart, host.editorFont().get(), false);
        form.tabs().setSelectedIndex(selectedTab);
        form.tabs().addChangeListener(event -> selectedTab = form.tabs().getSelectedIndex());
        formHolder.add(form, BorderLayout.CENTER);
        formHolder.revalidate();
        formHolder.repaint();
    }

    /** Shows whether the settings differ from the selected template; Revert and Update only make sense if so. */
    private void updateTemplateState() {
        boolean differs = store
            .getTemplateById(state.templateId)
            .map(template -> !TemplateCodec.sameSettings(template.settings, state.settings))
            .orElse(false);
        modified.setVisible(differs);
        revert.setEnabled(differs);
        update.setEnabled(differs);
    }

    private void updateOutputLabel(Settings settings) {
        String width = switch (settings.widthMode) {
            case FULL -> "full width";
            case PIXEL -> settings.widthPixels + " px wide";
            case A4, LETTER -> settings.widthMode.label();
        };
        output.setText("2× PNG · " + width);
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

    private static String shortcut(int keyCode) {
        String key = KeyEvent.getKeyText(keyCode);
        return System.getProperty("os.name", "").toLowerCase().contains("mac") ? "⌘" + key : "Ctrl+" + key;
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
                settings.showTimestamp ? timestamp() : null,
                settings.showResponseInfo ? responseInfo() : null
            );
        } catch (RuntimeException | StackOverflowError e) {
            // Runs on the Swing thread: report it in the extension's error log rather than Burp's.
            host.logError().accept("Could not render the screenshot: " + e);
            showStatus("Could not render the screenshot, see the extension's errors", true);
        }

        // Like Caido, a new content width resets the request/response split.
        Integer maxWidth = settings.maxContentWidth();
        if (rendered && !Objects.equals(maxWidth, lastMaxWidth)) {
            state.splitRatio = 0.5;
            content.setSplitRatio(0.5);
        }
        lastMaxWidth = maxWidth;
        rendered = true;

        preview.setMaxContentWidth(maxWidth);
        form.showRuleStatuses(content.ruleStatuses());
        int failed = failedRedactions().size();
        failedRules.setText("⚠ " + failed + (failed == 1 ? " redaction rule failed" : " redaction rules failed"));
        failedRules.setVisible(failed > 0);
        updateTemplateState();
        updateOutputLabel(settings);
        preview.setBackground(palette.canvas());
        preview.setHandleColors(palette.canvas(), palette.muted());
        previewScroll.getViewport().setBackground(palette.canvas());
    }

    /** The redaction rules that did not fully apply in the last render. */
    private List<RedactionRule> failedRedactions() {
        return state.settings.redactions
            .stream()
            .filter(rule -> {
                RuleStatus status = content.ruleStatuses().get(rule);
                return status != null && status.failed();
            })
            .toList();
    }

    private void showRules() {
        form.tabs().setSelectedIndex(SettingsForm.RULES);
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
        String export = toClipboard ? "Copy anyway" : "Save anyway";
        Object[] options = { export, "Show rules" };
        int answer = JOptionPane.showOptionDialog(
            this,
            message,
            toClipboard ? "Copy Screenshot" : "Save Screenshot",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            options,
            options[1]
        );
        if (answer == 1) {
            showRules();
        }
        return answer == 0;
    }

    private void flushRender() {
        if (renderTimer.isRunning()) {
            render();
        }
        getRootPane().validate();
    }

    private String timestamp() {
        ZonedDateTime time =
            data.requestTime() != null
                ? data.requestTime().withZoneSameInstant(ZoneId.systemDefault())
                : ZonedDateTime.now();
        return TIMESTAMP.format(time);
    }

    private String responseInfo() {
        if (data.responseLength() == null) {
            return null;
        }
        String info = NumberFormat.getIntegerInstance().format(data.responseLength()) + " bytes";
        return data.roundtripMillis() == null ? info : info + " | " + data.roundtripMillis() + "ms";
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
        String name = JOptionPane.showInputDialog(
            this,
            "Template name:",
            "Save As New Template",
            JOptionPane.PLAIN_MESSAGE
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

    /**
     * Port of the Caido selection context menu actions. Unlike Caido, new rules go first, like the ones added with the
     * "+" button in {@link RuleCards#section}.
     */
    private void onSelectionAction(MessagePane.SelectionAction action, String selected, RuleTarget target) {
        Settings settings = state.settings;
        switch (action) {
            case HIGHLIGHT -> settings.highlights.addFirst(HighlightRule.create(selected, target, MatchMode.STRING));
            case REDACT -> settings.redactions.addFirst(
                RedactionRule.create(selected, target, RedactionMode.REPLACE, MatchMode.STRING)
            );
            case HIDE_HEADER -> {
                int colon = selected.indexOf(':');
                String header = (colon > 0 ? selected.substring(0, colon) : selected).trim();
                if (header.isEmpty() || settings.headersToHide.both.contains(header)) {
                    return;
                }
                settings.headersToHide.both.add(header);
            }
        }
        rebuildForm();
        render();
    }

    private void fitToContent() {
        flushRender();
        int height = content.getHeight();
        int requestOverflow = content.requestPane().overflow();
        int responseOverflow = content.responsePane().overflow();
        if (content.isSideBySide()) {
            preview.setCropHeight(height + Math.max(requestOverflow, responseOverflow));
        } else {
            int requestHeight = content.requestPane().getHeight() + requestOverflow;
            int responseHeight = content.responsePane().getHeight() + responseOverflow;
            state.splitRatio = requestHeight / (double) Math.max(1, requestHeight + responseHeight);
            content.setSplitRatio(state.splitRatio);
            preview.setCropHeight(height + requestOverflow + responseOverflow);
        }
    }

    private void takeScreenshot(boolean toClipboard) {
        flushRender();
        if (!confirmFailedRedactions(toClipboard)) {
            return;
        }
        try {
            BufferedImage image = ImageCapture.render(content);
            if (toClipboard) {
                ImageCapture.copyToClipboard(image);
                showStatus("Copied to clipboard", false);
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
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), FILE_NAME.format(LocalDateTime.now())));
        while (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase().endsWith(".png")) {
                file = new File(file.getParentFile(), file.getName() + ".png");
            }
            if (
                !file.exists() ||
                JOptionPane.showConfirmDialog(
                    this,
                    file.getName() + " already exists. Replace it?",
                    "Save Screenshot",
                    JOptionPane.YES_NO_OPTION
                ) == JOptionPane.YES_OPTION
            ) {
                return file;
            }
        }
        return null;
    }

    private void showStatus(String message, boolean error) {
        status.setForeground(error ? Widgets.errorColor() : SUCCESS);
        status.setText(message);
        statusTimer.restart();
    }
}
