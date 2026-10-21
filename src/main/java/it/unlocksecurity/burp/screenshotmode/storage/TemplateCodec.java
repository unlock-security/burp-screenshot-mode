package it.unlocksecurity.burp.screenshotmode.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import it.unlocksecurity.burp.screenshotmode.model.Direction;
import it.unlocksecurity.burp.screenshotmode.model.Disposition;
import it.unlocksecurity.burp.screenshotmode.model.HeaderCatalog;
import it.unlocksecurity.burp.screenshotmode.model.HeaderVisibility;
import it.unlocksecurity.burp.screenshotmode.model.HighlightMode;
import it.unlocksecurity.burp.screenshotmode.model.HighlightRule;
import it.unlocksecurity.burp.screenshotmode.model.Labeled;
import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.Rule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.RuleType;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.Template;
import it.unlocksecurity.burp.screenshotmode.model.ThemeMode;
import it.unlocksecurity.burp.screenshotmode.model.UrlMode;
import it.unlocksecurity.burp.screenshotmode.model.ViewMode;
import it.unlocksecurity.burp.screenshotmode.model.Visibility;
import it.unlocksecurity.burp.screenshotmode.model.WidthMode;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Reads and writes templates as JSON (storage version 3), including the migrations from the older formats (versions 2
 * and 1, and the unversioned single-settings object of version 0).
 */
public final class TemplateCodec {

    public static final int VERSION = 3;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public record Decoded(StoredData data, boolean migrated) {}

    private TemplateCodec() {}

