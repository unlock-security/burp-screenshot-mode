package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Dimension;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class WrapLayoutTest {

    @Test
    void reportsTheHeightOfEveryRow() {
        JPanel panel = panel(new WrapLayout(0, 0));
        panel.setSize(250, 20);
        assertEquals(new Dimension(200, 40), panel.getPreferredSize(), "two rows of two and one");
        panel.setSize(400, 20);
        assertEquals(new Dimension(300, 20), panel.getPreferredSize(), "one row when it fits");
    }

    @Test
    void canPreferOneRowAndShrinkToItsWidestComponent() {
        JPanel panel = panel(new WrapLayout(0, 0, true));
        panel.setSize(250, 20);
        assertEquals(300, panel.getPreferredSize().width, "so a form grid gives it the room to unwrap");
        assertEquals(40, panel.getPreferredSize().height, "wrapped at the width it has");
        assertEquals(100, panel.getMinimumSize().width);
    }

    @Test
    void asksForANewLayoutWhenItsRowsNeedAnotherHeight() throws Exception {
        JPanel parent = new JPanel(null);
        JPanel panel = panel(new WrapLayout(0, 0));
        parent.add(panel);
        SwingUtilities.invokeAndWait(() -> {
            panel.setBounds(0, 0, 250, 20);
            panel.validate();
        });
        SwingUtilities.invokeAndWait(() ->
            assertEquals(false, panel.isValid(), "the second row is cut off: lay out again")
        );
    }

    /**
     * Lays {@code root} out as Swing does in a window, where wrapping rows ask for a second layout once they know their
     * width. Headless components have no peer, so {@code validate()} does nothing: lay the tree out by hand, twice.
     */
    static void layOut(java.awt.Container root) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            RenderPreviewTest.layoutTree(root);
            RenderPreviewTest.layoutTree(root);
        });
    }

    /** Whether {@code component} lies whole inside each of its ancestors up to {@code root}. */
    static boolean inView(java.awt.Component component, java.awt.Container root) {
        for (java.awt.Component c = component; c != root && c.getParent() != null; c = c.getParent()) {
            java.awt.Container parent = c.getParent();
            if (
                c.getWidth() <= 0 ||
                c.getX() < 0 ||
                c.getY() < 0 ||
                c.getX() + c.getWidth() > parent.getWidth() ||
                c.getY() + c.getHeight() > parent.getHeight()
            ) {
                return false;
            }
        }
        return true;
    }

    /** Three components 100 × 20 px. */
    private static JPanel panel(WrapLayout layout) {
        JPanel panel = new JPanel(layout);
        for (int i = 0; i < 3; i++) {
            JPanel box = new JPanel();
            box.setPreferredSize(new Dimension(100, 20));
            box.setMinimumSize(new Dimension(100, 20));
            panel.add(box);
        }
        return panel;
    }
}
