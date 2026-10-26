package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class ToolbarLayoutTest {

    private final JPanel left = new JPanel(new WrapLayout(0, 0, true));
    private final JPanel right = new JPanel(new WrapLayout(0, 0, true));
    private final JPanel toolbar = new JPanel(new ToolbarLayout(left, right));
    private final JPanel column = new JPanel(new BorderLayout());

    ToolbarLayoutTest() {
        left.add(box(200));
        left.add(box(100));
        ((WrapLayout) right.getLayout()).setAlignment(FlowLayout.RIGHT);
        right.add(box(100));
        right.add(box(150));
        toolbar.add(left);
        toolbar.add(right);
        column.add(toolbar, BorderLayout.NORTH);
    }

    @Test
    void keepsBothPartsOnOneRowWhenTheyFit() throws Exception {
        column.setSize(700, 300);
        WrapLayoutTest.layOut(column);
        assertEquals(20, toolbar.getHeight());
        assertEquals(0, right.getY());
        assertEquals(700, right.getX() + right.getWidth(), "on the right");
    }

    @Test
    void wrapsTheLeftPartBesideTheRightOneWhileItsWidestControlFits() throws Exception {
        column.setSize(500, 300);
        WrapLayoutTest.layOut(column);
        assertEquals(0, right.getY(), "the right part stays put");
        assertEquals(500, right.getX() + right.getWidth());
        assertEquals(20, left.getComponent(1).getY(), "the left part wraps");
        assertEquals(40, toolbar.getHeight());
    }

    @Test
    void movesTheRightPartBelowRatherThanSqueezingTheLeft() throws Exception {
        column.setSize(440, 300);
        WrapLayoutTest.layOut(column);
        assertEquals(440, left.getWidth(), "the left part keeps the whole row");
        assertEquals(20, right.getY(), "the right part below it");
        assertEquals(440, right.getX() + right.getWidth(), "still on the right");
        assertEquals(40, toolbar.getHeight());
    }

    @Test
    void wrapsInsideEachPartWhenEvenARowOfItsOwnIsTooNarrow() throws Exception {
        column.setSize(220, 300);
        WrapLayoutTest.layOut(column);
        assertEquals(80, toolbar.getHeight(), "two rows for each part");
        for (java.awt.Component box : new java.awt.Component[] {
            left.getComponent(0),
            left.getComponent(1),
            right.getComponent(0),
            right.getComponent(1),
        }) {
            assertTrue(WrapLayoutTest.inView(box, column));
        }
        assertEquals(
            220,
            right.getComponent(1).getX() + right.getComponent(1).getWidth() + right.getX(),
            "right-aligned"
        );
    }

    @Test
    void movesTheRightPartBelowRatherThanSplittingAGroupOnTheLeft() throws Exception {
        JPanel group = new JPanel(new WrapLayout(0, 0, true));
        for (int i = 0; i < 3; i++) {
            group.add(box(100));
        }
        left.add(group);
        // 300 px beside the right part's 250 needs 562 px.
        column.setSize(500, 300);
        WrapLayoutTest.layOut(column);
        assertEquals(300, group.getWidth(), "the group whole");
        assertEquals(40, right.getY(), "the right part below the left one");
    }

    @Test
    void neverAsksForMoreThanItsWidestControlAsItsMinimum() {
        assertEquals(200, toolbar.getMinimumSize().width);
    }

    private static JPanel box(int width) {
        JPanel box = new JPanel();
        box.setPreferredSize(new Dimension(width, 20));
        box.setMinimumSize(new Dimension(width, 20));
        return box;
    }
}
