package it.unlocksecurity.burp.screenshotmode.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.HeaderCatalog;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.Visibility;
import it.unlocksecurity.burp.screenshotmode.storage.DefaultTemplate;
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

    private final Settings settings = DefaultTemplate.settings();
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
