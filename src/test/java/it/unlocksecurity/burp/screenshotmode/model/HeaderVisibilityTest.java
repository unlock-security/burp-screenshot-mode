package it.unlocksecurity.burp.screenshotmode.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HeaderVisibilityTest {

    private static final HeaderCatalog CATALOG = new HeaderCatalog(
        List.of(
            new HeaderCatalog.Group(
                "caching",
                "Caching",
                List.of(
                    new HeaderCatalog.Header("Cache-Control", Direction.BOTH, "RFC 9111 §5.2"),
                    new HeaderCatalog.Header("ETag", Direction.RESPONSE, "RFC 9110 §8.8.3")
                )
            )
        )
    );

    @Test
    void showsWhatNothingHides() {
        HeaderVisibility headers = new HeaderVisibility();
        assertEquals(Visibility.SHOW, headers.of("ETag", CATALOG));
        assertEquals(Visibility.SHOW, headers.of("X-Unknown", CATALOG));
    }

    @Test
    void appliesTheGroupUnlessTheHeaderHasItsOwnSetting() {
        HeaderVisibility headers = new HeaderVisibility();
        headers.setGroup("caching", true, CATALOG);
        assertEquals(Visibility.HIDE, headers.of("etag", CATALOG));
        headers.set("etag", Visibility.SHOW, CATALOG);
        assertEquals(Visibility.SHOW, headers.of("ETag", CATALOG));
        assertEquals(Visibility.HIDE, headers.of("Cache-Control", CATALOG));
        assertEquals(Map.of("ETag", Visibility.SHOW), headers.headers, "stored under the catalog's spelling");
    }

    @Test
    void dropsHeaderSettingsThatRepeatTheirGroup() {
        HeaderVisibility headers = new HeaderVisibility();
        headers.set("ETag", Visibility.HIDE, CATALOG);
        headers.set("ETag", Visibility.SHOW, CATALOG);
        assertTrue(headers.headers.isEmpty());
    }

    @Test
    void keepsCustomHeadersEvenWhenShown() {
        HeaderVisibility headers = new HeaderVisibility();
        headers.set("X-Backend", Visibility.HIDE, CATALOG);
        headers.set("x-backend", Visibility.SHOW, CATALOG);
        assertEquals(Map.of("x-backend", Visibility.SHOW), headers.headers);
    }

    @Test
    void settingAGroupResetsItsHeaders() {
        HeaderVisibility headers = new HeaderVisibility();
        headers.set("ETag", Visibility.HIDE, CATALOG);
        headers.set("Cache-Control", Visibility.HIDE_IN_REQUEST, CATALOG);
        headers.set("X-Backend", Visibility.HIDE, CATALOG);
        headers.setGroup("caching", false, CATALOG);
        assertEquals(Map.of("X-Backend", Visibility.HIDE), headers.headers);
        assertTrue(headers.groups.isEmpty());
    }

    @Test
    void hidesInTheChosenMessagesOnly() {
        HeaderVisibility headers = new HeaderVisibility();
        headers.set("Cache-Control", Visibility.HIDE_IN_RESPONSE, CATALOG);
        assertFalse(headers.hides("Cache-Control", RuleTarget.REQUEST, CATALOG));
        assertTrue(headers.hides("Cache-Control", RuleTarget.RESPONSE, CATALOG));
    }

    @Test
    void copiesAreIndependent() {
        HeaderVisibility headers = new HeaderVisibility();
        HeaderVisibility copy = headers.copy();
        copy.setGroup("caching", true, CATALOG);
        copy.set("X-Backend", Visibility.HIDE, CATALOG);
        assertTrue(headers.groups.isEmpty());
        assertTrue(headers.headers.isEmpty());
        assertEquals(Visibility.HIDE, copy.of("x-BACKEND", CATALOG), "ignoring case in the copy too");
    }
}
