package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import it.unlocksecurity.burp.screenshotmode.model.MessageMode;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.Template;
import it.unlocksecurity.burp.screenshotmode.render.MessageData;
import org.junit.jupiter.api.Test;

class ScreenshotLauncherTest {

    private static final String RESPONSE = "HTTP/1.1 200 OK\n\n";

    private final Template template = new Template("t", "Template", new Settings());

    @Test
    void showsOnlyTheResponseOfAMessageWithoutARequest() {
        SessionState state = ScreenshotLauncher.newSession(template, message(null, RESPONSE));

        assertEquals(MessageMode.RESPONSE, state.settings.messages);
        assertEquals("t", state.templateId);
        assertEquals(MessageMode.BOTH, template.settings.messages, "the template itself is left as it was");
    }

    @Test
    void keepsTheTemplateMessagesOtherwise() {
        assertEquals(
            MessageMode.BOTH,
            ScreenshotLauncher.newSession(template, message("GET / HTTP/1.1\n\n", RESPONSE)).settings.messages
        );
        assertEquals(
            MessageMode.BOTH,
            ScreenshotLauncher.newSession(template, message("GET / HTTP/1.1\n\n", null)).settings.messages,
            "a missing response keeps its placeholder"
        );
    }

    private static MessageData message(String request, String response) {
        return new MessageData("key", "https://example.com:443", request, response, 0, null, null);
    }
}
