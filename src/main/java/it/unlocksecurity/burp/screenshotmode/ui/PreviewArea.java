package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.HeightMode;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
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
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;

/**
 * Hosts the {@link ContentPanel}: centred, exactly as wide as the configured width (scrolling sideways when the window
 * is narrower) or as the viewport for full width. Its height fills the viewport, fits the content, or is a number of
 * pixels, set with the drag handle underneath.
 */
final class PreviewArea extends JPanel implements Scrollable {

    private static final int MARGIN = 16;
    private static final int HANDLE_HEIGHT = 10;
    /** Fitting stops after this many tries in a row, in case the layout never settles. */
    private static final int MAX_FITS = 5;

    interface DragListener {
        /** @param done whether the drag is over, rather than still going */
        void dragged(int height, boolean done);
    }

    private final ContentPanel content;
    private final CropHandle handle = new CropHandle();
    private Integer maxContentWidth;
    private HeightMode heightMode = HeightMode.FULL;
    private int heightPixels = Settings.DEFAULT_PIXEL_HEIGHT;
    /** The content height that shows every line, while fitting to the content. */
    private int fittedHeight;
    private boolean fitPending;
    private int fits;
    private DragListener dragListener = (height, done) -> {};

    PreviewArea(ContentPanel content) {
        super(null);
        this.content = content;
        add(content);
        add(handle);
    }

    void setDragListener(DragListener listener) {
        this.dragListener = listener;
    }

    void setMaxContentWidth(Integer width) {
        maxContentWidth = width;
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

    /** Without the drag handle, for a preview that is not captured, such as the template editor's. */
    void hideCropHandle() {
        handle.setVisible(false);
        revalidate();
    }

    void setHandleColors(Color background, Color grip) {
        handle.background = background;
        handle.grip = grip;
        handle.repaint();
    }

    @Override
    public void doLayout() {
        int width = contentWidth();
        int x = Math.max(MARGIN, (getWidth() - width) / 2);
        int height = contentHeight();
        content.setBounds(x, MARGIN, width, height);
        handle.setBounds(x, MARGIN + height, width, HANDLE_HEIGHT);
        if (heightMode == HeightMode.CONTENT) {
            scheduleFit();
        }
    }

    /** The configured width, whatever the window: only full width follows the viewport. */
    private int contentWidth() {
        return maxContentWidth != null ? maxContentWidth : Math.max(100, viewportWidth() - 2 * MARGIN);
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
        return Math.max(Settings.MIN_PIXEL_HEIGHT, viewport - 2 * MARGIN - (handle.isVisible() ? HANDLE_HEIGHT : 0));
    }

    /**
     * Fits the height to the content once the panes are laid out at their new size: the lines a pane needs depend on
     * its width, so it can only be measured after a layout.
     */
    private void scheduleFit() {
        if (fitPending) {
            return;
        }
        fitPending = true;
        SwingUtilities.invokeLater(() -> {
            fitPending = false;
            if (heightMode == HeightMode.CONTENT) {
                fit();
            }
        });
    }

    /** Finishes fitting to the content now, before a capture, rather than later on the event queue. */
    void settle() {
        for (int i = 0; i < MAX_FITS && heightMode == HeightMode.CONTENT; i++) {
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

    /** One step of fitting to the content, measured on the current layout: @return whether the height changed */
    boolean fit() {
        if (heightMode != HeightMode.CONTENT) {
            return false;
        }
        content.validate();
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
        fitted = Math.max(Settings.MIN_PIXEL_HEIGHT, fitted);
        if (fitted == height && !dividerMoved) {
            fits = 0;
            return false;
        }
        if (++fits > MAX_FITS) {
            return false;
        }
        fittedHeight = fitted;
        revalidate();
        repaint();
        return true;
    }

    @Override
    public Dimension getPreferredSize() {
        int width = (maxContentWidth == null ? 600 : maxContentWidth) + 2 * MARGIN;
        return new Dimension(width, contentHeight() + 2 * MARGIN + HANDLE_HEIGHT);
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
        // A fixed width wider than the window scrolls sideways rather than shrinking the screenshot.
        return maxContentWidth == null || maxContentWidth + 2 * MARGIN <= viewportWidth();
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return heightMode == HeightMode.FULL;
    }

    /** Drag handle below the content that sets the captured height. */
    private final class CropHandle extends JComponent {

        private Color background = Color.DARK_GRAY;
        private Color grip = Color.GRAY;
        private int dragStartY;
        private int dragStartHeight;

        CropHandle() {
            setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
            setToolTipText("Drag to set the height of the screenshot");
            MouseAdapter drag = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragStartY = e.getYOnScreen();
                    dragStartHeight = content.getHeight();
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
            int height = Math.clamp(
                dragStartHeight + e.getYOnScreen() - dragStartY,
                Settings.MIN_PIXEL_HEIGHT,
                Settings.MAX_PIXEL_HEIGHT
            );
            setHeight(HeightMode.PIXEL, height);
            dragListener.dragged(height, done);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(background);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(grip);
            g.fillRoundRect(getWidth() / 2 - 16, getHeight() / 2 - 1, 32, 3, 3, 3);
            g.dispose();
        }
    }
}
