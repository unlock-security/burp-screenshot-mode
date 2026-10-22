package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.Disposition;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettingsFormRestoreTest {

    private final Settings settings = BuiltInTemplates.defaults();
    private final List<String> asked = new ArrayList<>();
    private String answer;
    private int changes;

    private SettingsForm form() {
        SettingsForm form = new SettingsForm(settings, () -> changes++, new Font(Font.MONOSPACED, Font.PLAIN, 12));
        form.setChooser((title, message, action, safe) -> {
            asked.add(message + " [" + action + " | " + safe + "]");
            return answer;
        });
        return form;
    }

    private static RedactionRule mine() {
        return RedactionRule.create("secret", RuleTarget.REQUEST, RedactionMode.REPLACE, MatchMode.STRING);
    }

    @Test
    void addsBackTheMissingRulesOfItsBuiltInTemplate() {
        settings.redactions.removeIf(rule -> rule.name.equals("Cookie values"));
        settings.redactions.getFirst().replacementText = "[edited]";
        settings.redactions.add(mine());
        SettingsForm form = form();
        assertEquals("Based on Defaults · 1 rule missing", form.rulesBasis().getText());
        assertTrue(form.restoreRulesButton().isEnabled());

        answer = null;
        form.restoreRulesButton().doClick();
        assertEquals(
            "1 rule of \"Defaults\" is missing from this template.\nAdd it back, or replace all your rules with the " +
                "10 rules of \"Defaults\"? [Replace with defaults | Add missing defaults]",
            asked.getLast()
        );
        assertEquals(10, settings.redactions.size(), "closing the dialog changes nothing");

        answer = "Add missing defaults";
        form.restoreRulesButton().doClick();
        assertEquals(11, settings.redactions.size(), "nothing deleted");
        assertEquals("Cookie values", settings.redactions.getLast().name);
        assertEquals("[edited]", settings.redactions.getFirst().replacementText, "edited rules are kept");
        assertEquals("Based on Defaults · no rules missing", form.rulesBasis().getText());
        assertTrue(form.redactionList().contains(settings.redactions.getLast()), "shown in the list");
        assertTrue(changes > 0);
    }

    @Test
    void replacesTheRulesWithThoseOfItsBuiltInTemplate() {
        settings.redactions.getFirst().replacementText = "[edited]";
        settings.redactions.add(mine());
        SettingsForm form = form();
        answer = "Replace with defaults";
        form.restoreRulesButton().doClick();
        assertTrue(asked.getLast().endsWith("[Replace with defaults | Cancel]"), "nothing to add: only replace");
        assertEquals(BuiltInTemplates.defaults().redactions.size(), settings.redactions.size());
        assertFalse(settings.redactions.getFirst().replacementText.equals("[edited]"));
        assertFalse(form.restoreRulesButton().isEnabled(), "the rules are the defaults again");
    }

    @Test
    void restoresNothingButEmptysFromATemplateBasedOnEmpty() {
        settings.basedOn = null;
        SettingsForm form = form();
        assertEquals("Based on Empty · no rules", form.rulesBasis().getText());
        answer = "Replace with defaults";
        form.restoreRulesButton().doClick();
        assertEquals(
            "\"Empty\" has no rules: replacing removes all your rules. [Replace with defaults | Cancel]",
            asked.getLast()
        );
        assertTrue(settings.redactions.isEmpty());
        assertFalse(form.restoreRulesButton().isEnabled());
    }

    @Test
    void restoresTheAppearanceOfItsBuiltInTemplate() {
        SettingsForm form = form();
        assertEquals("Based on Defaults · no changes", form.appearanceBasis().getText());
        assertFalse(form.restoreAppearanceButton().isEnabled());

        settings.disposition = Disposition.VERTICAL;
        settings.showTimestamp = true;
        form = form();
        assertEquals("Based on Defaults · 2 changes", form.appearanceBasis().getText());
        answer = "Cancel";
        form.restoreAppearanceButton().doClick();
        assertEquals(Disposition.VERTICAL, settings.disposition);

        answer = "Restore";
        form.restoreAppearanceButton().doClick();
        assertEquals(
            "Use the appearance of \"Defaults\" again?\nYour 2 changes to width, layout, theme, URL bar, body and " +
                "footer will be lost. [Restore | Cancel]",
            asked.getLast()
        );
        assertEquals(Disposition.HORIZONTAL, settings.disposition);
        assertFalse(settings.showTimestamp);
        assertEquals(10, settings.redactions.size(), "rules untouched");
        assertEquals("Based on Defaults · no changes", form.appearanceBasis().getText());
    }
}
