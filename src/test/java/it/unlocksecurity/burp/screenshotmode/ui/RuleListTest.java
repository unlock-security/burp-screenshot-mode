package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.RuleType;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.render.RuleStatus;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import java.awt.Font;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleListTest {

    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private final Settings settings = BuiltInTemplates.defaults();
    private int changes;
    private final SettingsForm form = new SettingsForm(settings, () -> changes++, MONO);

    private RedactionRule named(String name) {
        return settings.redactions
            .stream()
            .filter(rule -> rule.name.equals(name))
            .findFirst()
            .orElseThrow();
    }

    @Test
    void describesRulesInWords() {
        assertEquals("Header · X-Api-Key and 4 more · Replace", RuleList.summary(named("API key headers")));
        assertEquals("Header · Cookie · Replace with [OMITTED]", RuleList.summary(named("Cookie values")));
        assertEquals("Request · Response · URL · Replace", RuleList.summary(named("JSON Web Tokens")));

        HighlightRule pattern = HighlightRule.create("\"role\": \"admin\"", RuleTarget.REQUEST, MatchMode.STRING);
        pattern.targets.add(RuleTarget.RESPONSE);
        assertEquals("\"role\": \"admin\"", RuleList.title(pattern), "unnamed rules show their pattern");
        assertEquals("Request · Response · Highlight", RuleList.summary(pattern));

        HighlightRule header = HighlightRule.forHeaders(List.of("Strict-Transport-Security"));
        assertEquals("Strict-Transport-Security", RuleList.title(header));
        assertEquals("Header · Highlight the whole line", RuleList.summary(header));
        assertEquals("No headers yet", RuleList.title(HighlightRule.forHeaders(List.of())));
    }

    @Test
    void opensOneRuleAtATimeAcrossBothLists() {
        settings.highlights.add(HighlightRule.create("x", RuleTarget.REQUEST, MatchMode.STRING));
        SettingsForm form = new SettingsForm(settings, () -> {}, MONO);
        RuleList<RedactionRule>.Row cookie = form.redactionList().row(named("Cookie values"));
        RuleList<HighlightRule>.Row highlight = form.highlightList().row(settings.highlights.getFirst());

        cookie.header().toggle();
        assertTrue(cookie.isExpanded());
        highlight.header().toggle();
        assertTrue(highlight.isExpanded());
        assertFalse(cookie.isExpanded());
        highlight.header().toggle();
        assertFalse(highlight.isExpanded());
    }

    @Test
    void opensARuleFromAnyPartOfItsLine() {
        RuleList<RedactionRule>.Row row = form.redactionList().row(named("Cookie values"));
        // The title and the pill have tooltips, which keep mouse events from reaching the line.
        assertEquals(Widgets.Glyph.CHEVRON_DOWN, row.chevron().getIcon());
        click(row.titleLabel());
        assertTrue(row.isExpanded());
        assertEquals(Widgets.Glyph.CHEVRON_UP, row.chevron().getIcon());
        click(row.pill());
        assertFalse(row.isExpanded());
        assertEquals(Widgets.Glyph.CHEVRON_DOWN, row.chevron().getIcon());
    }

    @Test
    void turnsARuleOffFromItsLine() {
        RedactionRule cookie = named("Cookie values");
        RuleList<RedactionRule>.Row row = form.redactionList().row(cookie);
        Map<Object, RuleStatus> statuses = new IdentityHashMap<>();
        statuses.put(cookie, new RuleStatus(1, null, null));
        form.showRuleStatuses(statuses);
        assertEquals("1", row.pill().getText());
        assertEquals(RuleList.Pill.Style.FILLED, row.pill().style());

        clickEnabled(row);
        assertFalse(cookie.enabled);
        assertEquals("Off", row.pill().getText());
        assertEquals(1, changes);
    }

    @Test
    void flagsRulesThatFailed() {
        RedactionRule rule = RedactionRule.create("(", RuleTarget.REQUEST, RedactionMode.REPLACE, MatchMode.REGEX);
        settings.redactions.addFirst(rule);
        SettingsForm form = new SettingsForm(settings, () -> {}, MONO);
        Map<Object, RuleStatus> statuses = new IdentityHashMap<>();
        statuses.put(rule, new RuleStatus(0, RuleStatus.Problem.INVALID, "Unclosed group"));
        form.showRuleStatuses(statuses);
        RuleList.Pill shown = form.redactionList().row(rule).pill();
        assertEquals("Error", shown.getText());
        assertEquals(RuleList.Pill.Style.ERROR, shown.style());
        assertTrue(shown.getToolTipText().contains("Unclosed group"));
    }

    @Test
    void addsAHeaderRuleFirstAndOpen() {
        RuleList<RedactionRule> list = form.redactionList();
        list.addRuleForTests(RuleType.HEADER);
        RedactionRule added = settings.redactions.getFirst();
        assertEquals(RuleType.HEADER, added.type);
        assertEquals(RedactionMode.REPLACE, added.mode);
        assertTrue(list.row(added).isExpanded());
    }

    @Test
    void choosesBetweenTheWholeMatchAndSomeCaptureGroups() {
        RedactionRule rule = RedactionRule.create("token", RuleTarget.REQUEST, RedactionMode.REPLACE, MatchMode.REGEX);
        form.redactionList().addFirst(rule);
        RuleList<RedactionRule>.Row row = form.redactionList().row(rule);
        javax.swing.JLabel what = label(row.editorPanel(), "What");
        assertFalse(what.isVisible(), "no capture groups, nothing to choose");

        ((javax.swing.text.JTextComponent) find(row.editorPanel(), "Pattern")).setText("token=(?<Value>\\w+)");
        assertTrue(what.isVisible());
        javax.swing.AbstractButton some = button(row.editorPanel(), "Only these capture groups:");
        javax.swing.AbstractButton value = button(row.editorPanel(), "Value");
        assertTrue(some.isSelected(), "a new group is redacted on its own");
        assertTrue(value.isSelected() && value.isEnabled());

        button(row.editorPanel(), "The whole match").doClick();
        assertFalse(rule.useCaptureGroups);
        assertFalse(value.isEnabled(), "the groups belong to the other choice");
        some.doClick();
        assertEquals(List.of(1), rule.selectedGroups);

        // Only some groups, but none: the card says the rule redacts nothing.
        javax.swing.JTextArea nothing = textArea(row.editorPanel(), "Tick at least one group");
        assertFalse(nothing.isVisible());
        value.doClick();
        assertTrue(rule.selectedGroups.isEmpty());
        assertTrue(nothing.isVisible());
        value.doClick();
        assertFalse(nothing.isVisible());
        value.doClick();
        button(row.editorPanel(), "The whole match").doClick();
        assertFalse(nothing.isVisible(), "the whole match is redacted again");
    }

    private static javax.swing.JTextArea textArea(java.awt.Container container, String start) {
        for (java.awt.Component component : container.getComponents()) {
            if (component instanceof javax.swing.JTextArea area && area.getText().startsWith(start)) {
                return area;
            }
            if (component instanceof java.awt.Container child) {
                javax.swing.JTextArea found = textArea(child, start);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    void groupsTheCardIntoMatchAndRedact() {
        RedactionRule rule = named("Authorization credentials");
        RuleList<RedactionRule>.Row row = form.redactionList().row(rule);
        row.header().toggle();
        List<String> order = new java.util.ArrayList<>();
        for (java.awt.Component component : row.editorPanel().getComponents()) {
            if (component instanceof javax.swing.JLabel label && label.isVisible()) {
                order.add(label.getText());
            }
        }
        assertEquals(List.of("Name", "MATCH", "Pattern", "Options", "Applies to", "REDACT", "What", "With"), order);
        assertTrue(button(row.editorPanel(), "Delete rule") != null, "a labelled action at the end of the card");
    }

    @Test
    void explainsAnInvalidPatternRightUnderIt() {
        RedactionRule rule = RedactionRule.create(
            "(unclosed",
            RuleTarget.REQUEST,
            RedactionMode.REPLACE,
            MatchMode.REGEX
        );
        form.redactionList().addFirst(rule);
        RuleList<RedactionRule>.Row row = form.redactionList().row(rule);
        form.showRuleStatuses(
            java.util.Map.of(
                rule,
                new it.unlocksecurity.burp.screenshotmode.render.RuleStatus(
                    0,
                    it.unlocksecurity.burp.screenshotmode.render.RuleStatus.Problem.INVALID,
                    "Unclosed group"
                )
            )
        );
        java.awt.GridBagLayout grid = (java.awt.GridBagLayout) row.editorPanel().getLayout();
        java.awt.Component pattern = find(row.editorPanel(), "Pattern");
        java.awt.Component problem = null;
        for (java.awt.Component component : row.editorPanel().getComponents()) {
            if (component instanceof javax.swing.JTextArea area && area.getText().startsWith("⚠ Invalid regex")) {
                problem = area;
            }
        }
        assertTrue(problem != null && problem.isVisible());
        assertEquals(grid.getConstraints(pattern).gridy + 1, grid.getConstraints(problem).gridy);
    }

    @Test
    void anyPlaceCanBeUntickedAndTheCardSaysSo() {
        RedactionRule rule = RedactionRule.create("token", RuleTarget.REQUEST, RedactionMode.REPLACE, MatchMode.REGEX);
        form.redactionList().addFirst(rule);
        RuleList<RedactionRule>.Row row = form.redactionList().row(rule);
        javax.swing.AbstractButton request = button(row.editorPanel(), "Request");
        assertTrue(request.isEnabled(), "the last place is not greyed out");
        request.doClick();
        assertTrue(rule.targets.isEmpty());
        assertEquals("Off", row.pill().getText());
        assertTrue(row.summaryLabel().getText().startsWith("Applies nowhere"));
        javax.swing.JTextArea nowhere = null;
        for (java.awt.Component component : row.editorPanel().getComponents()) {
            if (component instanceof javax.swing.JTextArea area && area.getText().startsWith("Tick at least one")) {
                nowhere = area;
            }
        }
        assertTrue(nowhere != null && nowhere.isVisible());
        request.doClick();
        assertFalse(nowhere.isVisible());
    }

    @Test
    void offersTheSafestRedactionStyleFirst() {
        RedactionRule rule = named("Authorization credentials");
        RuleList<RedactionRule>.Row row = form.redactionList().row(rule);
        row.header().toggle();
        javax.swing.JComboBox<?> style = (javax.swing.JComboBox<?>) find(row.editorPanel(), "With");
        List<String> labels = new java.util.ArrayList<>();
        for (int i = 0; i < style.getItemCount(); i++) {
            labels.add(style.getItemAt(i).toString());
        }
        assertEquals(List.of("Text", "Black box", "Blur"), labels);
    }

    @Test
    void deletesFromTheEndOfTheCard() {
        RedactionRule rule = named("Cookie values");
        RuleList<RedactionRule>.Row row = form.redactionList().row(rule);
        row.header().toggle();
        javax.swing.AbstractButton delete = button(row.editorPanel(), "Delete rule");
        assertEquals(java.awt.Font.PLAIN, delete.getFont().getStyle(), "not bold: it shouldn't invite a click");
        assertEquals(
            java.awt.BorderLayout.EAST,
            ((java.awt.BorderLayout) delete.getParent().getLayout()).getConstraints(delete),
            "on the right"
        );
        delete.doClick();
        assertFalse(settings.redactions.contains(rule));
    }

    @Test
    void editsUpdateTheRuleLine() {
        RedactionRule cookie = named("Cookie values");
        RuleList<RedactionRule>.Row row = form.redactionList().row(cookie);
        row.header().toggle();
        javax.swing.text.JTextComponent headers = (javax.swing.text.JTextComponent) find(row.editorPanel(), "Headers");
        headers.setText("Cookie, X-Session");
        assertEquals(List.of("Cookie", "X-Session"), cookie.headers);
        assertEquals("Header · Cookie and 1 more · Replace with [OMITTED]", row.summaryLabel().getText());
    }

    @Test
    void showsTheWholePatternAndItsOptionsInWords() {
        RedactionRule rule = named("Authorization credentials");
        RuleList<RedactionRule>.Row row = form.redactionList().row(rule);
        row.header().toggle();
        javax.swing.JTextArea pattern = (javax.swing.JTextArea) find(row.editorPanel(), "Pattern");
        assertEquals(rule.regex, pattern.getText());
        assertTrue(pattern.getLineWrap(), "wraps instead of scrolling sideways");

        // Options in Burp's words, Regex first.
        javax.swing.AbstractButton regex = button(row.editorPanel(), "Regex");
        assertTrue(regex.isSelected());
        assertSame(regex.getParent(), find(row.editorPanel(), "Options").getParent());
        button(row.editorPanel(), "Case sensitive").doClick();
        assertFalse(rule.matchCase);

        pattern.setText("a\nb\nc");
        assertEquals("abc", rule.regex, "one line: line breaks are dropped");
    }

    private static void click(java.awt.Component component) {
        component.dispatchEvent(
            new java.awt.event.MouseEvent(
                component,
                java.awt.event.MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                java.awt.event.InputEvent.BUTTON1_DOWN_MASK,
                2,
                2,
                1,
                false,
                java.awt.event.MouseEvent.BUTTON1
            )
        );
    }

    @Test
    void keepsTheHeaderMenuOpenWhileHeadersAreTicked() {
        RedactionRule rule = RedactionRule.forHeaders(List.of(), RedactionMode.REPLACE);
        javax.swing.JTextField field = new javax.swing.JTextField();
        javax.swing.JPopupMenu menu = RuleList.headerMenu(
            rule,
            it.unlocksecurity.burp.screenshotmode.storage.HeaderCatalogFile.bundled(),
            field
        );
        javax.swing.JMenu group = (javax.swing.JMenu) menu.getComponent(0);
        javax.swing.JCheckBoxMenuItem first = (javax.swing.JCheckBoxMenuItem) group.getMenuComponent(0);
        javax.swing.JCheckBoxMenuItem second = (javax.swing.JCheckBoxMenuItem) group.getMenuComponent(1);
        assertEquals(Boolean.TRUE, first.getClientProperty("CheckBoxMenuItem.doNotCloseOnMouseClick"));

        // The field updates the rule as it changes, so a second tick in the same menu adds to the first.
        Widgets.onTextChange(field, text -> rule.headers = List.of(text.split(", ")));
        first.doClick();
        second.doClick();
        assertEquals(first.getText() + ", " + second.getText(), field.getText());
    }

    private static void clickEnabled(RuleList<?>.Row row) {
        for (java.awt.Component component : row.header().getComponents()) {
            if (component instanceof javax.swing.JCheckBox box) {
                box.doClick();
                return;
            }
        }
        throw new AssertionError("no checkbox");
    }

    private static javax.swing.JLabel label(java.awt.Container container, String text) {
        for (java.awt.Component component : container.getComponents()) {
            if (component instanceof javax.swing.JLabel label && text.equals(label.getText())) {
                return label;
            }
            if (component instanceof java.awt.Container child) {
                javax.swing.JLabel found = label(child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** The button with this text or accessible name. */
    private static javax.swing.AbstractButton button(java.awt.Container container, String name) {
        for (java.awt.Component component : container.getComponents()) {
            if (
                component instanceof javax.swing.AbstractButton button &&
                (name.equals(button.getText()) || name.equals(button.getAccessibleContext().getAccessibleName()))
            ) {
                return button;
            }
            if (component instanceof java.awt.Container child) {
                javax.swing.AbstractButton found = button(child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** The control a label is for. */
    private static java.awt.Component find(java.awt.Container container, String label) {
        for (java.awt.Component component : container.getComponents()) {
            if (
                component instanceof javax.swing.JLabel title &&
                label.equals(title.getText()) &&
                title.getLabelFor() != null
            ) {
                java.awt.Component target = title.getLabelFor();
                if (
                    target instanceof java.awt.Container holder &&
                    !(target instanceof javax.swing.text.JTextComponent) &&
                    holder.getComponentCount() > 0
                ) {
                    return holder.getComponent(0);
                }
                return target;
            }
            if (component instanceof java.awt.Container child) {
                java.awt.Component found = find(child, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
