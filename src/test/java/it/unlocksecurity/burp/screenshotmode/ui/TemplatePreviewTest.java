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
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
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
    void arrangesTheSampleAndTheScreenshotAsChosen() {
        assertEquals(TemplatePreview.Arrangement.SIDE_BY_SIDE, preview.arrangement().value(), "by default");
        assertEquals(JSplitPane.HORIZONTAL_SPLIT, splitOf(preview).getOrientation());

        preview.arrangement().click(TemplatePreview.Arrangement.STACKED);
        assertEquals(JSplitPane.VERTICAL_SPLIT, splitOf(preview).getOrientation());

        preview.arrangement().click(TemplatePreview.Arrangement.TABS);
        JTabbedPane tabs = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, preview.partView());
        assertNotNull(tabs, "the screenshot in a tab");
        assertEquals("In the screenshot", tabs.getTitleAt(tabs.getSelectedIndex()), "shown first");
        assertEquals("As written (editable)", tabs.getTitleAt(0));
        assertTrue(SwingUtilities.isDescendingFrom(preview.source(RuleTarget.REQUEST), tabs), "the sample in a tab");

        preview.arrangement().click(TemplatePreview.Arrangement.SIDE_BY_SIDE);
        assertEquals(JSplitPane.HORIZONTAL_SPLIT, splitOf(preview).getOrientation());
        assertTrue(SwingUtilities.isDescendingFrom(preview.source(RuleTarget.REQUEST), splitOf(preview)));
    }

    @Test
    void remembersTheArrangementForTheNextEditor() {
        Host host = TemplatesTabTest.host();
        new TemplatePreview(new TemplatePreview.Sample(), settings, host)
            .arrangement()
            .click(TemplatePreview.Arrangement.TABS);
        TemplatePreview next = new TemplatePreview(new TemplatePreview.Sample(), settings, host);
        assertEquals(TemplatePreview.Arrangement.TABS, next.arrangement().value());
        assertNotNull(SwingUtilities.getAncestorOfClass(JTabbedPane.class, next.source(RuleTarget.REQUEST)));

        host.preferences().set(TemplatePreview.ARRANGEMENT_KEY, "from-a-later-release");
        assertEquals(
            TemplatePreview.Arrangement.SIDE_BY_SIDE,
            new TemplatePreview(new TemplatePreview.Sample(), settings, host).arrangement().value()
        );
    }

    @Test
    void namesTheArrangementButtonsThatOnlyShowAnIcon() {
        for (TemplatePreview.Arrangement value : TemplatePreview.Arrangement.values()) {
            JToggleButton button = preview.arrangement().button(value);
            assertNotNull(button.getIcon());
            assertEquals(value.label(), button.getAccessibleContext().getAccessibleName());
            assertEquals(value.label(), button.getToolTipText());
        }
    }

    @Test
    void showsThePartsAndRestoreWholeInANarrowPreview() throws Exception {
        preview.setSize(300, 600);
        WrapLayoutTest.layOut(preview);
        for (java.awt.Component part : preview.parts().getComponents()) {
            assertTrue(WrapLayoutTest.inView(part, preview), part + " in view");
        }
        assertTrue(WrapLayoutTest.inView(preview.restoreButton(), preview), "Restore sample in view");
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
        java.awt.Component label = Stream.of(preview.parts().getParent().getComponents())
            .filter(c -> c instanceof javax.swing.JLabel l && l.getLabelFor() == preview.parts())
            .findFirst()
            .orElseThrow();
        preview.showWhole(true);
        assertFalse(preview.parts().isVisible());
        assertFalse(preview.arrangement().isVisible(), "nothing to arrange");
        assertFalse(label.isVisible(), "no \"Show\" without the parts");
        preview.showWhole(false);
        assertTrue(preview.parts().isVisible());
        assertTrue(label.isVisible());
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

    private static JSplitPane splitOf(TemplatePreview preview) {
        JSplitPane split = (JSplitPane) SwingUtilities.getAncestorOfClass(JSplitPane.class, preview.partView());
        assertNotNull(split, "the screenshot in a split pane");
        return split;
    }

    private static void undo(JComponent area) {
        KeyStroke key = KeyStroke.getKeyStroke(KeyEvent.VK_Z, Widgets.menuShortcutMask());
        Object action = area.getInputMap(JComponent.WHEN_FOCUSED).get(key);
        area.getActionMap().get(action).actionPerformed(null);
    }
}
