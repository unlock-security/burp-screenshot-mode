package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.Settings;

/** Screenshot window state remembered per message, like the Caido plugin's per-tab store. */
final class SessionState {

    Settings settings;
    String templateId;
    double splitRatio = 0.5;
    /** {@code null} when the content is not cropped. */
    Integer cropHeight;

    SessionState(Settings settings, String templateId) {
        this.settings = settings;
        this.templateId = templateId;
    }
}
