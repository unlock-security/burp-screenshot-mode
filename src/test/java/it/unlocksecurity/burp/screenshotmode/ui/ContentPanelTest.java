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
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import java.awt.Font;
import java.lang.reflect.InvocationTargetException;
import java.util.EnumSet;
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

    @Test
    void keepsTheSplitRatioWhenResized() throws Throwable {
        Settings settings = BuiltInTemplates.defaults();
        settings.disposition = it.unlocksecurity.burp.screenshotmode.model.Disposition.VERTICAL;
        ContentPanel content = rendered("https://x/", settings);
        List<Double> saved = new java.util.ArrayList<>();
        onEdt(() -> {
            content.setSize(600, 800);
            RenderPreviewTest.layoutTree(content);
            content.setSplitRatioListener(saved::add);
            content.setSplitRatio(0.4137);
            RenderPreviewTest.layoutTree(content);
            // Until painted, a split pane gives each side its preferred size instead of sharing out a resize.
            content.paint(
                new java.awt.image.BufferedImage(600, 800, java.awt.image.BufferedImage.TYPE_INT_RGB).getGraphics()
            );
            // What "Fit to content" does: the split pane rounds while sharing out the lost height.
            content.setSize(600, 500);
            RenderPreviewTest.layoutTree(content);
        });
        assertEquals(List.of(), saved, "a resize is not a move by the user");

        onEdt(() -> {
            javax.swing.JSplitPane split = (javax.swing.JSplitPane) content.requestPane().getParent();
            split.setDividerLocation(200);
        });
        assertEquals(1, saved.size(), "a move by the user is");
    }

    @Test
    void recordsTheRulesThatChangedEachHeaderLine() throws Throwable {
        Settings settings = BuiltInTemplates.defaults();
        settings.headers.set(
            "Accept",
            it.unlocksecurity.burp.screenshotmode.model.Visibility.HIDE,
            it.unlocksecurity.burp.screenshotmode.storage.HeaderCatalogFile.bundled()
        );
        ContentPanel[] content = new ContentPanel[1];
        onEdt(() -> {
            content[0] = new ContentPanel();
            String request =
                "GET / HTTP/1.1\nAuthorization: Bearer abc\nCookie: a=1\nAccept: */*\nHost: x\n\nCookie: body";
            MessageData data = new MessageData("key", "https://x/", request, "HTTP/1.1 200 OK\n", 0, null, null);
            content[0].render(data, settings, Palette.LIGHT, FONT, FONT, null, null);
        });
        var hits = content[0].headerHits().get(RuleTarget.REQUEST);
        RedactionRule authorization = named(settings, "Authorization credentials");
        RedactionRule cookie = named(settings, "Cookie values");
        assertEquals(java.util.Set.of(authorization), hits.get("authorization"), "a pattern rule, by what it changed");
        assertEquals(java.util.Set.of(cookie), hits.get("COOKIE"), "ignoring case");
        assertFalse(hits.containsKey("Host"));
        assertFalse(hits.containsKey("Accept"), "hidden");
    }

    @Test
    void addsUpWhatARuleDidInEachPart() throws Throwable {
        Settings settings = new Settings();
        RedactionRule rule = RedactionRule.forHeaders(List.of("X-Trace"), RedactionMode.REPLACE);
        settings.redactions.add(rule);
        ContentPanel[] content = new ContentPanel[1];
        onEdt(() -> {
            content[0] = new ContentPanel();
            MessageData data = new MessageData(
                "k",
                "https://x/",
                "GET / HTTP/1.1\nX-Trace: 1\n",
                "HTTP/1.1 200 OK\nX-Trace: 2\n",
                0,
                null,
                null
            );
            content[0].render(data, settings, Palette.LIGHT, FONT, FONT, null, null);
        });
        assertEquals(new RuleStatus(2, null, null), content[0].ruleStatuses().get(rule), "one in each message");
    }

    private static RedactionRule named(Settings settings, String name) {
        return settings.redactions
            .stream()
            .filter(rule -> rule.name.equals(name))
            .findFirst()
            .orElseThrow();
    }

    private static String renderedUrl(String url, Settings settings) throws Throwable {
        return rendered(url, settings).urlView().text();
    }

    @Test
    void redactsSecretsInTheUrlBarByDefault() throws Throwable {
        assertEquals(
            "https://example.com:443/reset?access_token=[REDACTED]&id=1",
            renderedUrl("https://example.com:443/reset?access_token=abc&id=1", BuiltInTemplates.defaults())
        );
    }

    @Test
    void appliesOnlyTheRulesMeantForTheUrlBar() throws Throwable {
        Settings settings = new Settings();
        settings.redactions.add(
            RedactionRule.create("needle", RuleTarget.REQUEST, RedactionMode.REPLACE, MatchMode.STRING)
        );
        assertEquals("https://example.com/needle", renderedUrl("https://example.com/needle", settings));

        settings.redactions.getFirst().targets = EnumSet.of(RuleTarget.URL);
        assertEquals("https://example.com/[REDACTED]", renderedUrl("https://example.com/needle", settings));
    }

    @Test
    void showsOnlyThePathAfterTheRulesRan() throws Throwable {
        Settings settings = BuiltInTemplates.defaults();
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
        Settings settings = BuiltInTemplates.defaults();
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
            "⚠ Invalid regex: Unclosed group near index 1",
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
