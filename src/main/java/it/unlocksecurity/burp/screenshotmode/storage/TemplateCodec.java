package it.unlocksecurity.burp.screenshotmode.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import it.unlocksecurity.burp.screenshotmode.model.Disposition;
import it.unlocksecurity.burp.screenshotmode.model.HiddenHeaders;
import it.unlocksecurity.burp.screenshotmode.model.HighlightMode;
import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.Labeled;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.Template;
import it.unlocksecurity.burp.screenshotmode.model.ThemeMode;
import it.unlocksecurity.burp.screenshotmode.model.ViewMode;
import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Reads and writes templates using the same JSON layout as the Caido plugin (storage version 2), including the
 * migrations from its legacy formats (version 1, and the unversioned single-settings object of version 0).
 */
public final class TemplateCodec {

    public static final int VERSION = 2;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public record Decoded(StoredData data, boolean migrated) {}

    private TemplateCodec() {}

    public static StoredData createDefault() {
        Template template = Template.create("Default", Settings.defaults());
        return new StoredData(new ArrayList<>(List.of(template)), template.id);
    }

    /** Port of {@code migrateStorage}: never fails, falling back to a fresh default template. */
    public static Decoded decodeOrDefault(String json) {
        try {
            return decode(json);
        } catch (IllegalArgumentException e) {
            return new Decoded(createDefault(), true);
        }
    }

