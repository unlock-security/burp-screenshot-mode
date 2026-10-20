package it.unlocksecurity.burp.screenshotmode.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import it.unlocksecurity.burp.screenshotmode.model.Disposition;
import it.unlocksecurity.burp.screenshotmode.model.HeaderCatalog;
import it.unlocksecurity.burp.screenshotmode.model.HeaderVisibility;
import it.unlocksecurity.burp.screenshotmode.model.HighlightMode;
import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.Template;
import it.unlocksecurity.burp.screenshotmode.model.ThemeMode;
import it.unlocksecurity.burp.screenshotmode.model.UrlMode;
import it.unlocksecurity.burp.screenshotmode.model.ViewMode;
import it.unlocksecurity.burp.screenshotmode.model.Visibility;
import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Port of the Caido plugin's schema and migration specs. */
class TemplateCodecTest {

    private static final String V2_SETTINGS = """
    {
      "headersToHide": {"both": ["Accept"], "request": ["Cookie"], "response": []},
      "disposition": "vertical",
      "width": {"mode": "pixel", "value": 900},
      "highlights": [
        {"id": "h1", "regex": "token", "target": "request", "color": "#ff0000", "mode": "rectangle", "matchMode": "string"}
      ],
      "redactions": [
        {"id": "r1", "regex": "(a)(b)", "target": "response", "useCaptureGroups": true, "selectedGroups": [2],
         "matchMode": "regex", "mode": "replace", "replacementText": "***"},
        {"id": "r2", "regex": "x", "target": "request", "useCaptureGroups": false, "selectedGroups": [],
         "mode": "opaque", "color": "#123456"}
      ],
      "showTimestamp": true,
      "showResponseInfo": false
    }""";

    private static String v2(String settings) {
        return (
            "{\"version\": 2, \"templates\": [{\"id\": \"t1\", \"name\": \"Mine\", \"settings\": " +
            settings +
            "}], \"defaultTemplateId\": \"t1\"}"
        );
    }

    private static Settings decodeSettings(String json) {
        return TemplateCodec.decode(json).data().templates().getFirst().settings;
    }

    private static final HeaderCatalog CATALOG = HeaderCatalogFile.bundled();

    private static final String V3_SETTINGS = """
    {
      "headers": {
        "groups": {"caching": "hide", "fetch": "show"},
        "headers": {"ETag": "show", "Content-Length": "hide-in-response", "X-Backend": "hide"}
      },
      "disposition": "horizontal",
      "width": {"mode": "full"},
      "highlights": [],
      "redactions": [],
      "showTimestamp": false,
      "showResponseInfo": true
    }""";

    private static String v3(String settings) {
        return v2(settings).replace("\"version\": 2", "\"version\": 3");
    }

    @Test
    void decodesV3AsIs() {
        TemplateCodec.Decoded decoded = TemplateCodec.decode(v3(V3_SETTINGS));
        assertFalse(decoded.migrated());
        HeaderVisibility headers = decoded.data().templates().getFirst().settings.headers;
        assertEquals(Map.of("caching", Visibility.HIDE), headers.groups, "shown groups are not stored");
        assertEquals(Visibility.HIDE, headers.of("Age", CATALOG), "from its group");
        assertEquals(Visibility.SHOW, headers.of("etag", CATALOG), "its own setting wins");
        assertEquals(Visibility.HIDE_IN_RESPONSE, headers.of("Content-Length", CATALOG));
        assertEquals(Visibility.HIDE, headers.of("x-backend", CATALOG));
        assertEquals(Visibility.SHOW, headers.of("Sec-Fetch-Mode", CATALOG));
    }

    @Test
    void roundTripsHeaderSettings() {
        StoredData data = TemplateCodec.decode(v3(V3_SETTINGS)).data();
        String encoded = TemplateCodec.encode(data);
        assertEquals(encoded, TemplateCodec.encode(TemplateCodec.decode(encoded).data()));
        assertTrue(encoded.contains("\"Content-Length\": \"hide-in-response\""), encoded);
    }

