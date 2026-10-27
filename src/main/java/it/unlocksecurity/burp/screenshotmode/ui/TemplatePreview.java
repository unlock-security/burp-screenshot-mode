package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.Labeled;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.UrlMode;
import it.unlocksecurity.burp.screenshotmode.render.MessageData;
import it.unlocksecurity.burp.screenshotmode.render.Palette;
import it.unlocksecurity.burp.screenshotmode.storage.SampleMessage;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.text.AbstractDocument;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;

/**
 * The template editor's preview of the sample message. With the Rules and Headers tabs, one part of it (URL, request
 * or response) as written, editable, beside the same part as the screenshot shows it, so a rule and the text it must
 * match can be changed together, side by side, stacked or in tabs, as the user last chose. With the Appearance tab,
 * the whole screenshot.
 */
final class TemplatePreview extends JPanel {

    /** Where the editor's choice of {@link Arrangement} is kept, for every template editor. */
    static final String ARRANGEMENT_KEY = "screenshot-mode.sampleLayout";
    private static final String PARTS = "parts";
    private static final String WHOLE = "whole";
    private static final RuleTarget[] PART_ORDER = { RuleTarget.URL, RuleTarget.REQUEST, RuleTarget.RESPONSE };

    /**
     * The sample message as edited, and the part on screen: one for every template editor, kept until Burp closes and
     * never stored.
     */
    static final class Sample {

        private final Map<RuleTarget, String> texts = new EnumMap<>(RuleTarget.class);
        RuleTarget part = RuleTarget.REQUEST;

        Sample() {
            restore();
        }

        String text(RuleTarget part) {
            return texts.get(part);
        }

        void set(RuleTarget part, String text) {
            texts.put(part, text);
        }

        void restore() {
            SampleMessage bundled = SampleMessage.bundled();
            texts.put(RuleTarget.URL, bundled.url());
            texts.put(RuleTarget.REQUEST, bundled.request());
            texts.put(RuleTarget.RESPONSE, bundled.response());
        }

        boolean isEdited() {
            SampleMessage bundled = SampleMessage.bundled();
            return !(
                texts.get(RuleTarget.URL).equals(bundled.url()) &&
                texts.get(RuleTarget.REQUEST).equals(bundled.request()) &&
                texts.get(RuleTarget.RESPONSE).equals(bundled.response())
            );
        }

        MessageData data() {
            String response = texts.get(RuleTarget.RESPONSE);
            return new MessageData(
                "sample",
                texts.get(RuleTarget.URL),
                texts.get(RuleTarget.REQUEST),
                response,
                response.getBytes(StandardCharsets.UTF_8).length,
                null,
                SampleMessage.bundled().requestTime()
            );
        }
    }

    /** How the part as written and as in the screenshot share the preview, like the layouts of Burp's Repeater. */
    enum Arrangement implements Labeled {
        SIDE_BY_SIDE("side-by-side", "Show the sample and the screenshot side by side", Widgets.Glyph.SIDE_BY_SIDE),
        STACKED("stacked", "Show the sample above the screenshot", Widgets.Glyph.STACKED),
        TABS("tabs", "Show the sample and the screenshot in tabs", Widgets.Glyph.SINGLE);

        private final String id;
        private final String label;
        final Widgets.Glyph icon;

        Arrangement(String id, String label, Widgets.Glyph icon) {
            this.id = id;
            this.label = label;
            this.icon = icon;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String label() {
            return label;
        }

        static Arrangement stored(String id) {
            try {
                return id == null ? SIDE_BY_SIDE : Labeled.fromId(Arrangement.class, id);
            } catch (IllegalArgumentException e) {
                return SIDE_BY_SIDE;
            }
        }
    }

    private final Sample sample;
    private final Settings settings;
    private final Host host;
    private final ContentPanel whole = new ContentPanel();
    private final PreviewArea wholeArea = new PreviewArea(whole);
    private final JScrollPane wholeScroll = new JScrollPane(wholeArea);
    private final ContentPanel part = new ContentPanel();
    private final JPanel partHolder = new JPanel(new BorderLayout());
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final CardLayout editorCards = new CardLayout();
    private final JPanel editors = new JPanel(editorCards);
    private final Map<RuleTarget, Source> sources = new EnumMap<>(RuleTarget.class);
    private final SegmentedControl<RuleTarget> parts;
    private final SegmentedControl<Arrangement> arrangement;
    private final JPanel written;
    private final JPanel shown;
    private final JSplitPane split = new JSplitPane();
    private final JTabbedPane tabs = new JTabbedPane();
    private final JPanel partsArea = new JPanel(new BorderLayout());
    /** Splits the panes evenly once the split pane has a size: before that, a proportional location is lost. */
    private boolean centerDivider;
    private final JButton restore = Widgets.button("Restore sample");
    private final JLabel partsLabel = new JLabel("Show");
    private final Timer renderTimer = new Timer(150, event -> render());
    private BiConsumer<MessageData, ContentPanel> rendered = (data, content) -> {};
    private boolean restoring;

