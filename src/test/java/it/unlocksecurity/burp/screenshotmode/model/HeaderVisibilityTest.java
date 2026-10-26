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
    void countsTheHiddenHeadersOfTheCatalogAndTheCustomOnes() {
        HeaderVisibility headers = new HeaderVisibility();
        assertEquals(0, headers.hiddenCount(CATALOG));
        assertEquals(2, headers.knownCount(CATALOG));
        headers.setGroup("caching", true, CATALOG);
        headers.set("X-Backend", Visibility.HIDE, CATALOG);
        headers.set("X-Shown", Visibility.SHOW, CATALOG);
        assertEquals(3, headers.hiddenCount(CATALOG), "both of the group and X-Backend");
        assertEquals(4, headers.knownCount(CATALOG), "a custom header counts even when shown");
        assertEquals(List.of("X-Backend", "X-Shown"), headers.customHeaders(CATALOG));
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

    private static final HeaderCatalog TWO_GROUPS = new HeaderCatalog(
        List.of(
            new HeaderCatalog.Group(
                "caching",
                "Caching",
                List.of(
                    new HeaderCatalog.Header("Cache-Control", Direction.BOTH, "RFC 9111 §5.2"),
                    new HeaderCatalog.Header("ETag", Direction.RESPONSE, "RFC 9110 §8.8.3")
                )
            ),
            new HeaderCatalog.Group(
                "fetch",
                "Fetch",
                List.of(
                    new HeaderCatalog.Header("Sec-Fetch-Mode", Direction.REQUEST, "Fetch"),
                    new HeaderCatalog.Header("Sec-Fetch-Site", Direction.REQUEST, "Fetch")
                )
            )
        )
    );

    private static HeaderVisibility parent() {
        HeaderVisibility parent = new HeaderVisibility();
        parent.setGroup("caching", true, TWO_GROUPS);
        parent.set("Sec-Fetch-Site", Visibility.HIDE, TWO_GROUPS);
        return parent;
    }

    @Test
    void storesOnlyTheChangesFromTheParent() {
        HeaderVisibility parent = parent();
        HeaderVisibility edited = parent.copy();
        assertEquals(0, edited.changesFrom(parent, TWO_GROUPS).size(), "no changes yet");

        edited.setGroup("caching", false, TWO_GROUPS);
        edited.set("Sec-Fetch-Site", Visibility.SHOW, TWO_GROUPS);
        edited.set("X-Backend", Visibility.HIDE_IN_RESPONSE, TWO_GROUPS);
        edited.set("X-Shown", Visibility.SHOW, TWO_GROUPS);
        HeaderVisibility changes = edited.changesFrom(parent, TWO_GROUPS);
        assertEquals(Map.of("caching", Visibility.SHOW), changes.groups);
        assertEquals(
            Map.of(
                "Sec-Fetch-Site",
                Visibility.SHOW,
                "X-Backend",
                Visibility.HIDE_IN_RESPONSE,
                "X-Shown",
                Visibility.SHOW
            ),
            changes.headers
        );

        HeaderVisibility back = changes.over(parent, TWO_GROUPS);
        assertEquals(edited.groups, back.groups);
        assertEquals(edited.headers, back.headers);
    }

    @Test
    void followsTheParentWhereItChangesNothing() {
        HeaderVisibility edited = parent();
        edited.set("ETag", Visibility.SHOW, TWO_GROUPS);
        HeaderVisibility changes = edited.changesFrom(parent(), TWO_GROUPS);

        // A later release shows Caching, hides Fetch and no longer hides Sec-Fetch-Site on its own.
        HeaderVisibility updated = new HeaderVisibility();
        updated.setGroup("fetch", true, TWO_GROUPS);
        HeaderVisibility headers = changes.over(updated, TWO_GROUPS);
        assertEquals(Visibility.SHOW, headers.of("Cache-Control", TWO_GROUPS));
        assertEquals(Visibility.SHOW, headers.of("ETag", TWO_GROUPS), "the template's own choice");
        assertEquals(Visibility.HIDE, headers.of("Sec-Fetch-Mode", TWO_GROUPS));
        assertEquals(Visibility.HIDE, headers.of("Sec-Fetch-Site", TWO_GROUPS));
        assertTrue(headers.headers.isEmpty(), "ETag now repeats its group: " + headers.headers);
    }
}
