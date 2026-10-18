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
import it.unlocksecurity.burp.screenshotmode.storage.TemplateStore;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
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
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;

/** The screenshot window: settings on the left, live preview of the captured content on the right. */
final class ScreenshotDialog extends JDialog {

    private static final String LAST_DIRECTORY_KEY = "screenshot-mode.lastDirectory";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);
    private static final DateTimeFormatter FILE_NAME = DateTimeFormatter.ofPattern(
        "'screenshot-'yyyyMMdd-HHmmss'.png'"
    );
    private static final Color SUCCESS = new Color(0x3fa45b);

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
    private final JButton resetHeight = new JButton("Reset Height");
    private final Runnable templatesListener = this::reloadTemplates;

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

        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildSidebar(), buildPreview());
        main.setDividerLocation(380);
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

        content.setSelectionListener(this::onSelectionAction);
        content.setSplitRatio(state.splitRatio);
        content.setSplitRatioListener(ratio -> state.splitRatio = ratio);
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
        store.removeListener(templatesListener);
        renderTimer.stop();
        statusTimer.stop();
        super.dispose();
    }

    private JComponent buildTopBar() {
        JLabel title = new JLabel("Screenshot Mode");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 3));
        JButton save = new JButton("Save Screenshot");
        save.addActionListener(event -> takeScreenshot(false));
        JButton copy = new JButton("Copy to Clipboard");
        copy.addActionListener(event -> takeScreenshot(true));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        bar.add(title);
        bar.add(save);
        bar.add(copy);
        bar.add(status);
        bar.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Widgets.borderColor()),
                BorderFactory.createEmptyBorder(10, 6, 10, 6)
            )
        );
        return bar;
    }

    private JComponent buildSidebar() {
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
        JButton reset = Widgets.iconButton(Widgets.Glyph.RESET, "Reset to template defaults", this::resetToTemplate);
        JPanel comboRow = new JPanel(new BorderLayout(6, 0));
        comboRow.setOpaque(false);
        comboRow.add(templateCombo, BorderLayout.CENTER);
        comboRow.add(reset, BorderLayout.EAST);

        JButton update = new JButton("Update Template");
        update.setToolTipText("Save the current settings into the selected template");
        update.addActionListener(event -> updateTemplate());
        JButton saveAs = new JButton("Save As New…");
        saveAs.addActionListener(event -> saveAsNewTemplate());

        JPanel templateContent = new JPanel(new BorderLayout(0, 6));
        templateContent.setOpaque(false);
        templateContent.add(comboRow, BorderLayout.NORTH);
        templateContent.add(Widgets.row(update, saveAs), BorderLayout.CENTER);

        formHolder.setOpaque(false);
        Widgets.Stack sidebar = new Widgets.Stack(20);
        sidebar.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        sidebar.addItem(Widgets.section("Template", null, null, templateContent));
        sidebar.addItem(formHolder);

        JScrollPane scroll = new JScrollPane(sidebar);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setMinimumSize(new Dimension(280, 0));
        return scroll;
    }

    private JComponent buildPreview() {
        previewScroll.setBorder(null);
        previewScroll.getVerticalScrollBar().setUnitIncrement(16);

        JButton fit = new JButton("Fit to Content");
        fit.setToolTipText("Crop the height so the whole request and response are visible");
        fit.addActionListener(event -> fitToContent());
        resetHeight.addActionListener(event -> preview.setCropHeight(null));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        bar.add(Widgets.hint("Drag the handle below the content to crop it • right-click selected text to add rules"));
        bar.add(fit);
        bar.add(resetHeight);
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Widgets.borderColor()));

        JPanel column = new JPanel(new BorderLayout());
        column.add(previewScroll, BorderLayout.CENTER);
        column.add(bar, BorderLayout.SOUTH);
        return column;
    }

    private void rebuildForm() {
        formHolder.removeAll();
        formHolder.add(
            new SettingsForm(state.settings, renderTimer::restart, host.editorFont().get(), false),
            BorderLayout.CENTER
        );
        formHolder.revalidate();
        formHolder.repaint();
    }

    private void render() {
        renderTimer.stop();
        Settings settings = state.settings;
        Palette palette = host.palette(settings.theme);
        content.render(
            data,
            settings,
            palette,
            host.editorFont().get(),
            host.displayFont().get(),
            settings.showTimestamp ? timestamp() : null,
            settings.showResponseInfo ? responseInfo() : null
        );

        // Like Caido, a new content width resets the request/response split.
        Integer maxWidth = settings.maxContentWidth();
        if (rendered && !Objects.equals(maxWidth, lastMaxWidth)) {
            state.splitRatio = 0.5;
            content.setSplitRatio(0.5);
        }
        lastMaxWidth = maxWidth;
        rendered = true;

        preview.setMaxContentWidth(maxWidth);
        preview.setBackground(palette.canvas());
        preview.setHandleColors(palette.canvas(), palette.muted());
        previewScroll.getViewport().setBackground(palette.canvas());
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
    }

    private void resetToTemplate() {
        store.getTemplateById(state.templateId).ifPresentOrElse(
            template -> {
                state.settings = template.settings.copy();
                rebuildForm();
                render();
                showStatus("Settings reset to \"" + template.name + "\"", false);
            },
            () -> showStatus("Select a template first", true)
        );
    }

    private void updateTemplate() {
        if (store.updateTemplate(state.templateId, null, state.settings).isPresent()) {
            showStatus("Template updated!", false);
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
        showStatus("Template \"" + template.name + "\" created!", false);
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
        try {
            BufferedImage image = ImageCapture.render(content);
            if (toClipboard) {
                ImageCapture.copyToClipboard(image);
                showStatus("Screenshot copied to clipboard!", false);
                return;
            }

            File file = chooseFile();
            if (file != null) {
                ImageCapture.save(image, file);
                host.preferences().set(LAST_DIRECTORY_KEY, file.getParent());
                showStatus("Screenshot saved!", false);
            }
        } catch (Exception e) {
            host.logError().accept("Screenshot failed: " + e);
            showStatus("Failed: " + e.getMessage(), true);
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