    @Test
    void rejectsUnknownHeaderSettings() {
        assertThrows(IllegalArgumentException.class, () ->
            TemplateCodec.decode(v3(V3_SETTINGS.replace("\"X-Backend\": \"hide\"", "\"X-Backend\": \"blur\"")))
        );
        assertThrows(IllegalArgumentException.class, () ->
            TemplateCodec.decode(
                v3(V3_SETTINGS.replace("\"groups\": {\"caching\": \"hide\", \"fetch\": \"show\"}", "\"groups\": []"))
            )
        );
    }

    @Test
    void migratesV2() {
        TemplateCodec.Decoded decoded = TemplateCodec.decode(v2(V2_SETTINGS));
        assertTrue(decoded.migrated());
        assertEquals("t1", decoded.data().defaultTemplateId());
        Template template = decoded.data().templates().getFirst();
        assertEquals("Mine", template.name);

        Settings settings = template.settings;
        assertEquals(Visibility.HIDE, settings.headers.of("Accept", CATALOG));
        assertEquals(Visibility.HIDE, settings.headers.of("Cookie", CATALOG), "only used in requests");
        assertEquals(Disposition.VERTICAL, settings.disposition);
        assertEquals(WidthMode.PIXEL, settings.widthMode);
        assertEquals(900, settings.widthPixels);
        assertTrue(settings.showTimestamp);
        assertFalse(settings.showResponseInfo);
        assertEquals(ThemeMode.AUTO, settings.theme);
        assertEquals(ViewMode.RAW, settings.view, "payloads without a view are raw");

        HighlightRule highlight = settings.highlights.getFirst();
        assertEquals(HighlightMode.RECTANGLE, highlight.mode);
        assertEquals(MatchMode.STRING, highlight.matchMode);

        RedactionRule replace = settings.redactions.get(0);
        assertEquals(RedactionMode.REPLACE, replace.mode);
        assertEquals("***", replace.replacementText);
        assertEquals(List.of(2), replace.selectedGroups);
        assertEquals(RuleTarget.RESPONSE, replace.target);

        RedactionRule opaque = settings.redactions.get(1);
        assertEquals(RedactionMode.OPAQUE, opaque.mode);
        assertEquals("#123456", opaque.color);
        assertEquals(MatchMode.REGEX, opaque.matchMode, "matchMode defaults to regex");
    }

    @Test
    void roundTripsThroughEncode() {
        StoredData original = TemplateCodec.decode(v2(V2_SETTINGS)).data();
        original.templates().getFirst().settings.theme = ThemeMode.LIGHT;
        original.templates().getFirst().settings.view = ViewMode.PRETTY;
        original.templates().getFirst().settings.redactions.getFirst().target = RuleTarget.URL;
        original.templates().getFirst().settings.url = UrlMode.PATH;
        String encoded = TemplateCodec.encode(original);
        StoredData decoded = TemplateCodec.decode(encoded).data();
        assertEquals(encoded, TemplateCodec.encode(decoded));
        assertEquals(ThemeMode.LIGHT, decoded.templates().getFirst().settings.theme);
        assertEquals(ViewMode.PRETTY, decoded.templates().getFirst().settings.view);
        assertEquals(RuleTarget.URL, decoded.templates().getFirst().settings.redactions.getFirst().target);
        assertEquals(UrlMode.PATH, decoded.templates().getFirst().settings.url);
        assertFalse(encoded.contains("\"color\": \"#000000\""), "mode-specific fields only for their mode");
    }

    @Test
    void comparesSettingsByWhatWouldBeStored() {
        Settings settings = decodeSettings(v2(V2_SETTINGS));
        Settings copy = settings.copy();
        assertTrue(TemplateCodec.sameSettings(settings, copy));

        copy.redactions.getFirst().replacementText = "###";
        assertFalse(TemplateCodec.sameSettings(settings, copy), "a rule edit counts");

        Settings full = DefaultTemplate.settings();
        // A copy, so only the pixel width differs.
        Settings fullWithStalePixels = full.copy();
        fullWithStalePixels.widthPixels = 1234;
        assertTrue(TemplateCodec.sameSettings(full, fullWithStalePixels), "pixels are not stored in full width");
    }

