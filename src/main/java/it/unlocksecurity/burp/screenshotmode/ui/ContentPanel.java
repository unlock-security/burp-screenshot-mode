package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.Disposition;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.ViewMode;
import it.unlocksecurity.burp.screenshotmode.render.Decorated;
import it.unlocksecurity.burp.screenshotmode.render.HeaderFilter;
import it.unlocksecurity.burp.screenshotmode.render.HttpSyntax;
import it.unlocksecurity.burp.screenshotmode.render.MessageData;
import it.unlocksecurity.burp.screenshotmode.render.Palette;
import it.unlocksecurity.burp.screenshotmode.render.RuleEngine;
import it.unlocksecurity.burp.screenshotmode.render.pretty.PrettyPrinter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

/** The captured area: URL header, request and response panes, and the optional metadata footer. */
final class ContentPanel extends JPanel {

    private static final int DIVIDER_SIZE = 5;

    private final JPanel urlHeader = new JPanel(new BorderLayout(10, 0));
    private final JLabel urlLabel = new JLabel("URL");
    private final RoundedPanel urlBox = new RoundedPanel(new BorderLayout());
    private final JTextArea url = new JTextArea();
    private final MessagePane requestPane = new MessagePane("Request", RuleTarget.REQUEST);
    private final MessagePane responsePane = new MessagePane("Response", RuleTarget.RESPONSE);
    private final PlainSplitPane split = new PlainSplitPane(requestPane, responsePane);
    private final JPanel footer = new JPanel(new BorderLayout(16, 0));
    private final JLabel timestampLabel = new JLabel();
    private final JLabel responseInfoLabel = new JLabel();

    private double splitRatio = 0.5;
    private boolean applyingRatio;
    private DoubleConsumer splitRatioListener = ratio -> {};
    private Consumer<String> errorListener = message -> {};

