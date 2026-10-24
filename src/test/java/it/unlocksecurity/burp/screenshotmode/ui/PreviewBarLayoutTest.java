package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class PreviewBarLayoutTest {

    private final JPanel size = box(150);
    private final JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
    private final JPanel right = new JPanel(new WrapLayout(12, 0, true));
    private final JPanel bar = new JPanel(new ScreenshotDialog.PreviewBarLayout(left, right));
    private final JPanel column = new JPanel(new BorderLayout());

    PreviewBarLayoutTest() {
        left.add(size);
        right.add(box(150));
        right.add(box(250));
        bar.add(left);
        bar.add(right);
        column.add(bar, BorderLayout.NORTH);
    }

    @Test
    void keepsTheWarningsOnTheRightOfTheSizeWhenTheyFit() throws Exception {
        column.setSize(800, 300);
        WrapLayoutTest.layOut(column);
        assertEquals(20, bar.getHeight(), "one row");
        assertEquals(0, right.getY());
        assertEquals(800, right.getX() + right.getWidth(), "on the right");
    }

    @Test
    void movesTheWarningsBelowTheSizeRatherThanCuttingIt() throws Exception {
        column.setSize(500, 300);
        WrapLayoutTest.layOut(column);
        assertEquals(150, size.getWidth(), "the size whole");
        assertEquals(20, right.getY(), "the warnings on a second row");
        assertEquals(40, bar.getHeight());

        column.setSize(300, 300);
        WrapLayoutTest.layOut(column);
        assertEquals(60, bar.getHeight(), "the size, then one warning per line when a row can't hold both");
        assertTrue(WrapLayoutTest.inView(right.getComponent(1), column));
    }

    @Test
    void neverAsksForMoreThanTheSizeAsItsMinimum() {
        // Otherwise the split pane would push the sidebar aside to make room for the warnings.
        assertEquals(150 + 24, bar.getMinimumSize().width);
    }

    private static JPanel box(int width) {
        JPanel box = new JPanel();
        box.setPreferredSize(new Dimension(width, 20));
        box.setMinimumSize(new Dimension(width, 20));
        return box;
    }
}
