package it.unlocksecurity.burp.screenshotmode.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.Direction;
import it.unlocksecurity.burp.screenshotmode.model.HeaderCatalog;
import org.junit.jupiter.api.Test;

class HeaderCatalogFileTest {

    private static final HeaderCatalog CATALOG = HeaderCatalogFile.bundled();

    @Test
    void readsTheBundledCatalog() {
        assertEquals(17, CATALOG.groups().size());
        assertEquals(149, CATALOG.headerCount());
        assertEquals("Credentials and session", CATALOG.groups().getFirst().label());
    }

    @Test
    void describesEveryHeader() {
        for (HeaderCatalog.Group group : CATALOG.groups()) {
            assertTrue(group.id().matches("[a-z]+(-[a-z]+)*"), group.id());
            assertFalse(group.label().isBlank(), group.id());
            assertFalse(group.headers().isEmpty(), group.id());
            for (HeaderCatalog.Header header : group.headers()) {
                // A token, as RFC 9110 §5.1 defines field names.
                assertTrue(header.name().matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+"), header.name());
                assertFalse(header.spec().isBlank(), header.name());
            }
        }
    }

    @Test
    void findsHeadersIgnoringCase() {
        assertEquals("Authorization", CATALOG.header("authorization").orElseThrow().name());
        assertEquals(Direction.RESPONSE, CATALOG.header("SET-COOKIE").orElseThrow().in());
        assertEquals("framing", CATALOG.groupOf("transfer-encoding").orElseThrow().id());
        assertTrue(CATALOG.header("X-Backend").isEmpty());
    }

    @Test
    void rejectsDuplicatesAndOtherFormats() {
        String header = "{\"name\": \"A\", \"in\": \"both\", \"spec\": \"s\"}";
        String duplicate =
            "{\"format\": \"screenshot-mode/header-catalog\", \"version\": 1, \"groups\": [" +
            "{\"id\": \"x\", \"label\": \"X\", \"headers\": [" +
            header +
            "]}," +
            "{\"id\": \"y\", \"label\": \"Y\", \"headers\": [" +
            header.replace("\"A\"", "\"a\"") +
            "]}]}";
        assertThrows(IllegalArgumentException.class, () -> HeaderCatalogFile.parse(duplicate));
        assertThrows(IllegalArgumentException.class, () ->
            HeaderCatalogFile.parse(duplicate.replace("\"version\": 1", "\"version\": 2"))
        );
        assertThrows(IllegalArgumentException.class, () ->
            HeaderCatalogFile.parse("{\"version\": 3, \"templates\": []}")
        );
    }
}