    /**
     * Decodes any supported version.
     *
     * @throws IllegalArgumentException if the input matches none of the supported formats.
     */
    public static Decoded decode(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Empty input");
        }

        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
        }
        JsonObject object = asObject(root, "root");

        List<String> errors = new ArrayList<>();
        try {
            return new Decoded(parseStoredData(object, 2), false);
        } catch (IllegalArgumentException e) {
            errors.add("v2: " + e.getMessage());
        }
        try {
            return new Decoded(parseStoredData(object, 1), true);
        } catch (IllegalArgumentException e) {
            errors.add("v1: " + e.getMessage());
        }
        try {
            Template template = Template.create("Default", parseSettings(object, 0));
            return new Decoded(new StoredData(new ArrayList<>(List.of(template)), template.id), true);
        } catch (IllegalArgumentException e) {
            errors.add("v0: " + e.getMessage());
        }
        throw new IllegalArgumentException("Unsupported template format (" + String.join("; ", errors) + ")");
    }

    public static String encode(StoredData data) {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonArray templates = new JsonArray();
        for (Template template : data.templates()) {
            templates.add(encodeTemplate(template));
        }
        root.add("templates", templates);
        root.addProperty("defaultTemplateId", data.defaultTemplateId());
        return GSON.toJson(root);
    }

    /**
     * Whether two settings would be stored identically, i.e. whether saving one over the other changes anything.
     * Comparing the stored form ignores values the format drops (such as the pixel width outside pixel mode).
     */
    public static boolean sameSettings(Settings a, Settings b) {
        return encodeSettings(a).equals(encodeSettings(b));
    }

    private static JsonObject encodeTemplate(Template template) {
        JsonObject object = new JsonObject();
        object.addProperty("id", template.id);
        object.addProperty("name", template.name);
        object.add("settings", encodeSettings(template.settings));
        return object;
    }

    private static JsonObject encodeSettings(Settings settings) {
        JsonObject object = new JsonObject();

        JsonObject headers = new JsonObject();
        headers.add("both", stringArray(settings.headersToHide.both));
        headers.add("request", stringArray(settings.headersToHide.request));
        headers.add("response", stringArray(settings.headersToHide.response));
        object.add("headersToHide", headers);

        object.addProperty("disposition", settings.disposition.id());

        JsonObject width = new JsonObject();
        width.addProperty("mode", settings.widthMode.id());
        if (settings.widthMode == WidthMode.PIXEL) {
            width.addProperty("value", settings.widthPixels);
        }
        object.add("width", width);

        JsonArray highlights = new JsonArray();
        for (HighlightRule rule : settings.highlights) {
            JsonObject json = new JsonObject();
            json.addProperty("id", rule.id);
            json.addProperty("regex", rule.regex);
            json.addProperty("target", rule.target.id());
            json.addProperty("color", rule.color);
            json.addProperty("mode", rule.mode.id());
            json.addProperty("matchMode", rule.matchMode.id());
            highlights.add(json);
        }
        object.add("highlights", highlights);

        JsonArray redactions = new JsonArray();
        for (RedactionRule rule : settings.redactions) {
            JsonObject json = new JsonObject();
            json.addProperty("id", rule.id);
            json.addProperty("regex", rule.regex);
            json.addProperty("target", rule.target.id());
            json.addProperty("useCaptureGroups", rule.useCaptureGroups);
            JsonArray groups = new JsonArray();
            rule.selectedGroups.forEach(groups::add);
            json.add("selectedGroups", groups);
            json.addProperty("matchMode", rule.matchMode.id());
            json.addProperty("mode", rule.mode.id());
            if (rule.mode == RedactionMode.OPAQUE) {
                json.addProperty("color", rule.color);
            } else if (rule.mode == RedactionMode.REPLACE) {
                json.addProperty("replacementText", rule.replacementText);
            }
            redactions.add(json);
        }
        object.add("redactions", redactions);

        object.addProperty("showTimestamp", settings.showTimestamp);
        object.addProperty("showResponseInfo", settings.showResponseInfo);
        object.addProperty("theme", settings.theme.id());
        object.addProperty("view", settings.view.id());
        return object;
    }

    private static StoredData parseStoredData(JsonObject object, int version) {
        JsonElement versionElement = object.get("version");
        if (
            versionElement == null ||
            !versionElement.isJsonPrimitive() ||
            !versionElement.getAsJsonPrimitive().isNumber() ||
            versionElement.getAsDouble() != version
        ) {
            throw new IllegalArgumentException("version must be " + version);
        }
        List<Template> templates = new ArrayList<>();
        for (JsonElement element : asArray(object.get("templates"), "templates")) {
            JsonObject template = asObject(element, "template");
            templates.add(
                new Template(
                    string(template, "id"),
                    string(template, "name"),
                    parseSettings(asObject(template.get("settings"), "settings"), version)
                )
            );
        }
        return new StoredData(templates, string(object, "defaultTemplateId"));
    }

    /**
     * @param version schema version of the payload: v0 has a plain header array, v1 either form, v2 split
     *                headers. Legacy versions reset the metadata toggles, like the Caido {@code fromV1} migration.
     */
    private static Settings parseSettings(JsonObject object, int version) {
        Settings settings = new Settings();
        settings.headersToHide = parseHiddenHeaders(object.get("headersToHide"), version);
        settings.disposition = enumValue(Disposition.class, object, "disposition");

        JsonObject width = asObject(object.get("width"), "width");
        settings.widthMode = enumValue(WidthMode.class, width, "mode");
        if (settings.widthMode == WidthMode.PIXEL) {
            settings.widthPixels = (int) Math.round(number(width, "value"));
        }

        for (JsonElement element : asArray(object.get("highlights"), "highlights")) {
            settings.highlights.add(parseHighlight(asObject(element, "highlight")));
        }
        for (JsonElement element : asArray(object.get("redactions"), "redactions")) {
            settings.redactions.add(parseRedaction(asObject(element, "redaction")));
        }

        if (version < VERSION) {
            settings.showTimestamp = false;
            settings.showResponseInfo = true;
        } else {
            settings.showTimestamp = optionalBoolean(object, "showTimestamp", false);
            settings.showResponseInfo = optionalBoolean(object, "showResponseInfo", true);
        }

        JsonElement theme = object.get("theme");
        settings.theme =
            theme != null && isString(theme)
                ? lenientEnum(ThemeMode.class, theme.getAsString(), ThemeMode.AUTO)
                : ThemeMode.AUTO;

        JsonElement view = object.get("view");
        settings.view =
            view != null && isString(view)
                ? lenientEnum(ViewMode.class, view.getAsString(), ViewMode.RAW)
                : ViewMode.RAW;
        return settings;
    }

    private static HiddenHeaders parseHiddenHeaders(JsonElement element, int version) {
        HiddenHeaders headers = new HiddenHeaders();
        if (version == 0 || (version == 1 && element != null && element.isJsonArray())) {
            headers.both = stringList(element, "headersToHide");
            return headers;
        }
        JsonObject object = asObject(element, "headersToHide");
        headers.both = stringList(object.get("both"), "headersToHide.both");
        headers.request = stringList(object.get("request"), "headersToHide.request");
        headers.response = stringList(object.get("response"), "headersToHide.response");
        return headers;
    }

    private static HighlightRule parseHighlight(JsonObject object) {
        HighlightRule rule = new HighlightRule();
        rule.id = string(object, "id");
        rule.regex = string(object, "regex");
        rule.target = enumValue(RuleTarget.class, object, "target");
        rule.color = string(object, "color");
        rule.mode = enumValue(HighlightMode.class, object, "mode");
        rule.matchMode = optionalMatchMode(object);
        return rule;
    }

    private static RedactionRule parseRedaction(JsonObject object) {
        RedactionRule rule = new RedactionRule();
        rule.id = string(object, "id");
        rule.regex = string(object, "regex");
        rule.target = enumValue(RuleTarget.class, object, "target");
        rule.useCaptureGroups = bool(object, "useCaptureGroups");
        rule.selectedGroups = new ArrayList<>();
        for (JsonElement group : asArray(object.get("selectedGroups"), "selectedGroups")) {
            if (!group.isJsonPrimitive() || !group.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("selectedGroups must contain numbers");
            }
            rule.selectedGroups.add(group.getAsInt());
        }
        rule.matchMode = optionalMatchMode(object);
        rule.mode = enumValue(RedactionMode.class, object, "mode");
        if (rule.mode == RedactionMode.OPAQUE) {
            rule.color = string(object, "color");
        } else if (rule.mode == RedactionMode.REPLACE) {
            rule.replacementText = string(object, "replacementText");
        }
        return rule;
    }

    private static MatchMode optionalMatchMode(JsonObject object) {
        return object.has("matchMode") ? enumValue(MatchMode.class, object, "matchMode") : MatchMode.REGEX;
    }

    // --- JSON helpers: each throws IllegalArgumentException describing the first schema violation ---

    private static JsonObject asObject(JsonElement element, String name) {
        if (element == null || !element.isJsonObject()) {
            throw new IllegalArgumentException(name + " must be an object");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray asArray(JsonElement element, String name) {
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException(name + " must be an array");
        }
        return element.getAsJsonArray();
    }

    private static boolean isString(JsonElement element) {
        return element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
    }

    private static String string(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || !isString(element)) {
            throw new IllegalArgumentException(name + " must be a string");
        }
        return element.getAsString();
    }

    private static double number(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(name + " must be a number");
        }
        return element.getAsDouble();
    }

    private static boolean bool(JsonObject object, String name) {
        JsonElement element = object.get(name);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException(name + " must be a boolean");
        }
        return element.getAsBoolean();
    }

    private static boolean optionalBoolean(JsonObject object, String name, boolean fallback) {
        return object.has(name) ? bool(object, name) : fallback;
    }

    private static List<String> stringList(JsonElement element, String name) {
        List<String> values = new ArrayList<>();
        for (JsonElement item : asArray(element, name)) {
            if (!isString(item)) {
                throw new IllegalArgumentException(name + " must contain strings");
            }
            values.add(item.getAsString());
        }
        return values;
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(value -> array.add(new JsonPrimitive(value)));
        return array;
    }

    private static <E extends Enum<E> & Labeled> E enumValue(Class<E> type, JsonObject object, String name) {
        return Labeled.fromId(type, string(object, name));
    }

    private static <E extends Enum<E> & Labeled> E lenientEnum(Class<E> type, String id, E fallback) {
        try {
            return Labeled.fromId(type, id);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Gives imported templates fresh identities so they never clash with existing ones. */
    static Template withNewId(Template template) {
        return new Template(UUID.randomUUID().toString(), template.name, template.settings);
    }
}
