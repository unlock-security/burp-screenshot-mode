package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.render.MessageData;
import it.unlocksecurity.burp.screenshotmode.render.Palette;
import it.unlocksecurity.burp.screenshotmode.storage.DefaultTemplate;
import java.awt.Font;
import java.lang.reflect.InvocationTargetException;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ContentPanelTest {

    private static final Font FONT = new Font(Font.MONOSPACED, Font.PLAIN, 13);

    private static String renderedUrl(String url, Settings settings) throws Throwable {
        String[] text = new String[1];
        onEdt(() -> {
            ContentPanel content = new ContentPanel();
            MessageData data = new MessageData("key", url, "GET / HTTP/1.1\n", "HTTP/1.1 200 OK\n", 0, null, null);
            content.render(data, settings, Palette.LIGHT, FONT, FONT, null, null);
            text[0] = content.urlView().text();
        });
        return text[0];
    }

    @Test
    void redactsSecretsInTheUrlBarByDefault() throws Throwable {
        assertEquals(
            "https://example.com:443/reset?access_token=[REDACTED]&id=1",
            renderedUrl("https://example.com:443/reset?access_token=abc&id=1", DefaultTemplate.settings())
        );
    }

    @Test
    void appliesOnlyTheRulesMeantForTheUrlBar() throws Throwable {
        Settings settings = new Settings();
        settings.redactions.add(
            RedactionRule.create("needle", RuleTarget.REQUEST, RedactionMode.REPLACE, MatchMode.STRING)
        );
        assertEquals("https://example.com/needle", renderedUrl("https://example.com/needle", settings));

        settings.redactions.getFirst().target = RuleTarget.URL;
        assertEquals("https://example.com/[REDACTED]", renderedUrl("https://example.com/needle", settings));
    }

    private static void onEdt(Runnable test) throws Throwable {
        try {
            SwingUtilities.invokeAndWait(test);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
