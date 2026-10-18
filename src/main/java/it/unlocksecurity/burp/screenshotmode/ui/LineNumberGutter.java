package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.render.Palette;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.Rectangle2D;
import javax.swing.JComponent;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;

/** Line numbers for a {@link MessageView}; wrapped continuation rows get no number, as in Caido. */
final class LineNumberGutter extends JComponent {

    private static final int PADDING = 8;

    private final MessageView view;
    private Palette palette = Palette.DARK;

    LineNumberGutter(MessageView view) {
        this.view = view;
        setOpaque(true);
        view.addComponentListener(
            new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    revalidate();
                    repaint();
                }
            }
        );
    }

    void setPalette(Palette palette) {
        this.palette = palette;
        revalidate();
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        int lines = view.getDocument().getDefaultRootElement().getElementCount();
        FontMetrics metrics = getFontMetrics(view.getFont());
        int digits = Math.max(2, String.valueOf(lines).length());
        return new Dimension(
            metrics.charWidth('0') * digits + PADDING * 2,
            Math.max(view.getHeight(), view.getPreferredSize().height)
        );
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics;
        Rectangle clip = g.getClipBounds();
        if (clip == null) {
            clip = new Rectangle(0, 0, getWidth(), getHeight());
        }
        g.setColor(palette.background());
        g.fill(clip);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(view.getFont());
        g.setColor(palette.muted());

        FontMetrics metrics = g.getFontMetrics();
        Element root = view.getDocument().getDefaultRootElement();
        int first = root.getElementIndex(view.viewToModel2D(new Point(0, clip.y)));
        int last = root.getElementIndex(view.viewToModel2D(new Point(0, clip.y + clip.height)));
        for (int line = first; line <= last; line++) {
            try {
                Rectangle2D row = view.modelToView2D(root.getElement(line).getStartOffset());
                if (row == null) {
                    continue;
                }
                String number = String.valueOf(line + 1);
                g.drawString(
                    number,
                    getWidth() - PADDING - metrics.stringWidth(number),
                    (int) Math.round(row.getY()) + metrics.getAscent()
                );
            } catch (BadLocationException e) {
                return;
            }
        }
    }
}
