package it.unlocksecurity.burp.screenshotmode.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import it.unlocksecurity.burp.screenshotmode.model.Disposition;
import it.unlocksecurity.burp.screenshotmode.model.HeaderCatalog;
import it.unlocksecurity.burp.screenshotmode.model.HeaderVisibility;
import it.unlocksecurity.burp.screenshotmode.model.HeightMode;
import it.unlocksecurity.burp.screenshotmode.model.HighlightMode;
import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.RuleType;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.Template;
import it.unlocksecurity.burp.screenshotmode.model.ThemeMode;
import it.unlocksecurity.burp.screenshotmode.model.UrlMode;
import it.unlocksecurity.burp.screenshotmode.model.ViewMode;
import it.unlocksecurity.burp.screenshotmode.model.Visibility;
import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
import java.util.EnumSet;
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
        assertEquals(EnumSet.of(RuleTarget.RESPONSE), replace.targets);

        RedactionRule opaque = settings.redactions.get(1);
        assertEquals(RedactionMode.OPAQUE, opaque.mode);
        assertEquals(MatchMode.REGEX, opaque.matchMode, "matchMode defaults to regex");
        assertTrue(opaque.matchCase, "rules saved before the match options keep matching case");
        assertFalse(opaque.wholeWord);
    }

    @Test
    void roundTripsThroughEncode() {
        StoredData original = TemplateCodec.decode(v2(V2_SETTINGS)).data();
        original.templates().getFirst().settings.theme = ThemeMode.LIGHT;
        original.templates().getFirst().settings.view = ViewMode.PRETTY;
        original.templates().getFirst().settings.redactions.getFirst().targets = EnumSet.of(
            RuleTarget.REQUEST,
            RuleTarget.URL
        );
        original.templates().getFirst().settings.url = UrlMode.PATH;
        String encoded = TemplateCodec.encode(original);
        StoredData decoded = TemplateCodec.decode(encoded).data();
        assertEquals(encoded, TemplateCodec.encode(decoded));
        assertEquals(ThemeMode.LIGHT, decoded.templates().getFirst().settings.theme);
        assertEquals(ViewMode.PRETTY, decoded.templates().getFirst().settings.view);
        assertEquals(
            EnumSet.of(RuleTarget.REQUEST, RuleTarget.URL),
            decoded.templates().getFirst().settings.redactions.getFirst().targets
        );
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

        Settings full = BuiltInTemplates.defaults();
        // A copy, so only the pixel width differs.
        Settings fullWithStalePixels = full.copy();
        fullWithStalePixels.widthPixels = 1234;
        assertTrue(TemplateCodec.sameSettings(full, fullWithStalePixels), "pixels are not stored in full width");
    }

    @Test
    void readsTheHeightAndFillsThePreviewWithout() {
        assertEquals(HeightMode.FULL, decodeSettings(v3(V3_SETTINGS)).heightMode, "saved before the height existed");

        Settings fitted = decodeSettings(
            v3(
                V3_SETTINGS.replace(
                    "\"width\": {\"mode\": \"full\"},",
                    "\"width\": {\"mode\": \"full\"}, \"height\": {\"mode\": \"content\"},"
                )
            )
        );
        assertEquals(HeightMode.CONTENT, fitted.heightMode);

        Settings pixels = decodeSettings(
            v3(
                V3_SETTINGS.replace(
                    "\"width\": {\"mode\": \"full\"},",
                    "\"width\": {\"mode\": \"full\"}, \"height\": {\"mode\": \"pixel\", \"value\": 640},"
                )
            )
        );
        assertEquals(HeightMode.PIXEL, pixels.heightMode);
        assertEquals(640, pixels.heightPixels);
        String encoded = TemplateCodec.encode(new StoredData(List.of(new Template("t1", "Mine", pixels)), "t1"));
        assertEquals(640, decodeSettings(encoded).heightPixels, "round trip");

        Settings huge = decodeSettings(
            v3(
                V3_SETTINGS.replace(
                    "\"width\": {\"mode\": \"full\"},",
                    "\"width\": {\"mode\": \"full\"}, \"height\": {\"mode\": \"pixel\", \"value\": 999999},"
                )
            )
        );
        assertEquals(Settings.MAX_PIXEL_HEIGHT, huge.heightPixels);

        for (String unreadable : List.of("{\"mode\": \"pixel\"}", "{\"mode\": \"later\"}", "\"tall\"")) {
            Settings settings = decodeSettings(
                v3(
                    V3_SETTINGS.replace(
                        "\"width\": {\"mode\": \"full\"},",
                        "\"width\": {\"mode\": \"full\"}, \"height\": " + unreadable + ","
                    )
                )
            );
            assertEquals(HeightMode.FULL, settings.heightMode, unreadable);
        }
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
    void roundTripsRulesOfVersion3() {
        Settings settings = BuiltInTemplates.defaults();
        HighlightRule header = HighlightRule.forHeaders(List.of("Strict-Transport-Security"));
        header.enabled = false;
        header.mode = HighlightMode.RECTANGLE;
        settings.highlights.add(header);
        RedactionRule pattern = RedactionRule.create(
            "(a)(b)",
            RuleTarget.RESPONSE,
            RedactionMode.OPAQUE,
            MatchMode.REGEX
        );
        pattern.name = "Mine";
        pattern.targets.add(RuleTarget.URL);
        pattern.useCaptureGroups = true;
        pattern.selectedGroups = List.of(2);
        pattern.matchCase = false;
        pattern.wholeWord = true;
        settings.redactions.add(pattern);

        String encoded = TemplateCodec.encode(new StoredData(List.of(Template.create("t", settings)), "x"));
        Settings decoded = decodeSettings(encoded);
        assertTrue(TemplateCodec.sameSettings(settings, decoded));
        HighlightRule readHeader = decoded.highlights.getLast();
        assertEquals(RuleType.HEADER, readHeader.type);
        assertEquals(List.of("Strict-Transport-Security"), readHeader.headers);
        assertFalse(readHeader.enabled);
        RedactionRule readPattern = decoded.redactions.getLast();
        assertEquals("Mine", readPattern.name);
        assertEquals(EnumSet.of(RuleTarget.RESPONSE, RuleTarget.URL), readPattern.targets);
        assertEquals(List.of(2), readPattern.selectedGroups);
        assertTrue(readPattern.useCaptureGroups);
        assertFalse(readPattern.matchCase);
        assertTrue(readPattern.wholeWord);
    }

    private static String v2Redactions(String redactions) {
        return v2(
            """
            {"headersToHide": {"both": [], "request": [], "response": []}, "disposition": "vertical",
             "width": {"mode": "full"}, "highlights": [], "redactions": [%s]}""".formatted(redactions)
        );
    }

    private static final String OLD_JWT = """
    {"id": "%s", "regex": "eyJ[\\\\w-]+\\\\.eyJ[\\\\w-]+\\\\.[\\\\w-]*", "target": "%s",
     "useCaptureGroups": false, "selectedGroups": [], "matchMode": "regex", "mode": "replace",
     "replacementText": "%s"}""";

    @Test
    void namesExactCopiesOfTheOldDefaultsAndMergesTheirTargets() {
        Settings settings = decodeSettings(
            v2Redactions(
                OLD_JWT.formatted("a", "request", "[REDACTED]") +
                    "," +
                    OLD_JWT.formatted("b", "url", "***") +
                    "," +
                    OLD_JWT.formatted("c", "response", "[REDACTED]")
            )
        );
        assertEquals(2, settings.redactions.size());
        RedactionRule jwt = settings.redactions.getFirst();
        assertEquals("JSON Web Tokens", jwt.name);
        assertEquals("a", jwt.id);
        assertEquals(EnumSet.of(RuleTarget.REQUEST, RuleTarget.RESPONSE), jwt.targets, "the URL rule was edited");
        RedactionRule edited = settings.redactions.getLast();
        assertEquals("", edited.name);
        assertEquals("***", edited.replacementText);
        assertEquals(EnumSet.of(RuleTarget.URL), edited.targets);
    }

    @Test
    void migratesTheOldDefaultRulesToTheNamedOnes() throws Exception {
        String json;
        try (var in = TemplateCodecTest.class.getResourceAsStream("default-template-v2.json")) {
            json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        List<RedactionRule> migrated = decodeSettings(json).redactions;
        List<RedactionRule> current = BuiltInTemplates.defaults().redactions;
        assertEquals(
            current
                .stream()
                .map(rule -> rule.name)
                .sorted()
                .toList(),
            migrated
                .stream()
                .map(rule -> rule.name)
                .sorted()
                .toList()
        );
        for (RedactionRule rule : migrated) {
            RedactionRule same = current
                .stream()
                .filter(c -> c.name.equals(rule.name))
                .findFirst()
                .orElseThrow();
            assertEquals(same.type, rule.type, rule.name);
            assertEquals(same.headers, rule.headers, rule.name);
            if (rule.type == RuleType.PATTERN) {
                assertEquals(same.targets, rule.targets, rule.name);
                assertEquals(same.regex, rule.regex, rule.name);
            }
        }
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
    void storesOnlyWhatATemplateChangesFromItsBuiltInParent() {
        Settings settings = BuiltInTemplates.defaults();
        settings.headers.set("Date", Visibility.SHOW, CATALOG);
        Template template = Template.create("Report", settings);
        String json = TemplateCodec.encode(new StoredData(List.of(template), template.id));
        var stored = JsonParser.parseString(json)
            .getAsJsonObject()
            .getAsJsonArray("templates")
            .get(0)
            .getAsJsonObject()
            .getAsJsonObject("settings");
        assertEquals(BuiltInTemplates.DEFAULTS_ID, stored.get("basedOn").getAsString());
        assertEquals(0, stored.getAsJsonObject("headers").getAsJsonObject("groups").size());
        assertEquals("show", stored.getAsJsonObject("headers").getAsJsonObject("headers").get("Date").getAsString());

        Settings read = TemplateCodec.decode(json).data().templates().getFirst().settings;
        assertEquals(BuiltInTemplates.DEFAULTS_ID, read.basedOn);
        assertTrue(TemplateCodec.sameSettings(settings, read));
        assertEquals(settings.headers.groups, read.headers.groups);
        assertEquals(settings.headers.headers, read.headers.headers);
    }

    @Test
    void readsATemplateWithoutChangesAsItsParent() {
        String json =
            "{\"version\": 3, \"defaultTemplateId\": \"\", \"templates\": [{\"id\": \"a\", \"name\": \"A\"," +
            " \"settings\": {\"basedOn\": \"built-in-defaults\", \"headers\": {\"groups\": {}, \"headers\": {}}," +
            " \"disposition\": \"horizontal\", \"width\": {\"mode\": \"full\"}, \"highlights\": []," +
            " \"redactions\": []}}]}";
        Settings read = TemplateCodec.decode(json).data().templates().getFirst().settings;
        assertEquals(BuiltInTemplates.defaults().headers.groups, read.headers.groups);
        assertEquals(Visibility.HIDE, read.headers.of("ETag", CATALOG));

        // A parent this release doesn't ship: the headers are taken as the template's own.
        Settings unknown = TemplateCodec.decode(json.replace("built-in-defaults", "built-in-future"))
            .data()
            .templates()
            .getFirst()
            .settings;
        assertEquals(null, unknown.basedOn);
        assertTrue(unknown.headers.groups.isEmpty());
    }
}