    public static StoredData createDefault() {
        Template template = Template.create("Default", DefaultTemplate.settings());
        return new StoredData(new ArrayList<>(List.of(template)), template.id);
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
        JsonElement version = object.get("version");
        if (
            version != null &&
            version.isJsonPrimitive() &&
            version.getAsJsonPrimitive().isNumber() &&
            version.getAsDouble() > VERSION
        ) {
            throw new IllegalArgumentException(
                "Saved by a newer version of Screenshot Mode (storage version " +
                    version.getAsString() +
                    "); this version reads up to " +
                    VERSION
            );
        }

        List<String> errors = new ArrayList<>();
        for (int v = VERSION; v >= 1; v--) {
            try {
                return new Decoded(parseStoredData(object, v), v < VERSION);
            } catch (IllegalArgumentException e) {
                errors.add("v" + v + ": " + e.getMessage());
            }
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
        headers.add("groups", visibilityMap(settings.headers.groups));
        headers.add("headers", visibilityMap(settings.headers.headers));
        object.add("headers", headers);

        object.addProperty("disposition", settings.disposition.id());

        JsonObject width = new JsonObject();
        width.addProperty("mode", settings.widthMode.id());
        if (settings.widthMode == WidthMode.PIXEL) {
            width.addProperty("value", settings.widthPixels);
        }
        object.add("width", width);

        JsonArray highlights = new JsonArray();
        for (HighlightRule rule : settings.highlights) {
            JsonObject json = encodeRule(rule, null);
            json.addProperty("mode", rule.mode.id());
            json.addProperty("color", rule.color);
            highlights.add(json);
        }
        object.add("highlights", highlights);

        JsonArray redactions = new JsonArray();
        for (RedactionRule rule : settings.redactions) {
            JsonObject json = encodeRule(rule, rule.useCaptureGroups ? rule.selectedGroups : null);
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
        object.addProperty("url", settings.url.id());
        return object;
    }

    /** @param groups the capture groups a pattern redaction is limited to, or {@code null} for the whole match */
    private static JsonObject encodeRule(Rule rule, List<Integer> groups) {
        JsonObject json = new JsonObject();
        json.addProperty("id", rule.id);
        json.addProperty("name", rule.name);
        json.addProperty("enabled", rule.enabled);
        JsonObject match = new JsonObject();
        match.addProperty("type", rule.type.id());
        if (rule.type == RuleType.HEADER) {
            match.add("headers", stringArray(rule.headers));
        } else {
            JsonArray targets = new JsonArray();
            for (RuleTarget target : RuleTarget.values()) {
                if (rule.targets.contains(target)) {
                    targets.add(target.id());
                }
            }
            match.add("targets", targets);
            match.addProperty("regex", rule.regex);
            match.addProperty("matchMode", rule.matchMode.id());
            match.addProperty("matchCase", rule.matchCase);
            match.addProperty("wholeWord", rule.wholeWord);
            if (groups != null) {
                JsonArray array = new JsonArray();
                groups.forEach(array::add);
                match.add("groups", array);
            }
        }
        json.add("match", match);
        return json;
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
     * @param version schema version of the payload: v0 has a plain list of hidden headers, v1 either a list or three
     *                lists split by message, v2 three lists, v3 group and header settings. Versions before 2 reset the
     *                metadata toggles.
     */
    private static Settings parseSettings(JsonObject object, int version) {
        Settings settings = new Settings();
        settings.headers =
            version >= 3
                ? parseHeaders(asObject(object.get("headers"), "headers"))
                : migrateHiddenHeaders(object.get("headersToHide"), version, HeaderCatalogFile.bundled());
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
        if (version < 3) {
            settings.redactions = LegacyDefaults.name(settings.redactions);
        }

        if (version < 2) {
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

        JsonElement url = object.get("url");
        settings.url =
            url != null && isString(url) ? lenientEnum(UrlMode.class, url.getAsString(), UrlMode.FULL) : UrlMode.FULL;
        return settings;
    }

    private static HeaderVisibility parseHeaders(JsonObject object) {
        HeaderVisibility headers = new HeaderVisibility();
        parseVisibilityMap(object.get("groups"), "headers.groups").forEach((group, visibility) -> {
            if (visibility != Visibility.SHOW) {
                headers.groups.put(group, Visibility.HIDE);
            }
        });
        headers.headers.putAll(parseVisibilityMap(object.get("headers"), "headers.headers"));
        return headers;
    }

    /**
     * Converts the lists of hidden headers of versions 0 to 2 (one list for both messages, one for requests, one for
     * responses; a {@code !} prefix forces a header to show in the messages of its list) to group and header settings.
     * A group whose headers were all hidden becomes a hidden group.
     */
    static HeaderVisibility migrateHiddenHeaders(JsonElement element, int version, HeaderCatalog catalog) {
        List<String> both;
        List<String> request = List.of();
        List<String> response = List.of();
        if (version == 0 || (version == 1 && element != null && element.isJsonArray())) {
            both = stringList(element, "headersToHide");
        } else {
            JsonObject object = asObject(element, "headersToHide");
            both = stringList(object.get("both"), "headersToHide.both");
            request = stringList(object.get("request"), "headersToHide.request");
            response = stringList(object.get("response"), "headersToHide.response");
        }

        // Header name, as first written, by lowercase name.
        Map<String, String> names = new LinkedHashMap<>();
        Stream.of(both, request, response)
            .flatMap(List::stream)
            .map(rule -> rule.startsWith("!") ? rule.substring(1) : rule)
            .forEach(name -> names.putIfAbsent(name.toLowerCase(Locale.ROOT), name));

        Map<String, Visibility> migrated = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> name : names.entrySet()) {
            boolean inRequests = hiddenByLists(name.getKey(), both, request);
            boolean inResponses = hiddenByLists(name.getKey(), both, response);
            Direction in = catalog.header(name.getValue()).map(HeaderCatalog.Header::in).orElse(Direction.BOTH);
            // Hidden in the only messages the catalog says it is used in: hidden.
            if (in == Direction.REQUEST && inRequests) {
                inResponses = true;
            } else if (in == Direction.RESPONSE && inResponses) {
                inRequests = true;
            }
            migrated.put(
                name.getValue(),
                inRequests && inResponses
                    ? Visibility.HIDE
                    : inRequests
                      ? Visibility.HIDE_IN_REQUEST
                      : inResponses
                        ? Visibility.HIDE_IN_RESPONSE
                        : Visibility.SHOW
            );
        }

        HeaderVisibility headers = new HeaderVisibility();
        for (HeaderCatalog.Group group : catalog.groups()) {
            List<HeaderCatalog.Header> members = group.headers();
            if (
                !members.isEmpty() &&
                members.stream().allMatch(header -> migrated.get(header.name()) == Visibility.HIDE)
            ) {
                headers.groups.put(group.id(), Visibility.HIDE);
            }
        }
        migrated.forEach((name, visibility) -> headers.set(name, visibility, catalog));
        return headers;
    }

    /** Whether the old lists hid a header in one message: the shared list and that message's, where ! wins. */
    private static boolean hiddenByLists(String lowercaseName, List<String> shared, List<String> own) {
        boolean hidden = false;
        for (String rule : Stream.concat(shared.stream(), own.stream()).toList()) {
            String lower = rule.toLowerCase(Locale.ROOT);
            if (lower.equals("!" + lowercaseName)) {
                return false;
            }
            hidden |= lower.equals(lowercaseName);
        }
        return hidden;
    }

    private static HighlightRule parseHighlight(JsonObject object) {
        HighlightRule rule = new HighlightRule();
        parseRule(object, rule);
        rule.color = string(object, "color");
        rule.mode = enumValue(HighlightMode.class, object, "mode");
        return rule;
    }

    private static RedactionRule parseRedaction(JsonObject object) {
        RedactionRule rule = new RedactionRule();
        List<Integer> groups = parseRule(object, rule);
        if (groups != null) {
            rule.useCaptureGroups = true;
            rule.selectedGroups = groups;
        } else if (!object.has("match")) {
            rule.useCaptureGroups = bool(object, "useCaptureGroups");
            rule.selectedGroups = numberList(object.get("selectedGroups"), "selectedGroups");
        }
        rule.mode = enumValue(RedactionMode.class, object, "mode");
        if (rule.mode == RedactionMode.OPAQUE) {
            rule.color = string(object, "color");
        } else if (rule.mode == RedactionMode.REPLACE) {
            rule.replacementText = string(object, "replacementText");
        }
        return rule;
    }

    /**
     * Reads what all rules have: a {@code match} object, or the {@code regex} and single {@code target} of versions 0
     * to 2. Returns the capture groups of a pattern match, or {@code null} if it has none.
     */
    private static List<Integer> parseRule(JsonObject object, Rule rule) {
        rule.id = string(object, "id");
        rule.name = object.has("name") ? string(object, "name") : "";
        rule.enabled = optionalBoolean(object, "enabled", true);
        if (!object.has("match")) {
            rule.regex = string(object, "regex");
            rule.targets = EnumSet.of(enumValue(RuleTarget.class, object, "target"));
            rule.matchMode = optionalMatchMode(object);
            return null;
        }
        JsonObject match = asObject(object.get("match"), "match");
        rule.type = enumValue(RuleType.class, match, "type");
        if (rule.type == RuleType.HEADER) {
            rule.headers = stringList(match.get("headers"), "match.headers");
            return null;
        }
        rule.targets = EnumSet.noneOf(RuleTarget.class);
        for (String target : stringList(match.get("targets"), "match.targets")) {
            rule.targets.add(Labeled.fromId(RuleTarget.class, target));
        }
        rule.regex = string(match, "regex");
        rule.matchMode = optionalMatchMode(match);
        rule.matchCase = optionalBoolean(match, "matchCase", true);
        rule.wholeWord = optionalBoolean(match, "wholeWord", false);
        return match.has("groups") ? numberList(match.get("groups"), "match.groups") : null;
    }

    private static List<Integer> numberList(JsonElement element, String name) {
        List<Integer> values = new ArrayList<>();
        for (JsonElement value : asArray(element, name)) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException(name + " must contain numbers");
            }
            values.add(value.getAsInt());
        }
        return values;
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
        values.forEach(array::add);
        return array;
    }

    private static Map<String, Visibility> parseVisibilityMap(JsonElement element, String name) {
        Map<String, Visibility> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : asObject(element, name).entrySet()) {
            if (!isString(entry.getValue())) {
                throw new IllegalArgumentException(name + " must contain strings");
            }
            values.put(entry.getKey(), Labeled.fromId(Visibility.class, entry.getValue().getAsString()));
        }
        return values;
    }

    private static JsonObject visibilityMap(Map<String, Visibility> values) {
        JsonObject object = new JsonObject();
        values.forEach((key, visibility) -> object.add(key, new JsonPrimitive(visibility.id())));
        return object;
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
