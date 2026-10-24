package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.render.Palette;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

class WidgetsTest {

    private static final List<Rectangle> SCREENS = List.of(
        new Rectangle(0, 0, 1920, 1080),
        new Rectangle(1920, 0, 1280, 800)
    );

    @Test
    void reopensWhereTheWindowWasClosed() {
        assertEquals(new Rectangle(100, 50, 1200, 900), Widgets.boundsOnScreen("100,50,1200,900", SCREENS));
        assertEquals(
            new Rectangle(2000, 10, 1000, 700),
            Widgets.boundsOnScreen("2000,10,1000,700", SCREENS),
            "on the second screen"
        );
    }

    @Test
    void usesTheDefaultBoundsWhenTheSavedOnesDontFit() {
        assertNull(Widgets.boundsOnScreen("3500,0,1000,700", SCREENS), "its screen was unplugged");
        assertNull(Widgets.boundsOnScreen("1500,0,1000,700", SCREENS), "across two screens");
        assertNull(Widgets.boundsOnScreen("0,0,0,0", SCREENS));
        assertNull(Widgets.boundsOnScreen("100,50", SCREENS));
        assertNull(Widgets.boundsOnScreen("left,top,wide,tall", SCREENS));
    }

    @Test
    void keepsTextColorsReadableAndFollowsThemeSwitches() {
        // Burp Dark: its disabled grey is too faint for hints (3.25:1).
        String[] keys = { "Panel.background", "Label.foreground", "Label.disabledForeground", "Colors.ui.text.error" };
        try {
            UIManager.put("Panel.background", new Color(0x323334));
            UIManager.put("Label.foreground", new Color(0xc5c7c8));
            UIManager.put("Label.disabledForeground", new Color(0x818181));
            UIManager.put("Colors.ui.text.error", new Color(0xee95a1));
            Color background = new Color(0x323334);
            assertTrue(Palette.contrast(Widgets.mutedColor(), background) >= 4.5);
            assertEquals(
                0xee95a1,
                Widgets.errorColor().getRGB() & 0xffffff,
                "the theme's own color when it is readable"
            );

            // Burp Light: the same color objects, as components keep them across a theme switch.
            UIManager.put("Panel.background", new Color(0xfbfbfb));
            UIManager.put("Label.foreground", new Color(0x2b2b2b));
            UIManager.put("Label.disabledForeground", new Color(0x4b4b4b));
            UIManager.put("Colors.ui.text.error", new Color(0xd6213f));
            assertEquals(0x4b4b4b, Widgets.mutedColor().getRGB() & 0xffffff);
            assertEquals(0xd6213f, Widgets.errorColor().getRGB() & 0xffffff);
        } finally {
            for (String key : keys) {
                UIManager.put(key, null);
            }
        }
    }

    @Test
    void clearsASearchFieldWithEscapeAndLetsEscapeThroughWhenEmpty() {
        javax.swing.JTextField field = new javax.swing.JTextField();
        Widgets.searchField(field);
        assertEquals(Widgets.Glyph.SEARCH, field.getClientProperty("JTextField.leadingIcon"));
        assertEquals(Boolean.TRUE, field.getClientProperty("JTextField.showClearButton"));

        javax.swing.Action clear = field
            .getActionMap()
            .get(field.getInputMap().get(javax.swing.KeyStroke.getKeyStroke("ESCAPE")));
        field.setText("cookie");
        assertTrue(clear.accept(field));
        clear.actionPerformed(null);
        assertEquals("", field.getText());
        assertTrue(!clear.accept(field), "an empty field leaves Escape to the window");
    }

