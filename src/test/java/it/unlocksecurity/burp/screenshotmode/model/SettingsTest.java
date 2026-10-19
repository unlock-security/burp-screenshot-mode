package it.unlocksecurity.burp.screenshotmode.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class SettingsTest {

    @Test
    void hidesDefaultHeadersOnlyInTheMessagesTheyBelongTo() {
        HiddenHeaders headers = Settings.defaults().headersToHide;
        assertTrue(headers.rulesFor(RuleTarget.REQUEST).contains("Sec-Fetch-Mode"));
        assertFalse(headers.rulesFor(RuleTarget.RESPONSE).contains("Sec-Fetch-Mode"));
        assertTrue(headers.rulesFor(RuleTarget.RESPONSE).contains("ETag"));
        assertFalse(headers.rulesFor(RuleTarget.REQUEST).contains("ETag"));
        assertTrue(headers.rulesFor(RuleTarget.REQUEST).contains("Date"));
        assertTrue(headers.rulesFor(RuleTarget.RESPONSE).contains("Date"));
    }

    @Test
    void listsEachDefaultHeaderOnce() {
        HiddenHeaders headers = Settings.defaults().headersToHide;
        List<String> all = new ArrayList<>(headers.both);
        all.addAll(headers.request);
        all.addAll(headers.response);
        assertEquals(
            all.size(),
            new HashSet<>(
                all
                    .stream()
                    .map(h -> h.toLowerCase(Locale.ROOT))
                    .toList()
            ).size()
        );
    }
}
