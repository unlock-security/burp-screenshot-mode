package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.render.Decorated;
import it.unlocksecurity.burp.screenshotmode.render.HttpSyntax;
import it.unlocksecurity.burp.screenshotmode.render.Palette;
import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.util.List;
import org.junit.jupiter.api.Test;

class MessageViewTest {

    private static final int WIDTH = 300;

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
