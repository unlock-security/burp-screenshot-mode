package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.RuleType;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.render.RuleStatus;
import it.unlocksecurity.burp.screenshotmode.storage.DefaultTemplate;
import java.awt.Font;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuleListTest {

    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private final Settings settings = DefaultTemplate.settings();
    private int changes;
    private final SettingsForm form = new SettingsForm(settings, () -> changes++, MONO, false);

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
        SettingsForm form = new SettingsForm(settings, () -> {}, MONO, false);
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
        SettingsForm form = new SettingsForm(settings, () -> {}, MONO, false);
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
    void editsUpdateTheRuleLine() {
        RedactionRule cookie = named("Cookie values");
        RuleList<RedactionRule>.Row row = form.redactionList().row(cookie);
        row.header().toggle();
        javax.swing.JTextField headers = (javax.swing.JTextField) find(row.editorPanel(), "Headers");
        headers.setText("Cookie, X-Session");
        assertEquals(List.of("Cookie", "X-Session"), cookie.headers);
        assertEquals("Header · Cookie and 1 more · Replace with [OMITTED]", row.summaryLabel().getText());
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

    /** The control a label is for. */
    private static java.awt.Component find(java.awt.Container container, String label) {
        for (java.awt.Component component : container.getComponents()) {
            if (
                component instanceof javax.swing.JLabel title &&
                label.equals(title.getText()) &&
                title.getLabelFor() != null
            ) {
                java.awt.Component target = title.getLabelFor();
                if (target instanceof java.awt.Container holder && !(target instanceof javax.swing.JTextField)) {
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