    @Test
    void metadataTogglesDefaultWhenMissingInV2() {
        Settings settings = decodeSettings(
            v2(
                V2_SETTINGS.replace("\"showTimestamp\": true,", "").replace(
                    "\"showResponseInfo\": false",
                    "\"theme\": \"dark\""
                )
            )
        );
        assertFalse(settings.showTimestamp);
        assertTrue(settings.showResponseInfo);
        assertEquals(ThemeMode.DARK, settings.theme);
        assertEquals(UrlMode.FULL, settings.url, "templates saved before the URL bar option show the full URL");
    }

    @Test
    void migratesV1FlatHeaders() {
        String v1 = """
        {"version": 1, "defaultTemplateId": "a", "templates": [{"id": "a", "name": "Old", "settings": {
          "headersToHide": ["Accept", "Cookie"], "disposition": "horizontal", "width": {"mode": "a4"},
          "highlights": [], "redactions": [], "showTimestamp": true}}]}""";
        TemplateCodec.Decoded decoded = TemplateCodec.decode(v1);
        assertTrue(decoded.migrated());
        Settings settings = decoded.data().templates().getFirst().settings;
        assertEquals(Map.of("Accept", Visibility.HIDE, "Cookie", Visibility.HIDE), settings.headers.headers);
        assertEquals(WidthMode.A4, settings.widthMode);
        assertFalse(settings.showTimestamp, "fromV1 resets metadata toggles");
        assertTrue(settings.showResponseInfo);
        assertEquals("a", decoded.data().defaultTemplateId());
    }

    @Test
    void preservesAlreadySplitHeadersInV1() {
        String v1 = """
        {"version": 1, "defaultTemplateId": "a", "templates": [{"id": "a", "name": "Old", "settings": {
          "headersToHide": {"both": ["A"], "request": ["B"], "response": ["C"]}, "disposition": "horizontal",
          "width": {"mode": "full"}, "highlights": [], "redactions": []}}]}""";
        Settings settings = decodeSettings(v1);
        assertEquals(Visibility.HIDE, settings.headers.of("A", CATALOG));
        assertEquals(Visibility.HIDE_IN_REQUEST, settings.headers.of("B", CATALOG));
        assertEquals(Visibility.HIDE_IN_RESPONSE, settings.headers.of("C", CATALOG));
    }

    @Test
    void wrapsV0RawSettingsInADefaultTemplate() {
        String v0 = """
        {"headersToHide": ["Accept"], "disposition": "vertical", "width": {"mode": "letter"},
         "highlights": [], "redactions": []}""";
        TemplateCodec.Decoded decoded = TemplateCodec.decode(v0);
        assertTrue(decoded.migrated());
        Template template = decoded.data().templates().getFirst();
        assertEquals("Default", template.name);
        assertEquals(template.id, decoded.data().defaultTemplateId());
        assertEquals(Visibility.HIDE, template.settings.headers.of("Accept", CATALOG));
        assertEquals(WidthMode.LETTER, template.settings.widthMode);
    }

    @Test
    void v0RejectsSplitHeaders() {
        String v0 = """
        {"headersToHide": {"both": [], "request": [], "response": []}, "disposition": "vertical",
         "width": {"mode": "full"}, "highlights": [], "redactions": []}""";
        assertThrows(IllegalArgumentException.class, () -> TemplateCodec.decode(v0));
    }

    @Test
    void rejectsSchemaViolations() {
        assertThrows(
            IllegalArgumentException.class,
            () ->
                TemplateCodec.decode(
                    v2(V2_SETTINGS.replace("\"mode\": \"pixel\", \"value\": 900", "\"mode\": \"pixel\""))
                ),
            "pixel mode without value"
        );
        assertThrows(IllegalArgumentException.class, () ->
            TemplateCodec.decode(v2(V2_SETTINGS.replace("\"mode\": \"pixel\"", "\"mode\": \"unknown\"")))
        );
        assertThrows(IllegalArgumentException.class, () ->
            TemplateCodec.decode(
                v2(V2_SETTINGS.replace("\"target\": \"request\", \"color\"", "\"target\": \"body\", \"color\""))
            )
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> TemplateCodec.decode(v2(V2_SETTINGS.replace(", \"color\": \"#123456\"", ""))),
            "opaque without color"
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> TemplateCodec.decode(v2(V2_SETTINGS.replace(", \"replacementText\": \"***\"", ""))),
            "replace without text"
        );
        assertThrows(IllegalArgumentException.class, () -> TemplateCodec.decode("{\"version\": 3, \"templates\": []}"));
        assertThrows(IllegalArgumentException.class, () -> TemplateCodec.decode(v3(V2_SETTINGS)), "v3 without headers");
        assertThrows(IllegalArgumentException.class, () -> TemplateCodec.decode("[]"));
        assertThrows(IllegalArgumentException.class, () -> TemplateCodec.decode("not json"));
    }

