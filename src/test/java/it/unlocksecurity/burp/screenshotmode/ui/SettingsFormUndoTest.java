package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.Disposition;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleType;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.ViewMode;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import it.unlocksecurity.burp.screenshotmode.storage.HeaderCatalogFile;
import java.awt.Font;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The settings form wired to a history, as the screenshot window and the template editor do. */
class SettingsFormUndoTest {

    private final Settings settings = BuiltInTemplates.defaults();
    private final SettingsHistory history = new SettingsHistory(() -> settings);
    private final SettingsForm form = new SettingsForm(
        settings,
        () -> history.changed(false),
        new Font(Font.MONOSPACED, Font.PLAIN, 12),
        new HeadersTab.InMessage(List.of("Cookie"), List.of())
    );

    {
        form.setUndo(() -> undo(false));
    }

    private void undo(boolean redo) {
        Settings before = redo ? history.redo() : history.undo();
        form.reload(before);
    }

    @Test
    void offersUndoAfterADeletionAndOpensTheRuleThatCameBack() {
        RedactionRule rule = settings.redactions.get(1);
        form.redactionList().row(rule).delete();
        assertFalse(settings.redactions.stream().anyMatch(r -> r.id.equals(rule.id)));
        assertTrue(form.notice().isVisible());
        assertEquals("Rule deleted.", form.noticeText().getText());
        assertEquals("Undo", form.noticeAction().getText());

        form.noticeAction().doClick();
        RedactionRule back = settings.redactions.get(1);
        assertEquals(rule.id, back.id, "back in its place");
        assertNotSame(rule, back);
        assertTrue(form.redactionList().row(back).isExpanded(), "opened, to show what came back");
        assertFalse(form.notice().isVisible());

        undo(true);
        assertFalse(settings.redactions.stream().anyMatch(r -> r.id.equals(rule.id)), "redone");
    }

    @Test
    void theUndoNoticeEndsWithTheNextChange() {
        form.redactionList().row(settings.redactions.getFirst()).delete();
        assertTrue(form.notice().isVisible());
        form.redactionList().row(settings.redactions.getFirst()).delete();
        assertTrue(form.notice().isVisible(), "a new deletion, a new notice");

        form.highlightList().addRuleForTests(RuleType.PATTERN);
        assertFalse(form.notice().isVisible(), "Undo would now undo the new rule, not the deletion");
    }

    @Test
    void showsTheTabWhereTheChangeWasUndone() {
        form.tabs().setSelectedIndex(SettingsForm.RULES);
        settings.headers.setGroup("caching", false, HeaderCatalogFile.bundled());
        history.record();
        undo(false);
        assertEquals(SettingsForm.HEADERS, form.tabs().getSelectedIndex());

        settings.disposition = Disposition.VERTICAL;
        history.record();
        undo(false);
        assertEquals(SettingsForm.APPEARANCE, form.tabs().getSelectedIndex());
        assertEquals(Disposition.HORIZONTAL, settings.disposition);

        // A step that also changed the open tab, like Revert, keeps it.
        settings.view = ViewMode.PRETTY;
        settings.redactions.removeFirst();
        history.record();
        undo(false);
        assertEquals(SettingsForm.APPEARANCE, form.tabs().getSelectedIndex());
        assertEquals(ViewMode.RAW, settings.view);
    }

    @Test
    void keepsTheOpenRuleOpen() {
        RedactionRule open = settings.redactions.get(2);
        form.expand(open);
        settings.disposition = Disposition.VERTICAL;
        history.record();
        undo(false);
        RedactionRule reloaded = settings.redactions.get(2);
        assertTrue(form.redactionList().row(reloaded).isExpanded());
    }
}
