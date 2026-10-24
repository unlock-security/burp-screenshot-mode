package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import org.junit.jupiter.api.Test;

class FooterLayoutTest {

    private final JTextArea text = Widgets.wrappingHint("Based on Defaults · no rules missing", Integer.MAX_VALUE);
    private final JButton button = new JButton("Restore defaults…");
    private final JPanel footer = Widgets.footer(text, button);
    private final JPanel tab = new JPanel(new BorderLayout());
    private final int line = text.getFontMetrics(text.getFont()).stringWidth(text.getText());

    FooterLayoutTest() {
        button.setPreferredSize(new Dimension(120, 30));
        tab.add(footer, BorderLayout.SOUTH);
    }

    @Test
    void keepsTheButtonOnTheRightOfTheTextWhenBothFit() throws Exception {
        tab.setSize(line + 8 + 120, 300);
        WrapLayoutTest.layOut(tab);
        assertEquals(30, footer.getHeight(), "one row");
        assertEquals(text.getWidth(), line, "the text whole");
        assertEquals(footer.getWidth(), button.getX() + button.getWidth(), "on the right");
    }

    @Test
    void movesTheButtonBelowTheTextRatherThanCuttingIt() throws Exception {
        tab.setSize(line + 8 + 119, 300);
        WrapLayoutTest.layOut(tab);
        assertEquals(footer.getWidth(), text.getWidth(), "the text on a line of its own");
        assertEquals(text.getHeight(), button.getY(), "the button below");
        assertEquals(footer.getWidth(), button.getX() + button.getWidth(), "still on the right");
        assertTrue(WrapLayoutTest.inView(button, tab));
    }

    @Test
    void wrapsTheTextWhenALineOfItsOwnIsTooNarrow() throws Exception {
        tab.setSize(line + 8 + 120, 300);
        WrapLayoutTest.layOut(tab);
        int lineHeight = text.getHeight();

        tab.setSize(line / 2, 300);
        WrapLayoutTest.layOut(tab);
        assertTrue(text.getHeight() > lineHeight, "on more lines");
        assertTrue(WrapLayoutTest.inView(text, tab));
        assertTrue(WrapLayoutTest.inView(button, tab));
    }

    @Test
    void neverAsksForMoreThanTheButtonAsItsMinimum() {
        assertEquals(120, footer.getMinimumSize().width);
    }
}
