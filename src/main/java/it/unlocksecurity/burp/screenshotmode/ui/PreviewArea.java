package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.HeightMode;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;

/**
 * Hosts the {@link ContentPanel}, centred. Its width follows the viewport, fits the longest lines, or is fixed
 * (scrolling sideways when the window is narrower); its height fills the viewport, fits every line, or is fixed. Drag
 * handles on the right and underneath set a custom width and height.
 */
final class PreviewArea extends JPanel implements Scrollable {

    private static final int MARGIN = 16;
    private static final int HANDLE_SIZE = 10;
    /** Fitting stops after this many tries in a row, in case the layout never settles. */
    private static final int MAX_FITS = 5;

    interface DragListener {
        /** @param done whether the drag is over, rather than still going */
        void dragged(int size, boolean done);
    }

    private final ContentPanel content;
    private final Handle widthHandle = new Handle(true);
    private final Handle heightHandle = new Handle(false);
    private WidthMode widthMode = WidthMode.FULL;
    /** The width when it is fixed: custom or a paper size. */
    private Integer fixedWidth;
    private HeightMode heightMode = HeightMode.FULL;
    private int heightPixels = Settings.DEFAULT_PIXEL_HEIGHT;
    /** The width and height that show every line, while fitting to the content. */
    private int fittedWidth;
    private int fittedHeight;
    private boolean fitPending;
    private int fits;
    private DragListener widthListener = (width, done) -> {};
    private DragListener heightListener = (height, done) -> {};

    PreviewArea(ContentPanel content) {
        super(null);
        this.content = content;
        add(content);
        add(widthHandle);
        add(heightHandle);
    }

    void setWidthDragListener(DragListener listener) {
        this.widthListener = listener;
    }

    void setHeightDragListener(DragListener listener) {
        this.heightListener = listener;
    }

    /** @param fixed the width when {@code mode} is a fixed one, as {@link Settings#maxContentWidth} */
    void setWidth(WidthMode mode, Integer fixed) {
        if (mode == widthMode && Objects.equals(fixed, fixedWidth)) {
            return;
        }
        if (mode == WidthMode.CONTENT && widthMode != WidthMode.CONTENT) {
            fittedWidth = 0;
            fits = 0;
        }
        widthMode = mode;
        fixedWidth = fixed;
        revalidate();
        repaint();
    }

    /** @param pixels the height when {@code mode} is {@link HeightMode#PIXEL} */
    void setHeight(HeightMode mode, int pixels) {
        if (mode == heightMode && pixels == heightPixels) {
            return;
        }
        if (mode == HeightMode.CONTENT && heightMode != HeightMode.CONTENT) {
            fittedHeight = content.getHeight();
            fits = 0;
        }
        heightMode = mode;
        heightPixels = pixels;
        content.setFitting(mode == HeightMode.CONTENT);
        revalidate();
        repaint();
    }

    /** Without the drag handles, for a preview that is not captured, such as the template editor's. */
    void hideHandles() {
        widthHandle.setVisible(false);
        heightHandle.setVisible(false);
        revalidate();
    }

    void setHandleColors(Color background, Color grip) {
        for (Handle handle : new Handle[] { widthHandle, heightHandle }) {
            handle.background = background;
            handle.grip = grip;
            handle.repaint();
        }
    }

    @Override
    public void doLayout() {
        int width = contentWidth();
        int x = Math.max(MARGIN, (getWidth() - width) / 2);
        int height = contentHeight();
        content.setBounds(x, MARGIN, width, height);
        widthHandle.setBounds(x + width, MARGIN, HANDLE_SIZE, height);
        heightHandle.setBounds(x, MARGIN + height, width, HANDLE_SIZE);
        if (fitting()) {
            scheduleFit();
        }
    }

    private boolean fitting() {
        return widthMode == WidthMode.CONTENT || heightMode == HeightMode.CONTENT;
    }

    private int contentWidth() {
        return switch (widthMode) {
            case FULL -> fullWidth();
            case CONTENT -> fittedWidth > 0 ? fittedWidth : unwrappedWidth();
            case A4, LETTER, PIXEL -> fixedWidth != null ? fixedWidth : fullWidth();
        };
    }

    private int fullWidth() {
        return Math.max(100, viewportWidth() - 2 * MARGIN);
    }

    /** The width that shows the longest lines whole, within the limits of a custom width. */
    private int unwrappedWidth() {
        return Math.clamp(content.unwrappedWidth(), Settings.MIN_PIXEL_WIDTH, Settings.MAX_PIXEL_WIDTH);
    }

    private int viewportWidth() {
        return getParent() instanceof JViewport viewport ? viewport.getWidth() : getWidth();
    }

    private int contentHeight() {
        return switch (heightMode) {
            case PIXEL -> heightPixels;
            case CONTENT -> fittedHeight > 0 ? fittedHeight : fullHeight();
            case FULL -> fullHeight();
        };
    }

    private int fullHeight() {
        int viewport = getParent() instanceof JViewport parent ? parent.getHeight() : getHeight();
        return Math.max(
            Settings.MIN_PIXEL_HEIGHT,
            viewport - 2 * MARGIN - (heightHandle.isVisible() ? HANDLE_SIZE : 0)
        );
    }

    /**
     * Fits to the content once the panes are laid out at their new size: the lines a pane needs depend on its width,
     * so they can only be measured after a layout.
     */
    private void scheduleFit() {
        if (fitPending) {
            return;
        }
        fitPending = true;
        SwingUtilities.invokeLater(() -> {
            fitPending = false;
            fit();
        });
    }

