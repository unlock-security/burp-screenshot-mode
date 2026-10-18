package it.unlocksecurity.burp.screenshotmode.ui;

import java.awt.FontMetrics;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;

/** Geometry of a text range: one rectangle per visual row, like the boxes of a wrapped inline CSS element. */
final class RowRects {

    private RowRects() {}

    static List<Rectangle2D> of(JTextComponent component, String text, int from, int to) {
        List<Rectangle2D> rows = new ArrayList<>();
        FontMetrics metrics = component.getFontMetrics(component.getFont());
        int end = Math.min(to, text.length());
        try {
            Rectangle2D current = null;
            Rectangle2D caret = from < end ? component.modelToView2D(from) : null;
            for (int i = from; i < end && caret != null; i++) {
                Rectangle2D next = component.modelToView2D(i + 1);
                char c = text.charAt(i);
                if (c == '\n') {
                    if (current != null) {
                        rows.add(current);
                        current = null;
                    }
                    caret = next;
                    continue;
                }

                boolean sameRow =
                    next != null && Math.abs(next.getY() - caret.getY()) < 0.5 && next.getX() > caret.getX();
                double width = sameRow ? next.getX() - caret.getX() : metrics.charWidth(c);
                Rectangle2D box = new Rectangle2D.Double(caret.getX(), caret.getY(), width, caret.getHeight());
                if (current != null && Math.abs(current.getY() - box.getY()) < 0.5) {
                    current = current.createUnion(box);
                } else {
                    if (current != null) {
                        rows.add(current);
                    }
                    current = box;
                }
                caret = next;
            }
            if (current != null) {
                rows.add(current);
            }
        } catch (BadLocationException e) {
            // The document changed under us; the next paint recomputes.
        }
        return rows;
    }
}
