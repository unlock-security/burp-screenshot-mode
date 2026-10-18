package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.render.Decorated;
import it.unlocksecurity.burp.screenshotmode.render.HttpSyntax;
import it.unlocksecurity.burp.screenshotmode.render.Palette;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

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
    private final JScrollPane scroll = new JScrollPane(view);
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

    private void maybeShowMenu(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        String selected = view.getSelectedText();
        if (selected == null || selected.isEmpty()) {
            return;
        }
        JPopupMenu menu = new JPopupMenu();
        for (SelectionAction action : SelectionAction.values()) {
            JMenuItem item = new JMenuItem(action.label);
            item.addActionListener(event -> selectionListener.onSelectionAction(action, selected, target));
            menu.add(item);
        }
        menu.show(view, e.getX(), e.getY());
    }
}
