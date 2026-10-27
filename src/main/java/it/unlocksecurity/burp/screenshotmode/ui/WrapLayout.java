package it.unlocksecurity.burp.screenshotmode.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/** A FlowLayout whose preferred size accounts for wrapping, so rows of controls reflow in narrow panels. */
final class WrapLayout extends FlowLayout {

    /** The height last asked of the parent, so it is asked once per change and never in a loop. */
    private int requestedHeight = -1;
    private final boolean wantsOneRow;

    WrapLayout(int hgap, int vgap) {
        this(hgap, vgap, false);
    }

    /**
     * @param wantsOneRow for a container its parent sizes to its preferred width, such as a control in a form grid:
     *                    it prefers the width of one row, so it unwraps as soon as there is room again, and can shrink
     *                    to its widest component, wrapping the rest.
     */
    WrapLayout(int hgap, int vgap, boolean wantsOneRow) {
        super(FlowLayout.LEFT, hgap, vgap);
        this.wantsOneRow = wantsOneRow;
    }

    /**
     * The parent asks for the preferred height before it gives this container its new width, so after a resize the
     * rows may need another height than they got, and wrapped controls end up cut off. Lays the parent out again then.
     */
    @Override
    public void layoutContainer(Container target) {
        super.layoutContainer(target);
        // A component wider than a row would be cut off: give it the row's width instead, so one that wraps, such as
        // a group of buttons, wraps inside it. It already sits on a row of its own.
        Insets insets = target.getInsets();
        int rowWidth = target.getWidth() - insets.left - insets.right - getHgap() * 2;
        for (Component component : target.getComponents()) {
            if (component.isVisible() && rowWidth > 0 && component.getWidth() > rowWidth) {
                component.setBounds(insets.left + getHgap(), component.getY(), rowWidth, component.getHeight());
            }
        }
        int height = preferredLayoutSize(target).height;
        if (height == target.getHeight()) {
            requestedHeight = -1;
        } else if (height != requestedHeight && target instanceof JComponent component) {
            requestedHeight = height;
            SwingUtilities.invokeLater(component::revalidate);
        }
    }

    @Override
    public Dimension preferredLayoutSize(Container target) {
        Dimension preferred = layoutSize(target, true, false);
        if (wantsOneRow) {
            preferred.width = layoutSize(target, true, true).width;
        }
        return preferred;
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false, false);
        if (wantsOneRow) {
            int widest = 0;
            for (Component component : target.getComponents()) {
                if (component.isVisible()) {
                    widest = Math.max(widest, component.getMinimumSize().width);
                }
            }
            Insets insets = target.getInsets();
            minimum.width = widest + insets.left + insets.right + getHgap() * 2;
        } else {
            minimum.width -= getHgap() + 1;
        }
        return minimum;
    }

    /** @param oneRow lay the components out on a single row, whatever the width */
    private Dimension layoutSize(Container target, boolean preferred, boolean oneRow) {
        synchronized (target.getTreeLock()) {
            Container container = target;
            while (container.getSize().width == 0 && container.getParent() != null) {
                container = container.getParent();
            }
            int targetWidth = container.getSize().width;
            if (targetWidth == 0 || oneRow) {
                targetWidth = Integer.MAX_VALUE;
            }

            int hgap = getHgap();
            int vgap = getVgap();
            Insets insets = target.getInsets();
            int maxWidth = targetWidth - (insets.left + insets.right + hgap * 2);

            Dimension dimension = new Dimension(0, 0);
            int rowWidth = 0;
            int rowHeight = 0;
            for (Component component : target.getComponents()) {
                if (!component.isVisible()) {
                    continue;
                }
                Dimension size = preferred ? component.getPreferredSize() : component.getMinimumSize();
                if (rowWidth + size.width > maxWidth) {
                    addRow(dimension, rowWidth, rowHeight);
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth != 0) {
                    rowWidth += hgap;
                }
                rowWidth += size.width;
                rowHeight = Math.max(rowHeight, size.height);
            }
            addRow(dimension, rowWidth, rowHeight);

            dimension.width += insets.left + insets.right + hgap * 2;
            dimension.height += insets.top + insets.bottom + vgap * 2;

            // Lets a container that fills a scroll pane shrink with it. Not for one row, which must fit its components
            // exactly or the last one wraps.
            Container scrollPane = SwingUtilities.getAncestorOfClass(JScrollPane.class, target);
            if (scrollPane != null && target.isValid() && !oneRow) {
                dimension.width -= hgap + 1;
            }
            return dimension;
        }
    }

    private void addRow(Dimension dimension, int rowWidth, int rowHeight) {
        dimension.width = Math.max(dimension.width, rowWidth);
        if (dimension.height > 0) {
            dimension.height += getVgap();
        }
        dimension.height += rowHeight;
    }
}
