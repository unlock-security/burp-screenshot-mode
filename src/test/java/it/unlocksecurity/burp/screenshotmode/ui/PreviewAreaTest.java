package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JScrollPane;
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
        area.setMaxContentWidth(1200);
        preview(area);

        assertEquals(1200, content.getWidth());
        assertFalse(area.getScrollableTracksViewportWidth());
        assertTrue(area.getWidth() >= 1200);
    }

    @Test
    void aFixedWidthThatFitsIsCentred() {
        ContentPanel content = new ContentPanel();
        PreviewArea area = new PreviewArea(content);
        area.setMaxContentWidth(400);
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
}
