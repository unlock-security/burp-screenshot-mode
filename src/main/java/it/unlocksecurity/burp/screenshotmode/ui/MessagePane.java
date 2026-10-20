package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.render.Decorated;
import it.unlocksecurity.burp.screenshotmode.render.HttpSyntax;
import it.unlocksecurity.burp.screenshotmode.render.Palette;
import java.awt.BorderLayout;
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
        HIGHLIGHT("Highlight"),
        REDACT("Redact"),
        HIDE_HEADER("Hide Header");

        final String label;

        SelectionAction(String label) {
            this.label = label;
        }
    }

    interface SelectionListener {
        void onSelectionAction(SelectionAction action, String selectedText, RuleTarget target);
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
        view.render(decorated, tokens, palette, mono);
        gutter.setPalette(palette);
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
     * On a popup trigger over selected text, offers the actions that create a rule for it. The URL bar has no
     * headers, so it only offers Highlight and Redact.
     */
    static void showSelectionMenu(MouseEvent e, MessageView view, RuleTarget target, SelectionListener listener) {
        if (!e.isPopupTrigger()) {
            return;
        }
        String selected = view.getSelectedText();
        if (selected == null || selected.isEmpty()) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        for (SelectionAction action : SelectionAction.values()) {
            if (action == SelectionAction.HIDE_HEADER && target == RuleTarget.URL) {
                continue;
            }
            JMenuItem item = new JMenuItem(action.label);
            item.addActionListener(event -> listener.onSelectionAction(action, selected, target));
            menu.add(item);
        }
        menu.show(view, e.getX(), e.getY());
    }
}
