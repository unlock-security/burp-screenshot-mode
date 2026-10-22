package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.Disposition;
import it.unlocksecurity.burp.screenshotmode.model.HeightMode;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
import it.unlocksecurity.burp.screenshotmode.render.MessageData;
import it.unlocksecurity.burp.screenshotmode.render.Palette;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import java.awt.Font;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class PreviewAreaTest {

    /** A preview 700 px wide, like a laptop window with the sidebar open. */
    private static JScrollPane preview(PreviewArea area) {
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(null);
        scroll.setSize(700, 500);
        scroll.doLayout();
        scroll.getViewport().doLayout();
        area.doLayout();
        return scroll;
    }

    @Test
    void aFixedWidthWiderThanThePreviewKeepsItsWidthAndScrolls() {
        ContentPanel content = new ContentPanel();
        PreviewArea area = new PreviewArea(content);
        area.setWidth(WidthMode.PIXEL, 1200);
        preview(area);

        assertEquals(1200, content.getWidth());
        assertFalse(area.getScrollableTracksViewportWidth());
        assertTrue(area.getWidth() >= 1200);
    }

    @Test
    void aFixedWidthThatFitsIsCentred() {
        ContentPanel content = new ContentPanel();
        PreviewArea area = new PreviewArea(content);
        area.setWidth(WidthMode.PIXEL, 400);
        preview(area);

        assertEquals(400, content.getWidth());
        assertEquals(150, content.getX());
        assertTrue(area.getScrollableTracksViewportWidth());
    }

    @Test
    void fullWidthFollowsThePreview() {
        ContentPanel content = new ContentPanel();
        PreviewArea area = new PreviewArea(content);
        preview(area);

        assertEquals(700 - 2 * 16, content.getWidth());
        assertTrue(area.getScrollableTracksViewportWidth());
    }

    /**
     * What {@link PreviewArea#settle} does in a window: lay out, measure, again until it fits. Off screen, Swing's
     * {@code validate} does nothing, so the layout is done by hand.
     */
    private static void settle(PreviewArea area) {
        for (int i = 0; i < 5; i++) {
            RenderPreviewTest.layoutTree(area.getParent().getParent());
            // Until painted, a split pane gives each side its preferred size rather than following the divider.
            area.paint(new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB).getGraphics());
            if (!area.fit()) {
                break;
            }
        }
        RenderPreviewTest.layoutTree(area.getParent().getParent());
    }

    private static final Font FONT = new Font(Font.MONOSPACED, Font.PLAIN, 13);

    /** A request and a response far taller than the 500 px preview. */
    private static ContentPanel rendered(Disposition disposition) {
        return rendered(
            disposition,
            "POST / HTTP/1.1\nHost: x\n\n" + "a=1\n".repeat(60),
            "HTTP/1.1 200 OK\n\n" + "line\n".repeat(90)
        );
    }

    private static ContentPanel rendered(Disposition disposition, String request, String response) {
        Settings settings = BuiltInTemplates.defaults();
        settings.disposition = disposition;
        ContentPanel content = new ContentPanel();
        content.render(
            new MessageData("k", "https://x/", request, response, 0, null, null),
            settings,
            Palette.LIGHT,
            FONT,
            FONT,
            null,
            null
        );
        return content;
    }

    @Test
    void fitsBothMessagesSideBySide() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ContentPanel content = rendered(Disposition.HORIZONTAL);
            PreviewArea area = new PreviewArea(content);
            preview(area);
            area.setHeight(HeightMode.CONTENT, 0);
            settle(area);

            assertTrue(content.getHeight() > 500, "taller than the preview: " + content.getHeight());
            assertEquals(0, Math.max(content.requestPane().overflow(), content.responsePane().overflow()));
        });
    }

    @Test
    void fitsBothMessagesOneAboveTheOther() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ContentPanel content = rendered(Disposition.VERTICAL);
            PreviewArea area = new PreviewArea(content);
            preview(area);
            area.setHeight(HeightMode.CONTENT, 0);
            settle(area);

            assertEquals(0, content.requestPane().overflow(), "the request shows whole");
            assertEquals(0, content.responsePane().overflow(), "the response shows whole");
        });
    }

    @Test
    void aCustomHeightIsExact() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ContentPanel content = rendered(Disposition.HORIZONTAL);
            PreviewArea area = new PreviewArea(content);
            preview(area);
            area.setHeight(HeightMode.PIXEL, 640);
            settle(area);

            assertEquals(640, content.getHeight());
        });
    }

    @Test
    void fitsTheWidthToTheLongestLines() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            String longLine = "x".repeat(90);
            ContentPanel content = rendered(
                Disposition.HORIZONTAL,
                "GET /" + longLine + " HTTP/1.1\nHost: x\n",
                "HTTP/1.1 200 OK\n\nshort\n"
            );
            PreviewArea area = new PreviewArea(content);
            preview(area);
            area.setWidth(WidthMode.CONTENT, null);
            settle(area);

            assertTrue(content.getWidth() > 700, "wider than the preview: " + content.getWidth());
            assertEquals(1, lines(content.requestPane()), "the request line does not wrap");
            assertTrue(
                content.requestPane().getWidth() > 2 * content.responsePane().getWidth(),
                "the split gives each message the width it needs"
            );
            assertFalse(area.getScrollableTracksViewportWidth(), "scrolls sideways");
        });
    }

    @Test
    void aFittedWidthStopsAtTheLargestCustomWidth() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ContentPanel content = rendered(
                Disposition.VERTICAL,
                "GET /" + "x".repeat(2000) + " HTTP/1.1\n",
                "HTTP/1.1 200 OK\n"
            );
            PreviewArea area = new PreviewArea(content);
            preview(area);
            area.setWidth(WidthMode.CONTENT, null);
            settle(area);

            assertEquals(Settings.MAX_PIXEL_WIDTH, content.getWidth(), "then the line wraps");
        });
    }

    /** How many rows the first line of a pane takes. */
    private static int lines(MessagePane pane) {
        MessageView view = viewOf(pane);
        try {
            java.awt.geom.Rectangle2D first = view.modelToView2D(0);
            int end = view.getText().indexOf('\n');
            java.awt.geom.Rectangle2D last = view.modelToView2D(end);
            return (int) Math.round((last.getY() - first.getY()) / first.getHeight()) + 1;
        } catch (javax.swing.text.BadLocationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static MessageView viewOf(java.awt.Container container) {
        for (java.awt.Component child : container.getComponents()) {
            if (child instanceof MessageView view) {
                return view;
            }
            if (child instanceof java.awt.Container inner && viewOf(inner) != null) {
                return viewOf(inner);
            }
        }
        return null;
    }
}
