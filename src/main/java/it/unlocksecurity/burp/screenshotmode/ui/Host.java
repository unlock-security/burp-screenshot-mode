package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.ThemeMode;
import it.unlocksecurity.burp.screenshotmode.render.Palette;
import it.unlocksecurity.burp.screenshotmode.storage.PreferenceStore;
import java.awt.Font;
import java.awt.Window;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** What the UI needs from Burp, kept behind plain Java types so the UI can be rendered without Burp. */
public record Host(
    Supplier<Font> editorFont,
    Supplier<Font> displayFont,
    BooleanSupplier darkTheme,
    Supplier<Window> owner,
    Consumer<String> log,
    Consumer<String> logError,
    PreferenceStore preferences
) {
    public Palette palette(ThemeMode mode) {
        return switch (mode) {
            case DARK -> Palette.DARK;
            case LIGHT -> Palette.LIGHT;
            case AUTO -> darkTheme.getAsBoolean() ? Palette.DARK : Palette.LIGHT;
        };
    }
}
