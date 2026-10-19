package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.Settings;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class WidgetsTest {

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
        Settings settings = Settings.defaults();
        settings.highlights.clear();
        settings.redactions.clear();
        List<String> html = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 13);
            collectHtml(new SettingsForm(settings, () -> {}, mono, false), html);
            collectHtml(new SettingsForm(settings, () -> {}, mono, true), html);
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
}
