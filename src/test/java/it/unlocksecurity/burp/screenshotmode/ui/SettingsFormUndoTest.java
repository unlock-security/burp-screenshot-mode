package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.Disposition;
import it.unlocksecurity.burp.screenshotmode.model.HeightMode;
import it.unlocksecurity.burp.screenshotmode.model.MessageMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleType;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.ViewMode;
import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import it.unlocksecurity.burp.screenshotmode.storage.HeaderCatalogFile;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import org.junit.jupiter.api.Test;

/** The settings form wired to a history, as the screenshot window and the template editor do. */
class SettingsFormUndoTest {

    private final Settings settings = BuiltInTemplates.defaults();
    private final SettingsHistory history = new SettingsHistory(() -> settings);
    private final SettingsForm form = new SettingsForm(
        settings,
        () -> history.changed(false),
        new Font(Font.MONOSPACED, Font.PLAIN, 12),
        new HeadersTab.InMessage(List.of("Cookie"), List.of())
    );

    {
        form.setUndo(() -> undo(false));
    }

    private void undo(boolean redo) {
        Settings before = redo ? history.redo() : history.undo();
        form.reload(before);
    }

    @Test
    void offersUndoAfterADeletionAndOpensTheRuleThatCameBack() {
        RedactionRule rule = settings.redactions.get(1);
        form.redactionList().row(rule).delete();
        assertFalse(settings.redactions.stream().anyMatch(r -> r.id.equals(rule.id)));
        assertTrue(form.notice().isVisible());
        assertEquals("Rule deleted.", form.noticeText().getText());
        assertEquals("Undo", form.noticeAction().getText());

        form.noticeAction().doClick();
        RedactionRule back = settings.redactions.get(1);
        assertEquals(rule.id, back.id, "back in its place");
        assertNotSame(rule, back);
        assertTrue(form.redactionList().row(back).isExpanded(), "opened, to show what came back");
        assertFalse(form.notice().isVisible());

        undo(true);
        assertFalse(settings.redactions.stream().anyMatch(r -> r.id.equals(rule.id)), "redone");
    }

    @Test
    void theUndoNoticeEndsWithTheNextChange() {
        form.redactionList().row(settings.redactions.getFirst()).delete();
        assertTrue(form.notice().isVisible());
        form.redactionList().row(settings.redactions.getFirst()).delete();
        assertTrue(form.notice().isVisible(), "a new deletion, a new notice");

        form.highlightList().addRuleForTests(RuleType.PATTERN);
        assertFalse(form.notice().isVisible(), "Undo would now undo the new rule, not the deletion");
    }

    @Test
    void showsTheTabWhereTheChangeWasUndone() {
        form.tabs().setSelectedIndex(SettingsForm.RULES);
        settings.headers.setGroup("caching", false, HeaderCatalogFile.bundled());
        history.record();
        undo(false);
        assertEquals(SettingsForm.HEADERS, form.tabs().getSelectedIndex());

        settings.disposition = Disposition.VERTICAL;
        history.record();
        undo(false);
        assertEquals(SettingsForm.APPEARANCE, form.tabs().getSelectedIndex());
        assertEquals(Disposition.HORIZONTAL, settings.disposition);

        // A step that also changed the open tab, like Revert, keeps it.
        settings.view = ViewMode.PRETTY;
        settings.redactions.removeFirst();
        history.record();
        undo(false);
        assertEquals(SettingsForm.APPEARANCE, form.tabs().getSelectedIndex());
        assertEquals(ViewMode.RAW, settings.view);
    }

    @Test
    void aDraggedHeightShowsInTheHeightRowAndIsOneStep() {
        heightCombo().setSelectedItem(HeightMode.CONTENT);
        assertEquals(HeightMode.CONTENT, settings.heightMode);

        // What the screenshot window does while the handle is dragged, then when it is let go.
        settings.heightMode = HeightMode.PIXEL;
        settings.heightPixels = 300;
        form.showSize();
        settings.heightPixels = 432;
        form.showSize();
        history.record();
        assertEquals(HeightMode.PIXEL, heightCombo().getSelectedItem());
        JSpinner pixels = (JSpinner) find(form, JSpinner.class, spinner -> spinner.getValue().equals(432));
        assertTrue(pixels.isVisible());
        assertEquals(432, settings.heightPixels, "showing the height is no change of its own");

        undo(false);
        assertEquals(HeightMode.CONTENT, settings.heightMode, "the whole drag undone at once");
        assertEquals(HeightMode.CONTENT, heightCombo().getSelectedItem());
        assertEquals(SettingsForm.APPEARANCE, form.tabs().getSelectedIndex());
    }