    /** Finishes fitting to the content now, before a capture, rather than later on the event queue. */
    void settle() {
        for (int i = 0; i < MAX_FITS && fitting(); i++) {
            validateAll();
            if (!fit()) {
                break;
            }
        }
        validateAll();
    }

    private void validateAll() {
        Container root = this;
        while (root.getParent() != null) {
            root = root.getParent();
        }
        root.validate();
    }

    /**
     * One step of fitting to the content, measured on the current layout: the width first, since the lines each pane
     * needs depend on it, then the height.
     *
     * @return whether the size or the split changed
     */
    boolean fit() {
        if (!fitting()) {
            return false;
        }
        content.validate();
        boolean changed = widthMode == WidthMode.CONTENT ? fitWidth() : false;
        if (!changed && heightMode == HeightMode.CONTENT) {
            changed = fitHeight();
        }
        if (!changed) {
            fits = 0;
            return false;
        }
        if (++fits > MAX_FITS) {
            return false;
        }
        revalidate();
        repaint();
        return true;
    }

    private boolean fitWidth() {
        int width = unwrappedWidth();
        boolean changed = width != content.getWidth();
        fittedWidth = width;
        double ratio = content.unwrappedSplitRatio();
        if (ratio >= 0 && content.fitSplitRatio(ratio)) {
            changed = true;
        }
        return changed;
    }

    private boolean fitHeight() {
        int height = content.getHeight();
        MessagePane request = content.requestPane();
        MessagePane response = content.responsePane();
        int requestOverflow = request.isVisible() ? request.overflow() : 0;
        int responseOverflow = response.isVisible() ? response.overflow() : 0;
        int fitted;
        boolean dividerMoved = false;
        if (content.isSideBySide()) {
            fitted = height + Math.max(requestOverflow, responseOverflow);
        } else {
            int requestHeight = request.getHeight() + requestOverflow;
            int responseHeight = response.getHeight() + responseOverflow;
            if (request.isVisible() && response.isVisible()) {
                dividerMoved = content.fitSplitRatio(
                    requestHeight / (double) Math.max(1, requestHeight + responseHeight)
                );
            }
            fitted = height + requestOverflow + responseOverflow;
        }
        fittedHeight = Math.max(Settings.MIN_PIXEL_HEIGHT, fitted);
        return fittedHeight != height || dividerMoved;
    }

    @Override
    public Dimension getPreferredSize() {
        int width = (widthMode == WidthMode.FULL ? 600 : contentWidth()) + 2 * MARGIN;
        return new Dimension(width, contentHeight() + 2 * MARGIN + HANDLE_SIZE);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(16, visibleRect.height - 32);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        // A width wider than the window scrolls sideways rather than shrinking the screenshot.
        return widthMode == WidthMode.FULL || contentWidth() + 2 * MARGIN <= viewportWidth();
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return heightMode == HeightMode.FULL;
    }

    /** Drag handle on the right of the content, which sets its width, or underneath, which sets its height. */
    private final class Handle extends JComponent {

        private final boolean horizontal;
        private Color background = Color.DARK_GRAY;
        private Color grip = Color.GRAY;
        private int dragStart;
        private int dragStartSize;
        /** Where in the handle the drag started, so the content's edge stays that far from the pointer. */
        private int grabbedAt;

        Handle(boolean horizontal) {
            this.horizontal = horizontal;
            setCursor(Cursor.getPredefinedCursor(horizontal ? Cursor.E_RESIZE_CURSOR : Cursor.N_RESIZE_CURSOR));
            setToolTipText(
                horizontal ? "Drag to set the width of the screenshot" : "Drag to set the height of the screenshot"
            );
            MouseAdapter drag = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragStart = e.getYOnScreen();
                    dragStartSize = content.getHeight();
                    grabbedAt = e.getX();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    dragTo(e, false);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    dragTo(e, true);
                }
            };
            addMouseListener(drag);
            addMouseMotionListener(drag);
        }

        private void dragTo(MouseEvent e, boolean done) {
            if (horizontal) {
                int width = Math.clamp(
                    widthAt(SwingUtilities.convertPoint(this, e.getPoint(), PreviewArea.this).x - grabbedAt),
                    Settings.MIN_PIXEL_WIDTH,
                    Settings.MAX_PIXEL_WIDTH
                );
                setWidth(WidthMode.PIXEL, width);
                widthListener.dragged(width, done);
            } else {
                int height = Math.clamp(
                    dragStartSize + e.getYOnScreen() - dragStart,
                    Settings.MIN_PIXEL_HEIGHT,
                    Settings.MAX_PIXEL_HEIGHT
                );
                setHeight(HeightMode.PIXEL, height);
                heightListener.dragged(height, done);
            }
        }

        /**
         * The width that puts the content's right edge at {@code right}: centred while it fits the viewport, so both
         * edges move, otherwise from the left margin.
         */
        private int widthAt(int right) {
            int centred = 2 * right - viewportWidth();
            return centred + 2 * MARGIN <= viewportWidth() ? centred : right - MARGIN;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(background);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(grip);
            if (horizontal) {
                g.fillRoundRect(getWidth() / 2 - 1, getHeight() / 2 - 16, 3, 32, 3, 3);
            } else {
                g.fillRoundRect(getWidth() / 2 - 16, getHeight() / 2 - 1, 32, 3, 3, 3);
            }
            g.dispose();
        }
    }
}
