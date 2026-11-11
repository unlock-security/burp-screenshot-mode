package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.MessageMode;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.UrlMode;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import java.awt.Font;
import org.junit.jupiter.api.Test;

/** The Appearance tab's URL bar, Messages and Layout choices, for what the message on screen has. */
class SettingsFormMessagePartsTest {

    private final Settings settings = BuiltInTemplates.defaults();
    private final SettingsForm form = new SettingsForm(settings, () -> {}, new Font(Font.MONOSPACED, Font.PLAIN, 12));

    @Test
    void offersEveryChoiceForARequest() {
        form.setMessageParts(true, true);

        assertTrue(form.urlBar().isEnabled());
        assertNull(form.urlBar().getToolTipText());
        assertTrue(form.messages().isEnabled());
        assertEquals(MessageMode.BOTH, form.messages().value());
        assertTrue(form.layoutControl().isEnabled());
    }

    @Test
    void showsOnlyTheResponseOfAMessageWithoutARequest() {
        form.setMessageParts(false, true);

        assertEquals(MessageMode.RESPONSE, form.messages().value());
        assertFalse(form.messages().isEnabled(), "Response is the only choice");
        assertFalse(form.layoutControl().isEnabled(), "one message has no layout");
        assertEquals(MessageMode.BOTH, settings.messages, "the template's choice is kept for the next message");
    }

    @Test
    void keepsTheUrlBarChoiceForAResponseWithoutARequest() {
        form.setMessageParts(false, true);

        assertTrue(form.urlBar().isEnabled(), "Full URL still shows the service");
        assertTrue(form.urlBar().getToolTipText().contains("no path"), "says why Path only shows no bar");
    }

    @Test
    void disablesTheUrlBarChoiceWithoutAUrl() {
        form.setMessageParts(false, false);

        assertFalse(form.urlBar().isEnabled(), "no choice would show anything");
    }

    @Test
    void staysDisabledWhenTheAppearanceTabIsRebuilt() {
        form.setMessageParts(false, false);
        Settings before = settings.copy();
        settings.url = UrlMode.FULL;

        form.reload(before);

        assertFalse(form.urlBar().isEnabled());
        assertFalse(form.messages().isEnabled());
        assertEquals(MessageMode.RESPONSE, form.messages().value());
        assertEquals(UrlMode.FULL, settings.url);
    }
}