    @Test
    void offersAFewNamedColorsAndKeepsACustomOne() {
        List<String> chosen = new ArrayList<>();
        javax.swing.JComboBox<Widgets.ColorChoice> combo = Widgets.colorCombo(
            Widgets.ColorChoice.HIGHLIGHT,
            "#FFFF00",
            chosen::add
        );
        assertEquals("Yellow", ((Widgets.ColorChoice) combo.getSelectedItem()).name(), "ignoring case");
        assertEquals(Widgets.ColorChoice.HIGHLIGHT.size(), combo.getItemCount());
        combo.setSelectedIndex(2);
        assertEquals(List.of("#f44336"), chosen);

        javax.swing.JComboBox<Widgets.ColorChoice> custom = Widgets.colorCombo(
            Widgets.ColorChoice.HIGHLIGHT,
            "#123456",
            value -> {}
        );
        Widgets.ColorChoice kept = (Widgets.ColorChoice) custom.getSelectedItem();
        assertEquals(new Widgets.ColorChoice("Custom", "#123456"), kept);
        assertEquals(Widgets.ColorChoice.HIGHLIGHT.size() + 1, custom.getItemCount());
    }

    @Test
    void offersTheDefaultColorOfNewHighlights() {
        assertTrue(
            Widgets.ColorChoice.HIGHLIGHT.stream().anyMatch(c ->
                c.hex().equalsIgnoreCase(it.unlocksecurity.burp.screenshotmode.model.HighlightRule.DEFAULT_COLOR)
            )
        );
    }

    @Test
    void wrapsHintsToTheWidthTheyAreGiven() {
        String text = "No highlights yet. Add one with + or right-click selected text in the preview.";
        JTextArea hint = Widgets.wrappingHint(text, 260);
        assertEquals(text, hint.getText());
        assertTrue(hint.getPreferredSize().width <= 260, "never widens its column");

        int lineHeight = hint.getFontMetrics(hint.getFont()).getHeight();
        hint.setSize(2000, 500);
        assertEquals(1, hint.getPreferredSize().height / lineHeight, "one line when there is room");
        hint.setSize(150, 500);
        assertTrue(hint.getPreferredSize().height / lineHeight > 1, "wraps when narrow");
    }

    /** Burp shows the markup of {@code <html>} labels instead of rendering it. */
    @Test
    void settingsAndTemplatesUseNoHtmlLabels() throws Exception {
        Settings settings = BuiltInTemplates.defaults();
        settings.highlights.clear();
        settings.redactions.clear();
        List<String> html = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 13);
            collectHtml(new SettingsForm(settings, () -> {}, mono), html);
            collectHtml(new TemplatesTab(TemplatesTabTest.store(), TemplatesTabTest.host()), html);
        });
        assertTrue(html.isEmpty(), "HTML texts: " + html);
    }

    private static void collectHtml(Container root, List<String> html) {
        for (Component child : root.getComponents()) {
            String text =
                child instanceof JLabel label
                    ? label.getText()
                    : child instanceof AbstractButton button
                      ? button.getText()
                      : null;
            if (text != null && text.stripLeading().toLowerCase(Locale.ROOT).startsWith("<html")) {
                html.add(text);
            }
            if (child instanceof JComponent component && component.getToolTipText() != null) {
                if (component.getToolTipText().toLowerCase(Locale.ROOT).startsWith("<html")) {
                    html.add(component.getToolTipText());
                }
            }
            if (child instanceof Container container) {
                collectHtml(container, html);
            }
        }
    }

    @Test
    void aWrappingFieldHoldsOneLineAndTakesItsWidthFromTheLayout() {
        javax.swing.JTextArea field = Widgets.wrappingField("short", 12);
        int width = field.getPreferredSize().width;
        field.setText("a much, much longer value than the twelve columns the field asks for in its layout");
        assertEquals(width, field.getPreferredSize().width, "wraps rather than widening the form");

        field.setText("one\ntwo\r\nthree");
        assertEquals("onetwothree", field.getText());
        // Tab moves the focus on, instead of typing a tab.
        assertTrue(
            field
                .getFocusTraversalKeys(java.awt.KeyboardFocusManager.FORWARD_TRAVERSAL_KEYS)
                .contains(java.awt.AWTKeyStroke.getAWTKeyStroke(java.awt.event.KeyEvent.VK_TAB, 0))
        );
    }
}
