package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import it.unlocksecurity.burp.screenshotmode.model.Disposition;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettingsHistoryTest {

    private Settings settings = BuiltInTemplates.defaults();
    private final SettingsHistory history = new SettingsHistory(() -> settings);

    @Test
    void undoesAndRedoesInPlace() {
        List<RedactionRule> redactions = settings.redactions;
        int count = redactions.size();
        settings.redactions.removeFirst();
        history.changed(false);
        settings.disposition = Disposition.HORIZONTAL;
        history.changed(false);

        Settings before = history.undo();
        assertEquals(Disposition.HORIZONTAL, before.disposition, "what the settings were");
        assertEquals(Disposition.VERTICAL, settings.disposition);
        assertEquals(count - 1, settings.redactions.size());

        history.undo();
        assertEquals(count, settings.redactions.size());
        assertSame(redactions, settings.redactions, "restored into the lists the editors hold");
        assertNull(history.undo(), "nothing left to undo");

        history.redo();
        history.redo();
        assertEquals(count - 1, settings.redactions.size());
        assertEquals(Disposition.HORIZONTAL, settings.disposition);
        assertNull(history.redo(), "nothing left to redo");
    }

    @Test
    void aChangeBackIsAStepButNoChangeIsNone() {
        history.changed(false);
        assertNull(history.undo(), "nothing changed");

        settings.disposition = Disposition.HORIZONTAL;
        history.changed(false);
        settings.disposition = Disposition.VERTICAL;
        history.changed(false);
        history.undo();
        assertEquals(Disposition.HORIZONTAL, settings.disposition);
    }

    @Test
    void aNewChangeDropsWhatWasUndone() {
        settings.disposition = Disposition.HORIZONTAL;
        history.changed(false);
        history.undo();
        settings.showTimestamp = false;
        history.changed(false);
        assertNull(history.redo());
        assertEquals(Disposition.VERTICAL, settings.disposition);
    }

    @Test
    void typingIsOneStep() {
        RedactionRule rule = settings.redactions.getFirst();
        String name = rule.name;
        for (String typed : List.of("M", "My", "My r", "My rule")) {
            rule.name = typed;
            history.changed(true);
        }
        // Undo takes the typing still waiting for its pause as a step first.
        assertNotNull(history.undo());
        assertEquals(name, settings.redactions.getFirst().name);
        assertNull(history.undo());
        history.redo();
        assertEquals("My rule", settings.redactions.getFirst().name);
    }

    @Test
    void keepsTheLastStepsOnly() {
        for (int i = 0; i <= SettingsHistory.LIMIT; i++) {
            settings.widthMode = WidthMode.PIXEL;
            settings.widthPixels = 300 + i;
            history.changed(false);
        }
        int undone = 0;
        while (history.undo() != null) {
            undone++;
        }
        assertEquals(SettingsHistory.LIMIT, undone);
        assertEquals(300, settings.widthPixels);
    }

    @Test
    void startsAgainFromOtherSettings() {
        settings.disposition = Disposition.HORIZONTAL;
        history.changed(false);
        settings = BuiltInTemplates.byId(BuiltInTemplates.EMPTY_ID).settings;
        history.reset();
        assertNull(history.undo());

        // Replaced settings, as Revert does, are a step like any other.
        settings = BuiltInTemplates.defaults();
        history.record();
        history.undo();
        assertEquals(0, settings.redactions.size(), "back to Empty's");
    }
}