    TemplatePreview(Sample sample, Settings settings, Host host) {
        super(new BorderLayout());
        this.sample = sample;
        this.settings = settings;
        this.host = host;
        renderTimer.setRepeats(false);

        parts = new SegmentedControl<>(PART_ORDER, sample.part, RuleTarget::label, value -> {
            sample.part = value;
            editorCards.show(editors, value.id());
            render();
        });
        parts.setToolTipText("The part of the sample message to edit and preview");
        restore.putClientProperty("JButton.buttonType", "borderless");
        restore.setToolTipText("Bring back the sample message as it was, in every part");
        restore.addActionListener(event -> restoreSample());
        arrangement = new SegmentedControl<>(
            Arrangement.values(),
            Arrangement.stored(host.preferences().get(ARRANGEMENT_KEY)),
            Arrangement::label,
            value -> {
                arrange(value);
                host.preferences().set(ARRANGEMENT_KEY, value.id());
            }
        );
        for (Arrangement value : Arrangement.values()) {
            arrangement.showIcon(value, value.icon);
        }
        arrangement.getAccessibleContext().setAccessibleName("Preview layout");

        // In a narrow preview Restore sample goes to a row of its own, then the parts below their label: the bar grows
        // to show them whole.
        JPanel left = new JPanel(new WrapLayout(10, 0, true));
        partsLabel.setLabelFor(parts);
        left.add(partsLabel);
        left.add(parts);
        JPanel right = new JPanel(new WrapLayout(8, 0, true));
        right.add(restore);
        right.add(arrangement);
        JPanel bar = new JPanel();
        bar.setLayout(new ToolbarLayout(left, right));
        bar.add(left);
        bar.add(right);
        bar.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Widgets.borderColor()),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
            )
        );

        Font mono = host.editorFont().get();
        for (RuleTarget target : PART_ORDER) {
            Source source = new Source(target, mono);
            sources.put(target, source);
            JScrollPane scroll = new JScrollPane(source.area);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            editors.add(scroll, target.id());
        }
        editorCards.show(editors, sample.part.id());

        partHolder.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        written = headed("As written (editable)", editors);
        shown = headed("In the screenshot", partHolder);
        split.setLeftComponent(written);
        split.setRightComponent(shown);
        split.setResizeWeight(0.5);
        split.setBorder(null);
        split.addComponentListener(
            new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    if (centerDivider && split.getWidth() > 0 && split.getHeight() > 0) {
                        centerDivider = false;
                        split.setDividerLocation(0.5);
                    }
                }
            }
        );
        arrange(arrangement.value());

        wholeArea.hideHandles();
        wholeScroll.setBorder(null);
        wholeScroll.getVerticalScrollBar().setUnitIncrement(16);
        center.add(partsArea, PARTS);
        center.add(wholeScroll, WHOLE);

        add(bar, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        showWhole(false);
    }

    /**
     * Called after each render with the sample and the panel of one part, for the rules' and headers' results: it
     * renders every part, so a rule shows what it does also in a message the screenshot leaves out.
     */
    void setRenderListener(BiConsumer<MessageData, ContentPanel> listener) {
        rendered = listener;
    }

    void setSelectionListener(MessagePane.SelectionListener listener) {
        whole.setSelectionListener(listener);
        part.setSelectionListener(listener);
    }

    /** The whole screenshot (Appearance tab), or one part beside its source (Rules and Headers tabs). */
    void showWhole(boolean show) {
        cards.show(center, show ? WHOLE : PARTS);
        parts.setVisible(!show);
        partsLabel.setVisible(!show);
        arrangement.setVisible(!show);
    }

    /** Renders again shortly: the settings changed. */
    void changed() {
        renderTimer.restart();
    }

    JTextArea source(RuleTarget target) {
        return sources.get(target).area;
    }

    SegmentedControl<RuleTarget> parts() {
        return parts;
    }

    SegmentedControl<Arrangement> arrangement() {
        return arrangement;
    }

    JButton restoreButton() {
        return restore;
    }

    ContentPanel partView() {
        return part;
    }

    ContentPanel wholeView() {
        return whole;
    }

    void render() {
        renderTimer.stop();
        MessageData data = sample.data();
        Palette palette = host.palette(settings.theme);
        Font mono = host.editorFont().get();
        Font ui = host.displayFont().get();
        try {
            whole.render(
                data,
                settings,
                palette,
                mono,
                ui,
                settings.showTimestamp ? ContentPanel.timestamp(data) : null,
                settings.showResponseInfo ? ContentPanel.responseInfo(data) : null
            );
            // A hidden URL bar would leave nothing to see beside the URL being edited: show it in full.
            Settings partSettings = settings;
            if (settings.url == UrlMode.HIDDEN) {
                partSettings = settings.copy();
                partSettings.url = UrlMode.FULL;
            }
            part.showOnly(sample.part);
            part.render(data, partSettings, palette, mono, ui, null, null);
        } catch (RuntimeException | StackOverflowError e) {
            host.logError().accept("Could not render the template preview: " + e);
        }
        partHolder.remove(part);
        partHolder.add(part, sample.part == RuleTarget.URL ? BorderLayout.NORTH : BorderLayout.CENTER);
        partHolder.setBackground(palette.canvas());
        wholeArea.setWidth(settings.widthMode, settings.maxContentWidth());
        wholeArea.setHeight(settings.heightMode, settings.heightPixels);
        wholeArea.setBackground(palette.canvas());
        wholeScroll.getViewport().setBackground(palette.canvas());
        restore.setEnabled(sample.isEdited());
        rendered.accept(data, part);
        revalidate();
        repaint();
    }

    /**
     * Puts the part as written and as in the screenshot in a split pane, or in two tabs, the screenshot first: the tab
     * titles then say what the headings say.
     */
    private void arrange(Arrangement arrangement) {
        partsArea.removeAll();
        if (arrangement == Arrangement.TABS) {
            tabs.removeAll();
            tabs.addTab("As written (editable)", editors);
            tabs.addTab("In the screenshot", partHolder);
            tabs.setSelectedComponent(partHolder);
            partsArea.add(tabs);
        } else {
            written.add(editors, BorderLayout.CENTER);
            shown.add(partHolder, BorderLayout.CENTER);
            split.setOrientation(
                arrangement == Arrangement.SIDE_BY_SIDE ? JSplitPane.HORIZONTAL_SPLIT : JSplitPane.VERTICAL_SPLIT
            );
            partsArea.add(split);
            if (split.getWidth() > 0 && split.getHeight() > 0) {
                split.setDividerLocation(0.5);
            } else {
                centerDivider = true;
            }
        }
        partsArea.revalidate();
        partsArea.repaint();
    }

    private static JPanel headed(String title, JComponent content) {
        JLabel label = Widgets.hint(title);
        label.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(label, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    /** Puts the bundled sample back in every part, as one step that ⌘Z undoes in each. */
    private void restoreSample() {
        sample.restore();
        restoring = true;
        try {
            for (RuleTarget target : PART_ORDER) {
                sources.get(target).replace(sample.text(target));
            }
        } finally {
            restoring = false;
        }
        render();
    }

    /** The editable text of one part, with its own undo and redo: the settings' history doesn't cover it. */
    private final class Source {

        final JTextArea area;
        private final UndoManager undo = new UndoManager();
        /** Collects the edits of a replacement, so that one undo takes it back. */
        private CompoundEdit group;

        Source(RuleTarget target, Font mono) {
            area = new JTextArea(sample.text(target));
            area.setFont(mono);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setCaretPosition(0);
            area.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            area.getAccessibleContext().setAccessibleName(target.label() + " of the sample message");
            if (target == RuleTarget.URL) {
                // One line: a pasted URL keeps no line break.
                ((AbstractDocument) area.getDocument()).setDocumentFilter(new Widgets.SingleLine());
            }
            area.getDocument().addUndoableEditListener(event -> {
                if (group != null) {
                    group.addEdit(event.getEdit());
                } else {
                    undo.addEdit(event.getEdit());
                }
            });
            Widgets.onTextChange(area, text -> {
                sample.set(target, text.replace("\r\n", "\n"));
                if (!restoring) {
                    renderTimer.restart();
                }
            });
            SettingsHistory.bindKeys(area, JComponent.WHEN_FOCUSED, () -> step(true), () -> step(false));
        }

        void replace(String text) {
            if (area.getText().equals(text)) {
                return;
            }
            group = new CompoundEdit();
            area.setText(text);
            area.setCaretPosition(0);
            group.end();
            undo.addEdit(group);
            group = null;
        }

        private void step(boolean back) {
            if (back ? !undo.canUndo() : !undo.canRedo()) {
                UIManager.getLookAndFeel().provideErrorFeedback(area);
            } else if (back) {
                undo.undo();
            } else {
                undo.redo();
            }
        }
    }
}
