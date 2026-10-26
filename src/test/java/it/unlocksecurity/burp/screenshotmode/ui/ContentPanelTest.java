package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.MessageMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.UrlMode;
import it.unlocksecurity.burp.screenshotmode.model.ViewMode;
import it.unlocksecurity.burp.screenshotmode.render.MessageData;
import it.unlocksecurity.burp.screenshotmode.render.MessageText;
import it.unlocksecurity.burp.screenshotmode.render.Palette;
import it.unlocksecurity.burp.screenshotmode.render.RuleStatus;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import java.awt.Font;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
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

    @Test
    void leavesOutTheMessageNotShownAndWhatItsRulesWouldDo() throws Throwable {
        Settings settings = new Settings();
        settings.messages = MessageMode.REQUEST;
        RedactionRule header = RedactionRule.forHeaders(List.of("X-Trace"), RedactionMode.REPLACE);
        RedactionRule invalid = RedactionRule.create("(", RuleTarget.RESPONSE, RedactionMode.BLUR, MatchMode.REGEX);
        settings.redactions.addAll(List.of(header, invalid));
        MessageData data = new MessageData(
            "k",
            "https://x/",
            "GET / HTTP/1.1\nX-Trace: 1\n",
            "HTTP/1.1 200 OK\nX-Trace: 2\n",
            0,
            null,
            null
        );
        ContentPanel[] content = new ContentPanel[1];
        onEdt(() -> {
            content[0] = new ContentPanel();
            content[0].render(data, settings, Palette.LIGHT, FONT, FONT, null, null);
        });
        assertTrue(content[0].requestPane().isVisible());
        assertFalse(content[0].responsePane().isVisible());
        assertEquals(new RuleStatus(1, null, null), content[0].ruleStatuses().get(header), "the request's only");
        assertEquals(null, content[0].ruleStatuses().get(invalid), "nothing to fail on in the screenshot");
        assertFalse(content[0].headerHits().containsKey(RuleTarget.RESPONSE));
        javax.swing.JSplitPane split = (javax.swing.JSplitPane) content[0].requestPane().getParent();
        assertEquals(0, split.getDividerSize(), "no divider beside the message left out");

        onEdt(() -> {
            content[0].showOnly(RuleTarget.RESPONSE);
            content[0].render(data, settings, Palette.LIGHT, FONT, FONT, null, null);
        });
        assertFalse(content[0].requestPane().isVisible());
        assertTrue(content[0].responsePane().isVisible(), "the part edited, whichever messages the screenshot shows");
        assertEquals(new RuleStatus(2, null, null), content[0].ruleStatuses().get(header), "every part rendered");
        assertTrue(content[0].ruleStatuses().get(invalid).failed());
        assertEquals(0, split.getDividerSize(), "nor beside a part shown alone");

        onEdt(() -> {
            content[0].showOnly(null);
            settings.messages = MessageMode.BOTH;
            content[0].render(data, settings, Palette.LIGHT, FONT, FONT, null, null);
        });
        assertTrue(split.getDividerSize() > 0, "between the two messages");
    }

    @Test
    void keepsWhatARuleHidesInTheUrlOutOfTheFileName() throws Throwable {
        Settings settings = new Settings();
        settings.redactions.add(RedactionRule.create("s3cr3t", RuleTarget.URL, RedactionMode.BLUR, MatchMode.STRING));
        for (UrlMode mode : List.of(UrlMode.FULL, UrlMode.PATH, UrlMode.HIDDEN)) {
            settings.url = mode;
            ContentPanel content = rendered("https://x/reset/s3cr3t?id=1", settings);
            MessageView url = content.urlView();
            assertEquals(
                "GET-reset-REDACTED-200.png",
                ImageCapture.fileName(
                    "GET / HTTP/1.1",
                    url.copyText(0, url.text().length()),
                    "HTTP/1.1 200 OK",
                    MessageMode.BOTH
                ),
                mode.label()
            );
        }
    }

    @Test
    void setsTheTextSizeOfTheMessagesAndScalesTheRest() throws Throwable {
        Settings settings = new Settings();
        ContentPanel burp = rendered("https://x/", settings);
        assertEquals(13f, viewOf(burp.requestPane()).getFont().getSize2D(), "Burp's font");
        assertEquals(11f, burp.urlLabel().getFont().getSize2D());

        settings.textSizeMode = it.unlocksecurity.burp.screenshotmode.model.TextSizeMode.CUSTOM;
        settings.textSize = 26;
        ContentPanel custom = rendered("https://x/", settings);
        assertEquals(26f, viewOf(custom.requestPane()).getFont().getSize2D());
        assertEquals(26f, viewOf(custom.responsePane()).getFont().getSize2D());
        assertEquals(26f, custom.urlView().getFont().getSize2D());
        assertEquals(24f, custom.urlLabel().getFont().getSize2D(), "twice the size, like the messages");
    }

    @Test
    void hidesTheLineNumbers() throws Throwable {
        Settings settings = new Settings();
        ContentPanel shown = rendered("https://x/", settings);
        settings.lineNumbers = false;
        ContentPanel hidden = rendered("https://x/", settings);
        for (ContentPanel content : List.of(shown, hidden)) {
            javax.swing.JScrollPane scroll = (javax.swing.JScrollPane) javax.swing.SwingUtilities.getAncestorOfClass(
                javax.swing.JScrollPane.class,
                viewOf(content.requestPane())
            );
            boolean numbered = scroll.getRowHeader() != null && scroll.getRowHeader().getView() != null;
            assertEquals(content == shown, numbered);
        }
        assertTrue(
            hidden.requestPane().unwrappedWidth() < shown.requestPane().unwrappedWidth(),
            "fitting the width leaves no room for them"
        );

        onEdt(() -> {
            settings.lineNumbers = true;
            hidden.render(
                new MessageData("key", "https://x/", "GET / HTTP/1.1\n", "HTTP/1.1 200 OK\n", 0, null, null),
                settings,
                Palette.LIGHT,
                FONT,
                FONT,
                null,
                null
            );
        });
        assertEquals(shown.requestPane().unwrappedWidth(), hidden.requestPane().unwrappedWidth(), "shown again");
    }

    private static MessageView viewOf(MessagePane pane) {
        java.util.ArrayDeque<java.awt.Component> queue = new java.util.ArrayDeque<>(List.of(pane));
        while (!queue.isEmpty()) {
            java.awt.Component component = queue.poll();
            if (component instanceof MessageView view) {
                return view;
            }
            if (component instanceof java.awt.Container container) {
                queue.addAll(List.of(container.getComponents()));
            }
        }
        throw new AssertionError("no view");
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
    void leavesTheTimestampOutWhenBurpDoesNotKnowWhenTheRequestWasSent() {
        MessageData unsent = new MessageData("key", "https://x/", "GET / HTTP/1.1\n", null, null, null, null);
        assertEquals(null, ContentPanel.timestamp(unsent), "not the current time, which would pass for the request's");
        MessageData sent = new MessageData(
            "key",
            "https://x/",
            "GET / HTTP/1.1\n",
            null,
            null,
            null,
            java.time.ZonedDateTime.parse("2026-03-12T10:42:07Z")
        );
        assertTrue(ContentPanel.timestamp(sent).contains("2026"));
    }

    @Test
    void recordsWhichPartsWereTooLongToShowWhole() throws Throwable {
        String body = "{\"a\": \"" + "x".repeat(MessageText.MAX_CHARS) + "\"}";
        String response = MessageText.fromBytes(
            ("HTTP/1.1 200 OK\nContent-Type: application/json\n\n" + body).getBytes(StandardCharsets.UTF_8)
        );
        for (ViewMode view : ViewMode.values()) {
            Settings settings = new Settings();
            settings.view = view;
            ContentPanel[] content = new ContentPanel[1];
            onEdt(() -> {
                content[0] = new ContentPanel();
                MessageData data = new MessageData("key", "https://x/", "GET / HTTP/1.1\n", response, 1, null, null);
                content[0].render(data, settings, Palette.LIGHT, FONT, FONT, null, null);
            });
            assertEquals(EnumSet.of(RuleTarget.RESPONSE), content[0].truncated(), view.label());
        }
    }

    @Test
    void saysSoWhenThereIsNoResponse() throws Throwable {
        ContentPanel[] content = new ContentPanel[1];
        onEdt(() -> {
            content[0] = new ContentPanel();
            MessageData data = new MessageData("key", "https://x/", "GET / HTTP/1.1\n", null, null, null, null);
            content[0].render(data, new Settings(), Palette.LIGHT, FONT, FONT, null, null);
        });
        java.util.List<String> texts = new java.util.ArrayList<>();
        java.util.ArrayDeque<java.awt.Component> queue = new java.util.ArrayDeque<>(List.of(content[0]));
        while (!queue.isEmpty()) {
            java.awt.Component component = queue.poll();
            if (component instanceof javax.swing.JLabel label && label.getParent().getParent() != null) {
                texts.add(label.getText());
            }
            if (component instanceof java.awt.Container container) {
                queue.addAll(List.of(container.getComponents()));
            }
        }
        assertTrue(texts.contains("No response"), "not an empty pane, which reads as an empty response: " + texts);
        assertFalse(content[0].prettyFailed());
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