    @Test
    void rejectsTemplatesFromANewerVersionSayingSo() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
            TemplateCodec.decode("{\"version\": 4, \"templates\": [], \"defaultTemplateId\": \"x\"}")
        );
        assertTrue(error.getMessage().startsWith("Saved by a newer version of Screenshot Mode"), error.getMessage());
    }

    private static HeaderVisibility migrate(String headersToHide) {
        return TemplateCodec.migrateHiddenHeaders(JsonParser.parseString(headersToHide), 2, CATALOG);
    }

    @Test
    void migratesAGroupWhoseHeadersWereAllHiddenToAHiddenGroup() {
        HeaderVisibility headers = migrate(
            "{\"both\": [], \"request\": [\"DNT\", \"sec-gpc\", \"Sec-Browsing-Topics\"], \"response\": []}"
        );
        assertEquals(Map.of("privacy", Visibility.HIDE), headers.groups);
        assertTrue(headers.headers.isEmpty());
    }

    @Test
    void migratesHeadersByTheMessagesTheyWereHiddenIn() {
        HeaderVisibility headers = migrate(
            "{\"both\": [\"date\"], \"request\": [\"Content-Type\"], \"response\": [\"Set-Cookie\", \"Content-Length\"]}"
        );
        assertEquals(Visibility.HIDE, headers.of("Date", CATALOG));
        assertEquals(Visibility.HIDE_IN_REQUEST, headers.of("Content-Type", CATALOG), "used in both messages");
        assertEquals(Visibility.HIDE, headers.of("Set-Cookie", CATALOG), "only used in responses");
        assertEquals(Visibility.HIDE_IN_RESPONSE, headers.of("Content-Length", CATALOG));
        assertEquals(
            List.of("Content-Length", "Content-Type", "Date", "Set-Cookie"),
            List.copyOf(headers.headers.keySet()),
            "catalog spelling"
        );
    }

    @Test
    void migratesForcedHeadersToShownInTheirMessages() {
        HeaderVisibility headers = migrate(
            "{\"both\": [\"Accept\", \"Via\", \"X-Shown\"], \"request\": [\"!Via\"], \"response\": [\"!accept\", \"!X-Shown\"]}"
        );
        assertEquals(Visibility.HIDE_IN_RESPONSE, headers.of("Via", CATALOG));
        assertEquals(Visibility.HIDE_IN_REQUEST, headers.of("Accept", CATALOG));
        assertEquals(Visibility.HIDE_IN_REQUEST, headers.of("X-Shown", CATALOG));
        assertEquals(
            Visibility.SHOW,
            migrate("{\"both\": [\"!X-Mine\"], \"request\": [], \"response\": []}").headers.get("X-Mine")
        );
    }

    @Test
    void migratesTheOldDefaultTemplate() throws Exception {
        String json;
        try (var in = TemplateCodecTest.class.getResourceAsStream("default-template-v2.json")) {
            json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        HeaderVisibility headers = decodeSettings(json).headers;
        assertEquals(
            Set.of(
                "negotiation",
                "caching",
                "client-hints",
                "fetch",
                "privacy",
                "reporting",
                "infrastructure",
                "general"
            ),
            headers.groups.keySet()
        );
        // What the old default hid outside those groups stays hidden, header by header.
        assertEquals(Visibility.HIDE, headers.of("X-Powered-By", CATALOG));
        assertEquals(Visibility.HIDE, headers.of("Strict-Transport-Security", CATALOG));
        assertEquals(Visibility.SHOW, headers.of("Content-Security-Policy", CATALOG));
        assertEquals(Visibility.SHOW, headers.of("Server", CATALOG));
    }

    @Test
    void generatesUniqueTemplateIds() {
        assertNotEquals(
            TemplateCodec.createDefault().defaultTemplateId(),
            TemplateCodec.createDefault().defaultTemplateId()
        );
    }
}