    @Test
    void theWidthRowListsItsChoicesInOrderAndShowsADraggedWidth() {
        JComboBox<WidthMode> combo = widthCombo();
        List<WidthMode> order = new java.util.ArrayList<>();
        for (int i = 0; i < combo.getItemCount(); i++) {
            order.add(combo.getItemAt(i));
        }
        assertEquals(
            List.of(WidthMode.FULL, WidthMode.CONTENT, WidthMode.A4, WidthMode.LETTER, WidthMode.PIXEL),
            order
        );

        settings.widthMode = WidthMode.PIXEL;
        settings.widthPixels = 1234;
        form.showSize();
        history.record();
        assertEquals(WidthMode.PIXEL, combo.getSelectedItem());
        assertTrue(find(form, JSpinner.class, spinner -> spinner.getValue().equals(1234)).isVisible());

        combo.setSelectedItem(WidthMode.FULL);
        combo.setSelectedItem(WidthMode.PIXEL);
        assertEquals(1234, settings.widthPixels, "choosing Custom again keeps the last custom width");

        undo(false);
        undo(false);
        undo(false);
        assertEquals(WidthMode.FULL, settings.widthMode, "the drag was one step");
    }

    @Test
    void disablesTheLayoutWhileOneMessageIsShown() {
        segmented(MessageMode.BOTH).click(MessageMode.REQUEST);
        assertEquals(MessageMode.REQUEST, settings.messages);
        assertFalse(segmented(Disposition.HORIZONTAL).isEnabled(), "it would change nothing");
        assertEquals(Disposition.HORIZONTAL, settings.disposition, "kept for when both are shown again");

        undo(false);
        assertEquals(MessageMode.BOTH, settings.messages);
        assertTrue(segmented(Disposition.HORIZONTAL).isEnabled());

        undo(true);
        assertFalse(segmented(Disposition.HORIZONTAL).isEnabled(), "as the reloaded tab is built");
    }

    @Test
    void hidesTheLineNumbersAndUndoes() {
        segmented(true).click(false);
        assertFalse(settings.lineNumbers);
        undo(false);
        assertTrue(settings.lineNumbers);
        assertEquals(SettingsForm.APPEARANCE, form.tabs().getSelectedIndex());
        assertEquals(true, segmented(true).value(), "the reloaded tab shows it");
    }

    /** The segmented control that shows {@code value}. */
    @SuppressWarnings("unchecked")
    private <E> SegmentedControl<E> segmented(E value) {
        return (SegmentedControl<E>) find(form, SegmentedControl.class, control -> control.value() == value);
    }

    @SuppressWarnings("unchecked")
    private JComboBox<WidthMode> widthCombo() {
        return (JComboBox<WidthMode>) find(form, JComboBox.class, combo -> combo.getItemAt(0) == WidthMode.FULL);
    }

    @SuppressWarnings("unchecked")
    private JComboBox<HeightMode> heightCombo() {
        return (JComboBox<HeightMode>) find(form, JComboBox.class, combo -> combo.getItemAt(0) == HeightMode.FULL);
    }

    private static <T extends Component> T find(Container root, Class<T> type, Predicate<T> filter) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && filter.test(type.cast(child))) {
                return type.cast(child);
            }
            if (child instanceof Container container) {
                T found = find(container, type, filter);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    void keepsTheOpenRuleOpen() {
        RedactionRule open = settings.redactions.get(2);
        form.expand(open);
        settings.disposition = Disposition.VERTICAL;
        history.record();
        undo(false);
        RedactionRule reloaded = settings.redactions.get(2);
        assertTrue(form.redactionList().row(reloaded).isExpanded());
    }

    @Test
    void warnsInTheRulesTabWhenARedactionRedactsNothing() {
        RedactionRule rule = settings.redactions
            .stream()
            .filter(r -> r.name.equals("Authorization credentials"))
            .findFirst()
            .orElseThrow();
        rule.selectedGroups = new java.util.ArrayList<>();
        form.showRuleStatuses(java.util.Map.of());
        javax.swing.JPanel title = (javax.swing.JPanel) form.tabs().getTabComponentAt(SettingsForm.RULES);
        assertEquals("⚠ 1", ((javax.swing.JLabel) title.getComponent(1)).getText());
    }
}
