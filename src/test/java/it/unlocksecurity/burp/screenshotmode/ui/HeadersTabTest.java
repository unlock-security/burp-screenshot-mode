package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.HeaderCatalog;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.Visibility;
import it.unlocksecurity.burp.screenshotmode.storage.BuiltInTemplates;
import it.unlocksecurity.burp.screenshotmode.storage.HeaderCatalogFile;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.text.JTextComponent;
import org.junit.jupiter.api.Test;

class HeadersTabTest {

    private static final HeaderCatalog CATALOG = HeaderCatalogFile.bundled();

    private static final HeadersTab.InMessage MESSAGE = new HeadersTab.InMessage(
        List.of("host", "user-agent", "accept", "sec-fetch-mode", "content-length", "x-backend"),
        List.of("date", "content-type", "content-length", "etag", "x-backend")
    );

    private final Settings settings = BuiltInTemplates.defaults();
    private int changes;

    private HeadersTab tab(HeadersTab.InMessage message) {
        return new HeadersTab(settings, CATALOG, message, () -> changes++);
    }

    private static List<String> labels(HeadersTab tab) {
        return tab
            .rows()
            .stream()
            .map(row ->
                row instanceof HeadersTab.GroupRow group ? "> " + group.label : ((HeadersTab.HeaderRow) row).name()
            )
            .toList();
    }

    private static Object row(HeadersTab tab, String label) {
        tab.select(label);
        return tab.rows().get(tab.table().getSelectedRow());
    }

    private static void press(HeadersTab tab, String action) {
        tab.table().getActionMap().get(action).actionPerformed(null);
    }

    @Test
    void listsTheMessageHeadersWithCustomOnesFirstAndOpen() {
        HeadersTab tab = tab(MESSAGE);
        assertEquals(
            List.of(
                "> Custom headers",
                "x-backend",
                "> Client identity",
                "> Routing and origin",
                "> Message framing",
                "> Content",
                "> Content negotiation",
                "> Caching",
                "> Fetch metadata and hints",
                "> General"
            ),
            labels(tab)
        );
        assertEquals(4, tab.hiddenCount(), "Accept, Sec-Fetch-Mode, Date, ETag");
    }

    @Test
    void togglesAHeaderWithSpace() {
        HeadersTab tab = tab(MESSAGE);
        tab.select("x-backend");
        press(tab, "toggleShow");
        assertEquals(Visibility.HIDE, settings.headers.of("X-Backend", CATALOG));
        assertEquals(1, changes);
        assertEquals(6, tab.hiddenCount(), "hidden in both messages");
        press(tab, "toggleShow");
        assertEquals(Visibility.SHOW, settings.headers.of("X-Backend", CATALOG));
    }

    @Test
    void expandsAndCollapsesGroupsWithTheArrowKeys() {
        HeadersTab tab = tab(MESSAGE);
        tab.select("Caching");
        press(tab, "expand");
        assertTrue(labels(tab).contains("ETag"));
        tab.select("ETag");
        press(tab, "collapse");
        assertEquals(tab.rows().indexOf(row(tab, "Caching")), tab.table().getSelectedRow(), "goes to the group");
        press(tab, "collapse");
        assertFalse(labels(tab).contains("ETag"));
    }

    @Test
    void togglesAWholeGroup() {
        HeadersTab tab = tab(null);
        HeadersTab.GroupRow security = (HeadersTab.GroupRow) row(tab, "Security policy");
        assertEquals(HeadersTab.State.SHOWN, tab.state(security));
        tab.toggle(security);
        assertTrue(settings.headers.isGroupHidden("security"));
        assertEquals(Visibility.HIDE, settings.headers.of("X-Frame-Options", CATALOG));

        settings.headers.set("X-Frame-Options", Visibility.SHOW, CATALOG);
        assertEquals(HeadersTab.State.MIXED, tab.state(security));
        tab.toggle(security);
        assertFalse(settings.headers.isGroupHidden("security"), "a mixed group shows all its headers");
        assertTrue(settings.headers.headers.isEmpty());
    }

