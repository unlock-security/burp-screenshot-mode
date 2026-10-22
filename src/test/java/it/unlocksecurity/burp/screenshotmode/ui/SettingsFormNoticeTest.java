package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import java.awt.Font;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettingsFormNoticeTest {

    private final Settings settings = BuiltInTemplates.defaults();
    private final SettingsForm form = new SettingsForm(
        settings,
        () -> {},
        new Font(Font.MONOSPACED, Font.PLAIN, 12),
        new HeadersTab.InMessage(List.of("X-Backend"), List.of())
    );

    @Test
    void jumpsToANewRuleAndBack() {
        form.tabs().setSelectedIndex(SettingsForm.HEADERS);
        form.headersTab().act(List.of("X-Backend"), "X-Backend", true);

        RedactionRule added = settings.redactions.getFirst();
        assertEquals(List.of("X-Backend"), added.headers);
        assertEquals(SettingsForm.RULES, form.tabs().getSelectedIndex());
        assertTrue(form.redactionList().row(added).isExpanded());
        assertTrue(form.notice().isVisible());
        assertEquals("Redaction created for X-Backend.", form.noticeText().getText());

        form.tabs().setSelectedIndex(SettingsForm.HEADERS);
        assertFalse(form.notice().isVisible(), "the notice ends with the jump");
    }
}
