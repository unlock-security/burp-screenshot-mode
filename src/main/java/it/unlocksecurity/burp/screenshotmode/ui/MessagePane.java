package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.render.Decorated;
import it.unlocksecurity.burp.screenshotmode.render.HeaderFilter;
import it.unlocksecurity.burp.screenshotmode.render.HttpSyntax;
import it.unlocksecurity.burp.screenshotmode.render.Palette;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.swing.BorderFactory;
import javax.swing.BoundedRangeModel;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

/** Titled, scrollable message view: the "Request" or "Response" half of the screenshot. */
final class MessagePane extends JPanel {

    enum SelectionAction {
        /** On the selected text. */
        HIGHLIGHT("Highlight", false),
        REDACT("Redact", false),
        /** On the header line under the pointer. */
        HIDE_HEADER("Hide header", true),
        REDACT_HEADER("Redact header value", true),
        HIGHLIGHT_HEADER("Highlight header", true);

        final String label;
        final boolean onHeader;

        SelectionAction(String label, boolean onHeader) {
            this.label = label;
            this.onHeader = onHeader;
        }
    }

    interface SelectionListener {
        /** @param text the selected text, or the header name for the actions on a header */
        void onSelectionAction(SelectionAction action, String text, RuleTarget target);
    }

    private final RuleTarget target;
    private final JPanel titleBar = new JPanel(new BorderLayout());
    private final JLabel title;
    private final MessageView view = new MessageView();
    private final LineNumberGutter gutter = new LineNumberGutter(view);
    private final JScrollPane scroll = new ChainedScrollPane(view);
    private SelectionListener selectionListener = (action, text, target) -> {};
    private boolean scrollbarVisible;

    MessagePane(String titleText, RuleTarget target) {
        super(new BorderLayout());
        this.target = target;
        title = new JLabel(titleText);
        titleBar.add(title, BorderLayout.CENTER);
        add(titleBar, BorderLayout.NORTH);

        scroll.setRowHeaderView(gutter);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setViewportBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        view.addMouseListener(
            new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    maybeShowMenu(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    maybeShowMenu(e);
                }
            }
        );
    }

    void setSelectionListener(SelectionListener listener) {
        this.selectionListener = listener;
    }

    void render(Decorated decorated, java.util.List<HttpSyntax.Token> tokens, Palette palette, Font mono, Font ui) {
        title.setFont(ui.deriveFont(Font.BOLD));
        title.setForeground(palette.text());
        titleBar.setBackground(palette.headerBackground());
        titleBar.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, palette.border()),
                BorderFactory.createEmptyBorder(7, 12, 7, 12)
            )
        );
        scroll.setBackground(palette.background());
        scroll.getViewport().setBackground(palette.background());
        styleScrollBar(scroll.getVerticalScrollBar(), palette);
        view.render(decorated, tokens, palette, mono);
        gutter.setPalette(palette);
    }

    /**
     * Colors the scrollbar with the screenshot's palette: Burp's theme would paint a light scrollbar over a dark
     * screenshot, and the other way round.
     */
    private static void styleScrollBar(JScrollBar bar, Palette palette) {
        Color track = palette.background();
        Color thumb = mix(palette.border(), palette.muted(), 0.3);
        bar.setBackground(track);
        bar.setForeground(thumb);
        bar.putClientProperty(
            "FlatLaf.style",
            "track: " +
                hex(track) +
                "; thumb: " +
                hex(thumb) +
                "; hoverTrackColor: " +
                hex(track) +
                "; hoverThumbColor: " +
                hex(mix(palette.border(), palette.muted(), 0.6)) +
                "; pressedTrackColor: " +
                hex(track) +
                "; pressedThumbColor: " +
                hex(palette.muted())
        );
    }

    private static Color mix(Color from, Color to, double amount) {
        return new Color(
            (int) Math.round(from.getRed() + (to.getRed() - from.getRed()) * amount),
            (int) Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * amount),
            (int) Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * amount)
        );
    }

    private static String hex(Color color) {
        return String.format("#%06x", color.getRGB() & 0xffffff);
    }

    /** Hides scrollbars and selection while the screenshot is taken; the layout is left untouched. */
    void setCaptureMode(boolean capturing) {
        if (capturing) {
            view.select(0, 0);
            scrollbarVisible = scroll.getVerticalScrollBar().isVisible();
            scroll.getVerticalScrollBar().setVisible(false);
        } else {
            scroll.getVerticalScrollBar().setVisible(scrollbarVisible);
        }
    }

    void setFitting(boolean fitting) {
        scroll.setVerticalScrollBarPolicy(
            fitting ? ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER : ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
        );
    }

    /** Extra height the view needs to show everything without scrolling (negative when there is spare room). */
    int overflow() {
        return view.getPreferredSize().height - scroll.getViewport().getExtentSize().height;
    }

    /**
     * Passes wheel scrolling it cannot use (nothing to scroll, or already at the top or bottom) on to the enclosing
     * scroll pane, as browsers do. Swing would swallow it, so once "Fit to Content" makes the whole message fit, the
     * preview could no longer be scrolled with the wheel or trackpad over the request and response.
     */
    private static final class ChainedScrollPane extends JScrollPane {

        ChainedScrollPane(Component view) {
            super(view);
        }

        @Override
        protected void processMouseWheelEvent(MouseWheelEvent e) {
            JScrollPane outer = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
            if (outer == null || canScroll(e)) {
                super.processMouseWheelEvent(e);
            } else {
                outer.dispatchEvent(SwingUtilities.convertMouseEvent(this, e, outer));
                e.consume();
            }
        }

        private boolean canScroll(MouseWheelEvent e) {
            JScrollBar bar = getVerticalScrollBar();
            if (e.isShiftDown() || !bar.isVisible()) {
                return false;
            }
            BoundedRangeModel model = bar.getModel();
            return e.getPreciseWheelRotation() < 0
                ? model.getValue() > model.getMinimum()
                : model.getValue() + model.getExtent() < model.getMaximum();
        }
    }

    private void maybeShowMenu(MouseEvent e) {
        showSelectionMenu(e, view, target, selectionListener);
    }

    /**
     * On a popup trigger, offers the actions that create a rule for the selected text and, over a header line, the
     * actions on that header. The URL bar has no headers.
     */
    static void showSelectionMenu(MouseEvent e, MessageView view, RuleTarget target, SelectionListener listener) {
        if (!e.isPopupTrigger()) {
            return;
        }
        String selected = view.getSelectedText();
        String header = target == RuleTarget.URL ? null : headerAt(view.getText(), view.viewToModel2D(e.getPoint()));
        JPopupMenu menu = new JPopupMenu();
        for (SelectionAction action : SelectionAction.values()) {
            String text = action.onHeader ? header : selected;
            if (text == null || text.isEmpty()) {
                continue;
            }
            if (action == SelectionAction.HIDE_HEADER && menu.getComponentCount() > 0) {
                menu.addSeparator();
            }
            JMenuItem item = new JMenuItem(action.label);
            item.addActionListener(event -> listener.onSelectionAction(action, text, target));
            menu.add(item);
        }
        if (menu.getComponentCount() > 0) {
            menu.show(view, e.getX(), e.getY());
        }
    }

    /** The name of the header on the line at {@code offset}, or {@code null} if that line is not a header. */
    static String headerAt(String text, int offset) {
        if (offset < 0) {
            return null;
        }
        for (HeaderFilter.Line line : HeaderFilter.lines(text)) {
            if (offset >= line.from() && offset <= line.to()) {
                return line.name();
            }
        }
        return null;
    }
}
