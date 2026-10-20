package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.UrlMode;
import it.unlocksecurity.burp.screenshotmode.render.MessageData;
import it.unlocksecurity.burp.screenshotmode.render.Palette;
import it.unlocksecurity.burp.screenshotmode.render.RuleStatus;
import it.unlocksecurity.burp.screenshotmode.storage.DefaultTemplate;
import java.awt.Font;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ContentPanelTest {

    private static final Font FONT = new Font(Font.MONOSPACED, Font.PLAIN, 13);

    private static ContentPanel rendered(String url, Settings settings) throws Throwable {
        ContentPanel[] content = new ContentPanel[1];
        onEdt(() -> {
            content[0] = new ContentPanel();
            MessageData data = new MessageData("key", url, "GET / HTTP/1.1\n", "HTTP/1.1 200 OK\n", 0, null, null);
            content[0].render(data, settings, Palette.LIGHT, FONT, FONT, null, null);
        });
        return content[0];
    }

    private static String renderedUrl(String url, Settings settings) throws Throwable {
        return rendered(url, settings).urlView().text();
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

    @Test
    void showsOnlyThePathAfterTheRulesRan() throws Throwable {
        Settings settings = DefaultTemplate.settings();
        settings.url = UrlMode.PATH;
        ContentPanel content = rendered("https://example.com:443/reset?access_token=abc&id=1", settings);
        assertEquals("/reset?access_token=[REDACTED]&id=1", content.urlView().text());
        assertEquals("Path", content.urlLabel().getText());
        assertTrue(content.urlBar().isVisible());
    }

    @Test
    void neverShowsTextARedactionCrossingIntoThePathCovered() throws Throwable {
        Settings settings = new Settings();
        settings.url = UrlMode.PATH;
        settings.redactions.add(
            RedactionRule.create("example.com/admin", RuleTarget.URL, RedactionMode.REPLACE, MatchMode.STRING)
        );
        String path = renderedUrl("https://example.com/admin/users?id=1", settings);
        assertEquals("/users?id=1", path);
        assertFalse(path.contains("admin"));
    }

    @Test
    void hidesTheUrlBar() throws Throwable {
        Settings settings = DefaultTemplate.settings();
        settings.url = UrlMode.HIDDEN;
        assertFalse(rendered("https://example.com/", settings).urlBar().isVisible());
    }

    @Test
    void recordsWhatEachRuleDidAcrossRequestResponseAndUrl() throws Throwable {
        Settings settings = new Settings();
        RedactionRule url = RedactionRule.create("abc", RuleTarget.URL, RedactionMode.REPLACE, MatchMode.STRING);
        RedactionRule request = RedactionRule.create("GET", RuleTarget.REQUEST, RedactionMode.BLUR, MatchMode.STRING);
        RedactionRule invalid = RedactionRule.create("(", RuleTarget.RESPONSE, RedactionMode.BLUR, MatchMode.REGEX);
        settings.redactions.addAll(List.of(url, request, invalid));
        ContentPanel content = rendered("https://example.com/?t=abc&u=abc", settings);
        assertEquals(2, content.ruleStatuses().get(url).matches());
        assertEquals(1, content.ruleStatuses().get(request).matches());
        assertTrue(content.ruleStatuses().get(invalid).failed());
    }

    @Test
    void describesRuleStatusesInWords() {
        assertEquals("No matches", SettingsForm.describe(new RuleStatus(0, null, null)));
        assertEquals("1 match", SettingsForm.describe(new RuleStatus(1, null, null)));
        assertEquals("3 matches", SettingsForm.describe(new RuleStatus(3, null, null)));
        assertEquals(
            "⚠ Not applied: invalid regex (Unclosed group near index 1)",
            SettingsForm.describe(new RuleStatus(0, RuleStatus.Problem.INVALID, "Unclosed group near index 1"))
        );
        assertTrue(
            SettingsForm.describe(new RuleStatus(2, RuleStatus.Problem.STOPPED, null)).startsWith("⚠ Partly applied")
        );
    }

    @Test
    void findsWhereThePathStarts() {
        assertEquals(19, ContentPanel.pathStart("https://example.com/a?b"));
        assertEquals(23, ContentPanel.pathStart("https://example.com:443?q=1"));
        assertEquals(15, ContentPanel.pathStart("http://[::1]:80#top"));
        assertEquals(19, ContentPanel.pathStart("https://example.com"));
        assertEquals(0, ContentPanel.pathStart("/relative"));
    }

    private static void onEdt(Runnable test) throws Throwable {
        try {
            SwingUtilities.invokeAndWait(test);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
