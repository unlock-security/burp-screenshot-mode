package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.UrlMode;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import java.awt.Font;
import org.junit.jupiter.api.Test;

/** The Appearance tab's URL bar choice, for what the message on screen gives the bar. */
class SettingsFormUrlBarTest {

    private final Settings settings = BuiltInTemplates.defaults();
    private final SettingsForm form = new SettingsForm(settings, () -> {}, new Font(Font.MONOSPACED, Font.PLAIN, 12));

    @Test
    void offersEveryChoiceForARequest() {
        form.setMessageUrl(true, true);

        assertTrue(form.urlBar().isEnabled());
        assertNull(form.urlBar().getToolTipText());
    }

    @Test
    void keepsTheChoiceForAResponseWithoutARequest() {
        form.setMessageUrl(false, true);

        assertTrue(form.urlBar().isEnabled(), "Full URL still shows the service");
        assertTrue(form.urlBar().getToolTipText().contains("no path"), "says why Path only shows no bar");
    }

    @Test
    void disablesTheChoiceWithoutAUrl() {
        form.setMessageUrl(false, false);

        assertFalse(form.urlBar().isEnabled(), "no choice would show anything");
    }

    @Test
    void staysDisabledWhenTheAppearanceTabIsRebuilt() {
        form.setMessageUrl(false, false);
        Settings before = settings.copy();
        settings.url = UrlMode.FULL;

        form.reload(before);

        assertFalse(form.urlBar().isEnabled());
        assertEquals(UrlMode.FULL, settings.url);
    }
}
