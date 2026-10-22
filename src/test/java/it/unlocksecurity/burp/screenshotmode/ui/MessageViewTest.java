package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.render.Decorated;
import it.unlocksecurity.burp.screenshotmode.render.HttpSyntax;
import it.unlocksecurity.burp.screenshotmode.render.Palette;
import java.awt.Color;
import java.awt.Font;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.geom.Rectangle2D;
import java.util.List;
import javax.swing.TransferHandler;
import org.junit.jupiter.api.Test;

class MessageViewTest {

    private static final int WIDTH = 300;
    private static final Font FONT = new Font(Font.MONOSPACED, Font.PLAIN, 13);

    /** Copies the selection through the view's transfer handler, as Copy and drag do, into a private clipboard. */
    private static String copy(MessageView view, int from, int to) throws Exception {
        view.select(from, to);
        Clipboard clipboard = new Clipboard("test");
        view.getTransferHandler().exportToClipboard(view, clipboard, TransferHandler.COPY);
        return (String) clipboard.getData(DataFlavor.stringFlavor);
    }

    @Test
    void copiesBlurredAndCoveredTextAsAPlaceholder() throws Exception {
        String text = "Cookie: session=s3cr3t; theme=dark\nX-Key: k3y";
        int session = text.indexOf("s3cr3t");
        int key = text.indexOf("k3y");
        MessageView view = new MessageView();
        view.render(
            new Decorated(
                text,
                List.of(
                    new Decorated.Span(session, session + 6, Decorated.Kind.BLUR, null),
                    // Overlapping redactions make one run, with one placeholder.
                    new Decorated.Span(session + 2, session + 6, Decorated.Kind.OPAQUE, Color.BLACK),
                    new Decorated.Span(key, key + 3, Decorated.Kind.OPAQUE, Color.BLACK)
                )
            ),
            List.of(),
            Palette.LIGHT,
            FONT
        );
        assertEquals("Cookie: session=[REDACTED]; theme=dark\nX-Key: [REDACTED]", copy(view, 0, text.length()));
        assertEquals("[REDACTED]; th", copy(view, session + 3, session + 10), "a partly selected run is hidden too");
        assertEquals(
            "theme=dark",
            copy(view, text.indexOf("theme"), text.indexOf("dark") + 4),
            "the rest is copied as is"
        );
    }

    @Test
    void wrapsLongUnbreakableWords() throws Exception {
        String text =
            "HTTP/2 200 OK\nContent-Type: application/json\n\n{\"img\":\"/images/" +
            "3VvbGEubmV0IiwiYXVkIjoiaHR0cHM6Ly9lZHVj".repeat(10) +
            "\"}";
        MessageView view = new MessageView();
        view.render(
            new Decorated(text, List.of()),
            HttpSyntax.tokenize(text, false),
            Palette.LIGHT,
            new Font(Font.MONOSPACED, Font.PLAIN, 13)
        );
        view.setSize(WIDTH, Short.MAX_VALUE);
        view.setSize(WIDTH, view.getPreferredSize().height);

        for (int i = 0; i <= text.length(); i++) {
            Rectangle2D caret = view.modelToView2D(i);
            assertTrue(caret.getMaxX() <= WIDTH, "offset " + i + " is painted at x=" + caret.getX());
        }
    }
}
