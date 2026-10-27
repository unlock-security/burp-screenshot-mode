package it.unlocksecurity.burp.screenshotmode;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.persistence.Preferences;
import burp.api.montoya.ui.Theme;
import burp.api.montoya.ui.hotkey.HotKey;
import burp.api.montoya.ui.hotkey.HotKeyContext;
import burp.api.montoya.ui.hotkey.HotKeyEvent;
import it.unlocksecurity.burp.screenshotmode.storage.PreferenceStore;
import it.unlocksecurity.burp.screenshotmode.storage.TemplateStore;
import it.unlocksecurity.burp.screenshotmode.ui.Host;
import it.unlocksecurity.burp.screenshotmode.ui.ScreenshotLauncher;
import it.unlocksecurity.burp.screenshotmode.ui.TemplatesTab;
import java.awt.Font;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Burp Suite port of the Caido "Screenshot Mode" plugin. */
public final class ScreenshotModeExtension implements BurpExtension {

    static final String NAME = "Screenshot Mode";
    private static final String HOTKEY = "Ctrl+Shift+S";

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName(NAME);

        Preferences preferences = api.persistence().preferences();
        PreferenceStore store = new PreferenceStore() {
            @Override
            public String get(String key) {
                return preferences.getString(key);
            }

            @Override
            public void set(String key, String value) {
                preferences.setString(key, value);
            }
        };

        Host host = new Host(
            () -> api.userInterface().currentEditorFont(),
            () -> {
                Font font = api.userInterface().currentDisplayFont();
                return font != null ? font : UIManager.getFont("Label.font");
            },
            () -> api.userInterface().currentTheme() == Theme.DARK,
            () -> api.userInterface().swingUtils().suiteFrame(),
            api.logging()::logToOutput,
            api.logging()::logToError,
            store
        );

        TemplateStore templates = new TemplateStore(store, api.logging()::logToOutput);
        templates.initialize();
        ScreenshotLauncher launcher = new ScreenshotLauncher(host, templates);

        api.userInterface().registerSuiteTab(NAME, new TemplatesTab(templates, host));
        api.userInterface().registerContextMenuItemsProvider(
            new ScreenshotContextMenu(requestResponse -> open(api, launcher, requestResponse))
        );
        registerHotKey(api, launcher);
        api.extension().registerUnloadingHandler(() -> SwingUtilities.invokeLater(launcher::close));

        api.logging().logToOutput(
            NAME +
                " loaded. Right-click a request and choose Extensions > " +
                NAME +
                " > Screenshot, or press " +
                HOTKEY +
                "."
        );
    }

    private static void open(MontoyaApi api, ScreenshotLauncher launcher, HttpRequestResponse requestResponse) {
        launcher.open(() -> BurpMessages.toMessageData(requestResponse), error -> reportOpenError(api, error));
    }

    /** Logs the error and says so in a dialog: otherwise nothing at all would seem to happen. */
    private static void reportOpenError(MontoyaApi api, Throwable e) {
        api.logging().logToError("Could not open " + NAME + ": " + e);
        String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        Runnable dialog = () ->
            JOptionPane.showMessageDialog(
                api.userInterface().swingUtils().suiteFrame(),
                NAME +
                    " couldn't open this message: " +
                    reason +
                    "\n\nThe details are in the extension's errors (Extensions › Installed › " +
                    NAME +
                    " › Errors).",
                NAME,
                JOptionPane.ERROR_MESSAGE
            );
        if (SwingUtilities.isEventDispatchThread()) {
            dialog.run();
        } else {
            SwingUtilities.invokeLater(dialog);
        }
    }

    /**
     * Port of the Caido Ctrl+Shift+S shortcut, active in message editors and request tables. Burp versions
     * predating {@link HotKey} only get it in message editors.
     */
    @SuppressWarnings("deprecation")
    private static void registerHotKey(MontoyaApi api, ScreenshotLauncher launcher) {
        try {
            api.userInterface().registerHotKeyHandler(HotKey.hotKey("Open " + NAME, HOTKEY), event ->
                onHotKey(api, launcher, event)
            );
        } catch (Throwable e) {
            try {
                api.userInterface().registerHotKeyHandler(HotKeyContext.HTTP_MESSAGE_EDITOR, HOTKEY, event ->
                    onHotKey(api, launcher, event)
                );
            } catch (Throwable fallback) {
                api.logging().logToError("Could not register the " + HOTKEY + " hotkey: " + fallback);
            }
        }
    }

    private static void onHotKey(MontoyaApi api, ScreenshotLauncher launcher, HotKeyEvent event) {
        ScreenshotContextMenu.target(event.messageEditorRequestResponse(), event.selectedRequestResponses()).ifPresent(
            requestResponse -> open(api, launcher, requestResponse)
        );
    }
}
