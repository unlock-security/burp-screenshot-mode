package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.Disposition;
import it.unlocksecurity.burp.screenshotmode.model.ThemeMode;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SegmentedControlTest {

    @Test
    void clickingASegmentSelectsItAndNotifies() {
        List<Disposition> changes = new ArrayList<>();
        SegmentedControl<Disposition> control = new SegmentedControl<>(
            Disposition.values(),
            Disposition.HORIZONTAL,
            changes::add
        );
        control.click(Disposition.VERTICAL);
        control.click(Disposition.VERTICAL);
        assertEquals(Disposition.VERTICAL, control.value());
        assertEquals(List.of(Disposition.VERTICAL), changes, "re-selecting the current value is not a change");
    }

    @Test
    void settingTheValueDoesNotNotify() {
        List<ThemeMode> changes = new ArrayList<>();
        SegmentedControl<ThemeMode> control = new SegmentedControl<>(ThemeMode.values(), ThemeMode.AUTO, changes::add);
        control.setValue(ThemeMode.DARK);
        assertEquals(ThemeMode.DARK, control.value());
        assertTrue(changes.isEmpty());
    }

    @Test
    void arrowKeysMoveTheSelectionAndWrap() {
        List<ThemeMode> changes = new ArrayList<>();
        SegmentedControl<ThemeMode> control = new SegmentedControl<>(ThemeMode.values(), ThemeMode.AUTO, changes::add);
        press(control, KeyEvent.VK_RIGHT);
        press(control, KeyEvent.VK_RIGHT);
        press(control, KeyEvent.VK_RIGHT);
        assertEquals(List.of(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.AUTO), changes);
        press(control, KeyEvent.VK_LEFT);
        assertEquals(ThemeMode.LIGHT, control.value());
    }

    @Test
    void labelsCanChangeAndResizeTheControl() {
        SegmentedControl<Disposition> control = new SegmentedControl<>(
            Disposition.values(),
            Disposition.HORIZONTAL,
            value -> {}
        );
        int before = control.getPreferredSize().width;
        control.setLabel(Disposition.VERTICAL, "Stacked (one above the other)");
        assertTrue(control.getPreferredSize().width > before);
    }

    private static void press(SegmentedControl<?> control, int key) {
        KeyEvent event = new KeyEvent(control, KeyEvent.KEY_PRESSED, 0, 0, key, KeyEvent.CHAR_UNDEFINED);
        for (var listener : control.getKeyListeners()) {
            listener.keyPressed(event);
        }
    }
}