    @Test
    void showsAHeaderHiddenInOneMessageAsMixed() {
        settings.headers.set("Content-Length", Visibility.HIDE_IN_RESPONSE, CATALOG);
        HeadersTab tab = tab(MESSAGE);
        tab.select("Message framing");
        press(tab, "expand");
        HeadersTab.HeaderRow row = (HeadersTab.HeaderRow) row(tab, "Content-Length");
        assertEquals(HeadersTab.State.MIXED, tab.state(row));
        assertTrue(texts(tab.details()).contains("Hide in"), "lets the user pick the messages");
        tab.toggle(row);
        assertEquals(Visibility.SHOW, settings.headers.of("Content-Length", CATALOG));
    }

    @Test
    void filtersAcrossGroupsAndOpensThem() {
        HeadersTab tab = tab(null);
        tab.filter().setText("x-amz-c");
        assertEquals(List.of("> CDN and infrastructure", "X-Amz-Cf-Id", "X-Amz-Cf-Pop"), labels(tab));
        tab.filter().setText("cors");
        assertEquals(9, tab.rows().size(), "a group's name lists all its headers");
    }

    @Test
    void listsEveryHeaderInTheTemplateEditor() {
        settings.headers.set("X-Mine", Visibility.HIDE, CATALOG);
        HeadersTab tab = tab(null);
        assertEquals("> Custom headers", labels(tab).getFirst());
        assertEquals("X-Mine", labels(tab).get(1));
        assertEquals(
            18,
            labels(tab)
                .stream()
                .filter(label -> label.startsWith("> "))
                .count()
        );
    }

    private record Link(String kind, it.unlocksecurity.burp.screenshotmode.model.Rule rule, String notice) {}

    private final List<Link> links = new ArrayList<>();

    private HeadersTab linked(HeadersTab.InMessage message) {
        return new HeadersTab(
            settings,
            CATALOG,
            message,
            () -> changes++,
            new HeadersTab.Links() {
                @Override
                public void create(it.unlocksecurity.burp.screenshotmode.model.Rule rule, String notice) {
                    links.add(new Link("create", rule, notice));
                }

                @Override
                public void open(it.unlocksecurity.burp.screenshotmode.model.Rule rule, String notice) {
                    links.add(new Link("open", rule, notice));
                }
            }
        );
    }

    @Test
    void findsTheRulesNamingAHeaderOrChangingIt() {
        HeadersTab tab = linked(MESSAGE);
        assertEquals(
            List.of("User-Agent"),
            tab
                .rulesFor("user-agent", true)
                .stream()
                .map(rule -> rule.name)
                .toList()
        );
        assertTrue(tab.rulesFor("host", true).isEmpty());

        var authorization = settings.redactions
            .stream()
            .filter(r -> r.name.equals("Authorization credentials"))
            .findFirst()
            .orElseThrow();
        java.util.Map<String, java.util.Set<it.unlocksecurity.burp.screenshotmode.model.Rule>> request =
            new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        request.put("host", java.util.Set.of(authorization));
        tab.showHits(java.util.Map.of(it.unlocksecurity.burp.screenshotmode.model.RuleTarget.REQUEST, request));
        assertEquals(List.of(authorization), tab.rulesFor("Host", true));
        assertTrue(labels(tab).contains("Host"), "the group of a header a rule changed opens");

        authorization.enabled = false;
        assertTrue(tab.rulesFor("Host", true).isEmpty(), "rules that are off don't count");
    }

