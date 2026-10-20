package it.unlocksecurity.burp.screenshotmode.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.Disposition;
import it.unlocksecurity.burp.screenshotmode.model.HighlightMode;
import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.Template;
import it.unlocksecurity.burp.screenshotmode.model.ThemeMode;
import it.unlocksecurity.burp.screenshotmode.model.ViewMode;
import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
import java.util.List;
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

    @Test
    void decodesV2AsIs() {
        TemplateCodec.Decoded decoded = TemplateCodec.decode(v2(V2_SETTINGS));
        assertFalse(decoded.migrated());
        assertEquals("t1", decoded.data().defaultTemplateId());
        Template template = decoded.data().templates().getFirst();
        assertEquals("Mine", template.name);

        Settings settings = template.settings;
        assertEquals(List.of("Accept"), settings.headersToHide.both);
        assertEquals(List.of("Cookie"), settings.headersToHide.request);
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
        String encoded = TemplateCodec.encode(original);
        StoredData decoded = TemplateCodec.decode(encoded).data();
        assertEquals(encoded, TemplateCodec.encode(decoded));
        assertEquals(ThemeMode.LIGHT, decoded.templates().getFirst().settings.theme);
        assertEquals(ViewMode.PRETTY, decoded.templates().getFirst().settings.view);
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
        assertEquals(List.of("Accept", "Cookie"), settings.headersToHide.both);
        assertTrue(settings.headersToHide.request.isEmpty());
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
        assertEquals(List.of("B"), settings.headersToHide.request);
        assertEquals(List.of("C"), settings.headersToHide.response);
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
        assertEquals(List.of("Accept"), template.settings.headersToHide.both);
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
        assertThrows(IllegalArgumentException.class, () -> TemplateCodec.decode("[]"));
        assertThrows(IllegalArgumentException.class, () -> TemplateCodec.decode("not json"));
    }

    @Test
    void fallsBackToDefaultsForUnknownInput() {
        for (String input : new String[] { null, "", "{}", "{\"foo\": 1}", "garbage" }) {
            TemplateCodec.Decoded decoded = TemplateCodec.decodeOrDefault(input);
            assertTrue(decoded.migrated());
            assertEquals(1, decoded.data().templates().size());
            Template template = decoded.data().templates().getFirst();
            assertEquals("Default", template.name);
            assertEquals(template.id, decoded.data().defaultTemplateId());
            assertTrue(template.settings.headersToHide.request.contains("Sec-Fetch-Mode"));
        }
    }

    @Test
    void generatesUniqueTemplateIds() {
        assertNotEquals(
            TemplateCodec.createDefault().defaultTemplateId(),
            TemplateCodec.createDefault().defaultTemplateId()
        );
    }
}
