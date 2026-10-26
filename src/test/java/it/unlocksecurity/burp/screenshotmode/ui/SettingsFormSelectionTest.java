package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import it.unlocksecurity.burp.screenshotmode.storage.HeaderCatalogFile;
import java.awt.Font;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The preview's right-click actions, the same in the screenshot window and in the template editor. */
class SettingsFormSelectionTest {

    private final Settings settings = BuiltInTemplates.defaults();
    private int changes;
    private final SettingsForm form = new SettingsForm(
        settings,
        () -> changes++,
        new Font(Font.MONOSPACED, Font.PLAIN, 12),
        new HeadersTab.InMessage(List.of("X-Backend", "User-Agent"), List.of())
    );

    @Test
    void addsARuleForTheSelectionFirstAndOpen() {
        form.onSelectionAction(MessagePane.SelectionAction.HIGHLIGHT, "admin", RuleTarget.RESPONSE);
        HighlightRule rule = settings.highlights.getFirst();
        assertEquals("admin", rule.regex);
        assertEquals(MatchMode.STRING, rule.matchMode);
        assertEquals(java.util.Set.of(RuleTarget.RESPONSE), rule.targets);
        assertTrue(form.highlightList().row(rule).isExpanded());
        assertEquals(1, changes);

        int count = settings.redactions.size();
        form.onSelectionAction(MessagePane.SelectionAction.REDACT, "secret", RuleTarget.URL);
        assertEquals(count + 1, settings.redactions.size());
        assertEquals("secret", settings.redactions.getFirst().regex);
    }

    @Test
    void hidesAHeader() {
        form.onSelectionAction(MessagePane.SelectionAction.HIDE_HEADER, "X-Backend", RuleTarget.REQUEST);
        assertTrue(settings.headers.hides("X-Backend", RuleTarget.REQUEST, HeaderCatalogFile.bundled()));
        assertEquals(1, changes);
    }

    @Test
    void showsTheRuleThatAlreadyRedactsAHeader() {
        int count = settings.redactions.size();
        form.onSelectionAction(MessagePane.SelectionAction.REDACT_HEADER, "User-Agent", RuleTarget.REQUEST);
        assertEquals(count, settings.redactions.size(), "no duplicate");
        assertEquals(SettingsForm.RULES, form.tabs().getSelectedIndex());
        RedactionRule existing = settings.redactions
            .stream()
            .filter(rule -> rule.name.equals("User-Agent"))
            .findFirst()
            .orElseThrow();
        assertTrue(form.redactionList().row(existing).isExpanded());

        form.onSelectionAction(MessagePane.SelectionAction.REDACT_HEADER, "X-Backend", RuleTarget.REQUEST);
        assertEquals(List.of("X-Backend"), settings.redactions.getFirst().headers);
    }
}