    @Test
    void createsAHeaderRuleOrOpensTheOneThatExists() {
        HeadersTab tab = linked(MESSAGE);
        tab.act(List.of("X-Backend"), "X-Backend", true);
        Link created = links.getLast();
        assertEquals("create", created.kind());
        assertEquals(List.of("X-Backend"), created.rule().headers);
        assertEquals("Redaction created for X-Backend.", created.notice());

        tab.act(List.of("User-Agent"), "User-Agent", true);
        assertEquals("open", links.getLast().kind());
        assertEquals("User-Agent", links.getLast().rule().name);
        assertEquals("This rule redacts User-Agent.", links.getLast().notice());

        tab.act(List.of("User-Agent"), "User-Agent", false);
        assertEquals("create", links.getLast().kind(), "nothing highlights it yet");
        assertTrue(links.getLast().rule() instanceof it.unlocksecurity.burp.screenshotmode.model.HighlightRule);
    }

    @Test
    void offersRuleActionsInTheDetails() {
        HeadersTab tab = linked(MESSAGE);
        tab.select("Client identity");
        press(tab, "expand");
        tab.select("user-agent");
        List<String> texts = texts(tab.details());
        assertTrue(texts.contains("Redacted by “User-Agent”"), texts.toString());
        assertTrue(texts.contains("Edit ›"));
        assertTrue(texts.contains("Highlight…"));
        assertFalse(texts.contains("Redact value…"), "a rule already redacts it");

        tab.select("Caching");
        assertTrue(texts(tab.details()).contains("Redact values…"));
    }

    @Test
    void restoresTheHeadersOfItsBuiltInTemplateAfterAsking() {
        HeadersTab tab = tab(null);
        List<String> asked = new ArrayList<>();
        tab.setRestoreConfirmation(message -> {
            asked.add(message);
            return asked.size() > 1;
        });
        javax.swing.AbstractButton restore = restoreButton(tab);
        assertTrue(texts(tab).contains("Based on Defaults · no changes"));
        assertFalse(restore.isEnabled(), "nothing to restore");

        tab.toggle((HeadersTab.GroupRow) row(tab, "Security policy"));
        assertTrue(texts(tab).contains("Based on Defaults · 1 change"));
        assertTrue(restore.isEnabled());

        restore.doClick();
        assertTrue(settings.headers.isGroupHidden("security"), "declined");
        restore.doClick();
        assertEquals(
            "Show and hide the headers as \"Defaults\" does?\nYour change to the headers will be lost.",
            asked.getLast()
        );
        assertEquals(BuiltInTemplates.defaults().headers.groups, settings.headers.groups);
        assertTrue(texts(tab).contains("Based on Defaults · no changes"));
    }

    @Test
    void countsEveryHiddenGroupAsAChangeFromEmpty() {
        settings.basedOn = null;
        HeadersTab tab = tab(null);
        assertTrue(texts(tab).contains("Based on Empty · 8 changes"), texts(tab).toString());
        tab.setRestoreConfirmation(message -> true);
        restoreButton(tab).doClick();
        assertTrue(settings.headers.groups.isEmpty(), "Empty hides nothing");
    }

    private static javax.swing.AbstractButton restoreButton(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof javax.swing.AbstractButton button && "Restore defaults".equals(button.getText())) {
                return button;
            }
            if (component instanceof Container child) {
                javax.swing.AbstractButton found = restoreButton(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @Test
    void describesTheSelectedHeader() {
        HeadersTab tab = tab(MESSAGE);
        tab.select("Content");
        press(tab, "expand");
        tab.select("Content-Type");
        assertTrue(texts(tab.details()).contains("Request and response header · RFC 9110 §8.3 · Content"));
        tab.select("x-backend");
        assertTrue(texts(tab.details()).contains("Custom header"));
    }

    private static List<String> texts(Container container) {
        List<String> texts = new ArrayList<>();
        for (Component component : container.getComponents()) {
            if (component instanceof JLabel label) {
                texts.add(label.getText());
            } else if (component instanceof javax.swing.AbstractButton button) {
                texts.add(button.getText());
            } else if (component instanceof JTextComponent text) {
                texts.add(text.getText());
            }
            if (component instanceof Container child) {
                texts.addAll(texts(child));
            }
        }
        return texts;
    }
}
