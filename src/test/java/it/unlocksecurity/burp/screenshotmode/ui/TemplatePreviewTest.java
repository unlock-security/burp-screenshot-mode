package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.Rule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.UrlMode;
import it.unlocksecurity.burp.screenshotmode.render.RuleStatus;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import it.unlocksecurity.burp.screenshotmode.storage.SampleMessage;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.stream.Stream;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

class TemplatePreviewTest {

    private final Settings settings = BuiltInTemplates.defaults();
    private final TemplatePreview.Sample sample = new TemplatePreview.Sample();
    private final TemplatePreview preview = new TemplatePreview(sample, settings, TemplatesTabTest.host());
    private Map<Object, RuleStatus> statuses;

    {
        preview.setRenderListener((data, content) -> statuses = Map.copyOf(content.ruleStatuses()));
        preview.render();
    }

    @Test
    void everyRuleOfDefaultsMatchesInTheSample() {
        // A message has one body: the sample's is JSON, so the rule for form bodies has nothing to match.
        Stream.concat(settings.highlights.stream(), settings.redactions.stream())
            .filter(rule -> !rule.name.equals("Passwords in form data"))
            .forEach(rule -> {
                RuleStatus status = statuses.get(rule);
                assertNotNull(status, rule.name);
                assertTrue(status.matches() > 0, rule.name + " matches nothing in the sample");
            });
    }

    @Test
    void showsOnlyThePartBeingEdited() {
        ContentPanel part = preview.partView();
        assertEquals(RuleTarget.REQUEST, preview.parts().value());
        assertTrue(part.requestPane().isVisible());
        assertFalse(part.responsePane().isVisible());
        assertFalse(part.urlBar().isVisible());

        preview.parts().click(RuleTarget.URL);
        assertEquals(RuleTarget.URL, sample.part, "remembered for the next editor");
        assertTrue(part.urlBar().isVisible());
        assertFalse(part.requestPane().isVisible());

        // The URL being edited stays visible, whatever the URL bar setting.
        settings.url = UrlMode.HIDDEN;
        preview.render();
        assertTrue(part.urlBar().isVisible());
        assertFalse(preview.wholeView().urlBar().isVisible(), "the screenshot itself has none");
    }

    @Test
    void theAppearanceTabShowsTheWholeScreenshot() {
        preview.showWhole(true);
        assertFalse(preview.parts().isVisible());
        preview.showWhole(false);
        assertTrue(preview.parts().isVisible());
    }

    @Test
    void rendersEditsToTheSample() {
        Rule apiKeys = settings.redactions
            .stream()
            .filter(rule -> rule.name.equals("API key headers"))
            .findFirst()
            .orElseThrow();
        int before = statuses.get(apiKeys).matches();
        String request = preview.source(RuleTarget.REQUEST).getText();
        preview.source(RuleTarget.REQUEST).setText(request.replace("\nPriority:", "\nX-Auth-Token: t\nPriority:"));
        preview.render();
        assertEquals(before + 1, statuses.get(apiKeys).matches());
        assertTrue(preview.restoreButton().isEnabled());
    }

    @Test
    void restoresTheSampleAsOneUndoableStep() {
        preview.source(RuleTarget.URL).setText("https://other.example.com/\n?x=1");
        assertEquals("https://other.example.com/?x=1", sample.text(RuleTarget.URL), "no line break in a URL");
        preview.source(RuleTarget.RESPONSE).setText("HTTP/2 404 Not Found");
        preview.render();

        preview.restoreButton().doClick();
        assertEquals(SampleMessage.bundled().url(), preview.source(RuleTarget.URL).getText());
        assertEquals(SampleMessage.bundled().response(), sample.text(RuleTarget.RESPONSE));
        assertFalse(preview.restoreButton().isEnabled());

        undo(preview.source(RuleTarget.RESPONSE));
        assertEquals("HTTP/2 404 Not Found", sample.text(RuleTarget.RESPONSE), "one undo takes the restore back");
    }

    private static void undo(JComponent area) {
        KeyStroke key = KeyStroke.getKeyStroke(KeyEvent.VK_Z, Widgets.menuShortcutMask());
        Object action = area.getInputMap(JComponent.WHEN_FOCUSED).get(key);
        area.getActionMap().get(action).actionPerformed(null);
    }
}
