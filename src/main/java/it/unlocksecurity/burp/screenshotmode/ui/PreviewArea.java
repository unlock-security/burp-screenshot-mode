package it.unlocksecurity.burp.screenshotmode.ui;

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
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;

/**
 * Hosts the {@link ContentPanel}: centred, exactly as wide as the configured width (scrolling sideways when the window
 * is narrower) or as the viewport for full width, and as tall as the viewport unless cropped with the drag handle
 * underneath.
 */
final class PreviewArea extends JPanel implements Scrollable {

    static final int MIN_HEIGHT = 50;
    private static final int MARGIN = 16;
    private static final int HANDLE_HEIGHT = 10;

    private final ContentPanel content;
    private final CropHandle handle = new CropHandle();
    private Integer maxContentWidth;
    private Integer cropHeight;
    private Consumer<Integer> cropListener = height -> {};

    PreviewArea(ContentPanel content) {
        super(null);
        this.content = content;
        add(content);
        add(handle);
    }

    void setCropListener(Consumer<Integer> listener) {
        this.cropListener = listener;
    }

    void setMaxContentWidth(Integer width) {
        maxContentWidth = width;
        revalidate();
        repaint();
    }

    /** {@code null} removes the crop and fills the available height again. */
    void setCropHeight(Integer height) {
        cropHeight = height == null ? null : Math.max(MIN_HEIGHT, height);
        revalidate();
        repaint();
        cropListener.accept(cropHeight);
    }

    Integer cropHeight() {
        return cropHeight;
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
    }

    /** The configured width, whatever the window: only full width follows the viewport. */
    private int contentWidth() {
        return maxContentWidth != null ? maxContentWidth : Math.max(100, viewportWidth() - 2 * MARGIN);
    }

    private int viewportWidth() {
        return getParent() instanceof JViewport viewport ? viewport.getWidth() : getWidth();
    }

    private int contentHeight() {
        if (cropHeight != null) {
            return cropHeight;
        }
        Container parent = getParent();
        int viewport = parent instanceof JViewport ? parent.getHeight() : getHeight();
        return Math.max(MIN_HEIGHT, viewport - 2 * MARGIN - (handle.isVisible() ? HANDLE_HEIGHT : 0));
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
        return cropHeight == null;
    }

    /** Drag handle below the content that adjusts the captured height. */
    private final class CropHandle extends JComponent {

        private Color background = Color.DARK_GRAY;
        private Color grip = Color.GRAY;
        private int dragStartY;
        private int dragStartHeight;

        CropHandle() {
            setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
            setToolTipText("Drag to adjust content height");
            MouseAdapter drag = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragStartY = e.getYOnScreen();
                    dragStartHeight = cropHeight != null ? cropHeight : content.getHeight();
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    setCropHeight(dragStartHeight + e.getYOnScreen() - dragStartY);
                }
            };
            addMouseListener(drag);
            addMouseMotionListener(drag);
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
