package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.render.Decorated;
import it.unlocksecurity.burp.screenshotmode.render.HttpSyntax;
import it.unlocksecurity.burp.screenshotmode.render.Palette;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseWheelEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MessagePaneTest {

    private static final Font FONT = new Font(Font.MONOSPACED, Font.PLAIN, 13);

    /** A message pane of the given height inside a taller, scrollable preview, like after "Fit to Content". */
    private record Setup(JScrollPane outer, JScrollPane inner) {
        static Setup of(int lines, int paneHeight) {
            String text = "HTTP/1.1 200 OK\n\n" + "line\n".repeat(lines);
            MessagePane pane = new MessagePane("Response", RuleTarget.RESPONSE);
            pane.render(new Decorated(text, List.of()), HttpSyntax.tokenize(text, false), Palette.LIGHT, FONT, FONT);
            pane.setPreferredSize(new Dimension(400, paneHeight));
            JPanel holder = new JPanel(new BorderLayout());
            holder.add(pane, BorderLayout.NORTH);
            holder.setPreferredSize(new Dimension(400, 3000));
            JScrollPane outer = new JScrollPane(holder);
            outer.setSize(420, 300);
            RenderPreviewTest.layoutTree(outer);
            RenderPreviewTest.layoutTree(outer);
            JScrollPane inner = null;
            for (Component child : pane.getComponents()) {
                if (child instanceof JScrollPane scroll) {
                    inner = scroll;
                }
            }
            return new Setup(outer, inner);
        }

        void wheel(int rotation) {
            inner.dispatchEvent(
                new MouseWheelEvent(
                    inner,
                    MouseWheelEvent.MOUSE_WHEEL,
                    0,
                    0,
                    10,
                    10,
                    0,
                    false,
                    MouseWheelEvent.WHEEL_UNIT_SCROLL,
                    3,
                    rotation
                )
            );
        }

        int outerY() {
            return outer.getViewport().getViewPosition().y;
        }

        int innerY() {
            return inner.getViewport().getViewPosition().y;
        }
    }

    @Test
    void passesWheelToThePreviewWhenTheMessageFits() throws Throwable {
        onEdt(() -> {
            Setup setup = Setup.of(3, 250);
            assertTrue(!setup.inner().getVerticalScrollBar().isVisible(), "message fits, no inner scrollbar");
            setup.wheel(1);
            assertTrue(setup.outerY() > 0, "the preview scrolled");
        });
    }

    @Test
    void scrollsTheMessageFirstThenThePreview() throws Throwable {
        onEdt(() -> {
            Setup setup = Setup.of(100, 250);
            setup.wheel(1);
            assertTrue(setup.innerY() > 0, "the message scrolled");
            assertEquals(0, setup.outerY(), "the preview did not");

            for (int i = 0; i < 200; i++) {
                setup.wheel(1);
            }
            assertTrue(setup.outerY() > 0, "at the bottom of the message, the preview scrolls");
        });
    }

    @Test
    void findsTheHeaderOnTheLineUnderThePointer() {
        String text = "GET / HTTP/1.1\nHost: x\nX-Backend: blue\n\nX-Body: no";
        assertEquals("Host", MessagePane.headerAt(text, text.indexOf("x\n")));
        assertEquals("X-Backend", MessagePane.headerAt(text, text.indexOf("blue") + 4), "at the end of the line");
        assertNull(MessagePane.headerAt(text, 2), "the start line");
        assertNull(MessagePane.headerAt(text, text.indexOf("X-Body")), "the body");
        assertNull(MessagePane.headerAt(text, -1));
    }

    /**
     * Swing components must be used on the event dispatch thread: laid out from the test thread, they can deadlock
     * with the EDT on the tree lock when a display is available (-PpreviewUi).
     */
    private static void onEdt(Runnable test) throws Throwable {
        try {
            SwingUtilities.invokeAndWait(test);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
