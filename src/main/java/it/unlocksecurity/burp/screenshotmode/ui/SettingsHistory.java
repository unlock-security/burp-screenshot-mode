package it.unlocksecurity.burp.screenshotmode.ui;

import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.storage.TemplateCodec;
import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.InputMethodEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.text.JTextComponent;

/**
 * Undo and redo for the settings of the screenshot window or of the template editor, as snapshots taken after each
 * change. A change that leaves the stored settings as they were is no step. Typing in a text field is one step, taken
 * when the typing pauses, rather than one per key.
 */
final class SettingsHistory {

    static final int LIMIT = 100;
    static final int TYPING_PAUSE = 1000;

    private final Supplier<Settings> settings;
    private final Deque<Settings> undo = new ArrayDeque<>();
    private final Deque<Settings> redo = new ArrayDeque<>();
    private final Timer typing = new Timer(TYPING_PAUSE, event -> record());
    /** The settings as of the last step. */
    private Settings last;

    /** @param settings the settings being edited; the object may be replaced, then call {@link #record}. */
    SettingsHistory(Supplier<Settings> settings) {
        this.settings = settings;
        typing.setRepeats(false);
        last = settings.get().copy();
    }

    /** Takes note of a change to the settings: a step now, or when the typing pauses. */
    void changed() {
        changed(isTyping(EventQueue.getCurrentEvent()));
    }

    void changed(boolean typing) {
        if (typing) {
            this.typing.restart();
        } else {
            record();
        }
    }

    private static boolean isTyping(AWTEvent event) {
        return (
            (event instanceof KeyEvent || event instanceof InputMethodEvent) &&
            event.getSource() instanceof JTextComponent
        );
    }

    /** Makes the changes since the last step a step of their own, if they changed anything. */
    void record() {
        typing.stop();
        Settings current = settings.get();
        if (TemplateCodec.sameSettings(last, current)) {
            return;
        }
        push(undo, last);
        redo.clear();
        last = current.copy();
    }

    /** Starts again from the current settings, with nothing to undo or redo. */
    void reset() {
        typing.stop();
        undo.clear();
        redo.clear();
        last = settings.get().copy();
    }

    /**
     * Puts the settings back as they were before the last step, in place.
     *
     * @return the settings as they were before undoing, or {@code null} if there is nothing to undo
     */
    Settings undo() {
        return move(undo, redo);
    }

    /** Like {@link #undo}, for the last step undone. */
    Settings redo() {
        return move(redo, undo);
    }

    private Settings move(Deque<Settings> from, Deque<Settings> to) {
        record();
        if (from.isEmpty()) {
            return null;
        }
        Settings current = settings.get();
        Settings before = current.copy();
        push(to, last);
        last = from.pop();
        current.copyFrom(last);
        return before;
    }

    private static void push(Deque<Settings> steps, Settings step) {
        steps.push(step);
        if (steps.size() > LIMIT) {
            steps.removeLast();
        }
    }

    /**
     * Binds Undo (⌘Z, or Ctrl+Z) and Redo (⇧⌘Z, or Ctrl+Shift+Z and Ctrl+Y) on {@code component}, for the given
     * {@link JComponent#getInputMap(int) condition}.
     */
    static void bindKeys(JComponent component, int condition, Runnable undo, Runnable redo) {
        int menu = Widgets.menuShortcutMask();
        bind(component, condition, KeyStroke.getKeyStroke(KeyEvent.VK_Z, menu), "screenshot-mode.undo", undo);
        bind(
            component,
            condition,
            KeyStroke.getKeyStroke(KeyEvent.VK_Z, menu | InputEvent.SHIFT_DOWN_MASK),
            "screenshot-mode.redo",
            redo
        );
        if (menu == InputEvent.CTRL_DOWN_MASK) {
            bind(component, condition, KeyStroke.getKeyStroke(KeyEvent.VK_Y, menu), "screenshot-mode.redo", redo);
        }
    }

    private static void bind(JComponent component, int condition, KeyStroke key, String name, Runnable action) {
        component.getInputMap(condition).put(key, name);
        component.getActionMap().put(
            name,
            new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    action.run();
                }
            }
        );
    }
}