    ContentPanel() {
        super(new BorderLayout());

        url.setEditable(false);
        url.setLineWrap(true);
        url.setWrapStyleWord(false);
        url.setOpaque(false);
        url.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        urlBox.add(url, BorderLayout.CENTER);
        JPanel labelHolder = new JPanel(new BorderLayout());
        labelHolder.setOpaque(false);
        labelHolder.add(urlLabel, BorderLayout.NORTH);
        urlLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        urlHeader.add(labelHolder, BorderLayout.WEST);
        urlHeader.add(urlBox, BorderLayout.CENTER);
        // A wrapping text area only knows its height once it has a width: lay out again after the first pass.
        url.addComponentListener(
            new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    if (url.getPreferredSize().height != url.getHeight()) {
                        SwingUtilities.invokeLater(ContentPanel.this::revalidate);
                    }
                }
            }
        );

        footer.add(timestampLabel, BorderLayout.WEST);
        footer.add(responseInfoLabel, BorderLayout.EAST);

        split.setResizeWeight(splitRatio);
        split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event -> {
            if (!applyingRatio && split.getDividerLocation() > 0) {
                int total = splitTotal();
                if (total > 0) {
                    splitRatio = Math.max(0.1, Math.min(0.9, split.getDividerLocation() / (double) total));
                    split.setResizeWeight(splitRatio);
                    splitRatioListener.accept(splitRatio);
                }
            }
        });
        split.addComponentListener(
            new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    applySplitRatio();
                }
            }
        );

        add(urlHeader, BorderLayout.NORTH);
        add(split, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }

    void setSelectionListener(MessagePane.SelectionListener listener) {
        requestPane.setSelectionListener(listener);
        responsePane.setSelectionListener(listener);
    }

    void setSplitRatioListener(DoubleConsumer listener) {
        this.splitRatioListener = listener;
    }

    /** Receives errors that did not stop the rendering, such as a body the pretty printer failed on. */
    void setErrorListener(Consumer<String> listener) {
        this.errorListener = listener;
    }

    void setSplitRatio(double ratio) {
        splitRatio = ratio;
        split.setResizeWeight(ratio);
        applySplitRatio();
    }

    /**
     * @param timestamp    shown in the footer when not {@code null}
     * @param responseInfo shown in the footer when not {@code null}
     */
    void render(
        MessageData data,
        Settings settings,
        Palette palette,
        Font mono,
        Font ui,
        String timestamp,
        String responseInfo
    ) {
        setBorder(BorderFactory.createLineBorder(palette.border()));
        setBackground(palette.background());

        urlHeader.setBackground(palette.headerBackground());
        urlHeader.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, palette.border()),
                BorderFactory.createEmptyBorder(8, 16, 8, 16)
            )
        );
        urlLabel.setFont(ui.deriveFont(Font.BOLD, ui.getSize2D() - 2));
        urlLabel.setForeground(palette.muted());
        urlBox.setBackground(palette.codeBackground());
        url.setFont(mono);
        url.setForeground(palette.text());
        url.setText(data.url());

        renderPane(requestPane, data.requestText(), RuleTarget.REQUEST, settings, palette, mono, ui);
        renderPane(responsePane, data.responseText(), RuleTarget.RESPONSE, settings, palette, mono, ui);

        int orientation =
            settings.disposition == Disposition.HORIZONTAL ? JSplitPane.HORIZONTAL_SPLIT : JSplitPane.VERTICAL_SPLIT;
        if (split.getOrientation() != orientation) {
            split.setOrientation(orientation);
            SwingUtilities.invokeLater(this::applySplitRatio);
        }
        split.setDividerColor(palette.border());
        split.setBackground(palette.background());

        footer.setVisible(timestamp != null || responseInfo != null);
        footer.setBackground(palette.background());
        footer.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, palette.border()),
                BorderFactory.createEmptyBorder(4, 12, 4, 12)
            )
        );
        Font small = ui.deriveFont(ui.getSize2D() - 1);
        for (JLabel label : new JLabel[] { timestampLabel, responseInfoLabel }) {
            label.setFont(small);
            label.setForeground(palette.muted());
        }
        timestampLabel.setText(timestamp == null ? "" : timestamp);
        responseInfoLabel.setText(responseInfo == null ? "" : responseInfo);

        revalidate();
        repaint();
    }

    private void renderPane(
        MessagePane pane,
        String raw,
        RuleTarget target,
        Settings settings,
        Palette palette,
        Font mono,
        Font ui
    ) {
        String text = raw == null ? "" : raw;
        // Before the header filter, which may hide the Content-Type the pretty printer relies on.
        if (settings.view == ViewMode.PRETTY) {
            try {
                text = PrettyPrinter.format(text);
            } catch (RuntimeException | StackOverflowError e) {
                // The pretty printers accept any input, so this is a bug: show the raw message rather than nothing.
                errorListener.accept("Could not pretty print the " + target.id() + ", showing it raw: " + e);
            }
        }
        String filtered = HeaderFilter.filter(text, settings.headersToHide.rulesFor(target));
        Decorated decorated = RuleEngine.apply(
            filtered,
            settings.highlights
                .stream()
                .filter(rule -> rule.target == target)
                .toList(),
            settings.redactions
                .stream()
                .filter(rule -> rule.target == target)
                .toList()
        );
        pane.render(decorated, HttpSyntax.tokenize(decorated.text(), target == RuleTarget.REQUEST), palette, mono, ui);
    }

    void setCaptureMode(boolean capturing) {
        requestPane.setCaptureMode(capturing);
        responsePane.setCaptureMode(capturing);
    }

    boolean isSideBySide() {
        return split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT;
    }

    MessagePane requestPane() {
        return requestPane;
    }

    MessagePane responsePane() {
        return responsePane;
    }

    private int splitTotal() {
        int size = isSideBySide() ? split.getWidth() : split.getHeight();
        return size - split.getDividerSize();
    }

    private void applySplitRatio() {
        int total = splitTotal();
        if (total <= 0) {
            return;
        }
        applyingRatio = true;
        try {
            split.setDividerLocation((int) Math.round(total * splitRatio));
        } finally {
            applyingRatio = false;
        }
    }

    /** A split pane whose divider is a flat line in the screenshot palette, whatever the look and feel. */
    private static final class PlainSplitPane extends JSplitPane {

        private Color dividerColor = Color.GRAY;

        PlainSplitPane(MessagePane left, MessagePane right) {
            super(JSplitPane.HORIZONTAL_SPLIT, left, right);
            setBorder(null);
            setContinuousLayout(true);
            setDividerSize(DIVIDER_SIZE);
        }

        void setDividerColor(Color color) {
            dividerColor = color;
            repaint();
        }

        @Override
        public void updateUI() {
            setUI(
                new BasicSplitPaneUI() {
                    @Override
                    public BasicSplitPaneDivider createDefaultDivider() {
                        return new BasicSplitPaneDivider(this) {
                            @Override
                            public void paint(Graphics g) {
                                g.setColor(dividerColor);
                                g.fillRect(0, 0, getWidth(), getHeight());
                            }

                            @Override
                            public void setBorder(Border border) {}
                        };
                    }
                }
            );
            revalidate();
        }
    }

    /** Panel with a rounded background, like the URL "code" box in Caido. */
    private static final class RoundedPanel extends JPanel {

        RoundedPanel(BorderLayout layout) {
            super(layout);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(getBackground());
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            g.dispose();
        }
    }
}
