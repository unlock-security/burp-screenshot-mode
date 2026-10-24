package it.unlocksecurity.burp.screenshotmode.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * A toolbar of two parts, each a {@link WrapLayout} that wants one row: {@code right} on the right and {@code left}
 * wrapping its components in the rest of the width, as long as that rest holds each of them whole; otherwise
 * {@code right} on a row of its own below, still on the right. A component that is a group of controls splits only
 * when even a whole row is too narrow for it, so the toolbar never cuts a control off.
 */
final class ToolbarLayout implements LayoutManager {

    private static final int GAP = 12;
    private final Container left;
    private final Component right;

    ToolbarLayout(Container left, Component right) {
        this.left = left;
        this.right = right;
    }

    /** Whether {@code right}, on one row, leaves {@code left} the room for its widest component, whole. */
    private boolean sideBySide(Container toolbar, int width) {
        Insets insets = toolbar.getInsets();
        return insets.left + widestOnLeft() + GAP + right.getPreferredSize().width + insets.right <= width;
    }

    private int widestOnLeft() {
        int widest = 0;
        for (Component component : left.getComponents()) {
            if (component.isVisible()) {
                widest = Math.max(widest, component.getPreferredSize().width);
            }
        }
        Insets insets = left.getInsets();
        int gaps = left.getLayout() instanceof FlowLayout flow ? flow.getHgap() * 2 : 0;
        return insets.left + widest + gaps + insets.right;
    }

    @Override
    public Dimension preferredLayoutSize(Container toolbar) {
        Insets insets = toolbar.getInsets();
        // The toolbar spans its parent: the parent's width is already the new one while the toolbar's is not.
        int width = toolbar.getParent() != null ? toolbar.getParent().getWidth() : toolbar.getWidth();
        Dimension l = left.getPreferredSize();
        Dimension r = right.getPreferredSize();
        int height = sideBySide(toolbar, width) ? Math.max(l.height, r.height) : l.height + r.height;
        return new Dimension(insets.left + l.width + GAP + r.width + insets.right, insets.top + height + insets.bottom);
    }

    /** As narrow as the wider part's widest control: everything else wraps. */
    @Override
    public Dimension minimumLayoutSize(Container toolbar) {
        Insets insets = toolbar.getInsets();
        int width = Math.max(left.getMinimumSize().width, right.getMinimumSize().width);
        return new Dimension(insets.left + width + insets.right, preferredLayoutSize(toolbar).height);
    }

    @Override
    public void layoutContainer(Container toolbar) {
        Insets insets = toolbar.getInsets();
        int width = toolbar.getWidth() - insets.left - insets.right;
        int rightWidth = Math.min(right.getPreferredSize().width, width);
        if (sideBySide(toolbar, toolbar.getWidth())) {
            int height = toolbar.getHeight() - insets.top - insets.bottom;
            left.setBounds(insets.left, insets.top, width - rightWidth - GAP, height);
            right.setBounds(insets.left + width - rightWidth, insets.top, rightWidth, height);
        } else {
            int leftHeight = left.getPreferredSize().height;
            left.setBounds(insets.left, insets.top, width, leftHeight);
            right.setBounds(
                insets.left + width - rightWidth,
                insets.top + leftHeight,
                rightWidth,
                right.getPreferredSize().height
            );
        }
    }

    @Override
    public void addLayoutComponent(String name, Component component) {}

    @Override
    public void removeLayoutComponent(Component component) {}
}
