package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import org.junit.jupiter.api.Test;

class SettingsFormLayoutTest {

    @Test
    void wrapsAppearanceOptionsInTheNarrowestSidebar() throws Exception {
        SettingsForm form = new SettingsForm(
            BuiltInTemplates.defaults(),
            () -> {},
            new Font(Font.MONOSPACED, Font.PLAIN, 12)
        );
        form.tabs().setSelectedIndex(SettingsForm.APPEARANCE);
        // The screenshot window's sidebar can be dragged down to 300 px.
        form.setSize(300, 900);
        WrapLayoutTest.layOut(form);
        Container appearance = (Container) form.tabs().getSelectedComponent();
        List<AbstractButton> options = new ArrayList<>();
        collect(appearance, options);
        assertTrue(options.size() > 10, "found the options");
        for (AbstractButton option : options) {
            assertTrue(WrapLayoutTest.inView(option, appearance), option.getText() + " in view");
        }
    }

    @Test
    void linesTheNoticeLinkUpWithItsTextWhenItWrapsBelow() throws Exception {
        SettingsForm form = new SettingsForm(
            BuiltInTemplates.defaults(),
            () -> {},
            new Font(Font.MONOSPACED, Font.PLAIN, 12)
        );
        form.noticeText().setText("This rule redacts Authorization and Proxy-Authorization.");
        form.noticeAction().setText("‹ Back to Headers");
        form.notice().setVisible(true);
        form.setSize(300, 900);
        WrapLayoutTest.layOut(form);
        assertTrue(form.noticeAction().getY() > form.noticeText().getY(), "wrapped below the text");
        assertEquals(
            form.noticeText().getX() + form.noticeText().getInsets().left,
            form.noticeAction().getX() + form.noticeAction().getInsets().left,
            "both texts start at the same x"
        );
    }

    private static void collect(Container container, List<AbstractButton> buttons) {
        for (Component component : container.getComponents()) {
            if (component instanceof AbstractButton button && button.getText() != null && !button.getText().isEmpty()) {
                buttons.add(button);
            } else if (component instanceof Container child) {
                collect(child, buttons);
            }
        }
    }
}
