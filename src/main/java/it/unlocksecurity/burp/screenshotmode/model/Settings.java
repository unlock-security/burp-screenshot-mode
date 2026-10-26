package it.unlocksecurity.burp.screenshotmode.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Screenshot settings. New templates start from a copy of a built-in template ({@code storage.BuiltInTemplates}). */
public final class Settings {

    public static final int DEFAULT_PIXEL_WIDTH = 800;
    public static final int MIN_PIXEL_WIDTH = 200;
    public static final int MAX_PIXEL_WIDTH = 2000;
    public static final int DEFAULT_PIXEL_HEIGHT = 600;
    public static final int MIN_PIXEL_HEIGHT = 50;
    public static final int MAX_PIXEL_HEIGHT = 20_000;
    /** The image pixels per pixel on screen that can be chosen. */
    public static final List<Integer> SCALES = List.of(1, 2, 3);
    public static final int DEFAULT_SCALE = 2;

    /**
     * The id of the built-in template these settings are based on: the headers that {@link #headers} doesn't set
     * itself follow that template, also when a release changes it. {@code null} is Empty, which hides nothing.
     */
    public String basedOn;
    /** The headers shown and hidden, the built-in template's included. */
    public HeaderVisibility headers = new HeaderVisibility();
    public MessageMode messages = MessageMode.BOTH;
    /** Only meaningful when {@link #messages} shows both. */
    public Disposition disposition = Disposition.HORIZONTAL;
    public WidthMode widthMode = WidthMode.FULL;
    /** Only meaningful when {@link #widthMode} is {@link WidthMode#PIXEL}. */
    public int widthPixels = DEFAULT_PIXEL_WIDTH;
    public HeightMode heightMode = HeightMode.FULL;
    /** Only meaningful when {@link #heightMode} is {@link HeightMode#PIXEL}. */
    public int heightPixels = DEFAULT_PIXEL_HEIGHT;
    /** Image pixels per pixel on screen: one of {@link #SCALES}. */
    public int scale = DEFAULT_SCALE;
    public List<HighlightRule> highlights = new ArrayList<>();
    public List<RedactionRule> redactions = new ArrayList<>();
    public boolean showTimestamp = false;
    public boolean showResponseInfo = true;
    /** The capture palette: Swing has no CSS theme to inherit, so it is selectable. */
    public ThemeMode theme = ThemeMode.AUTO;
    /** Raw or Pretty, like the tabs of Burp's message editor. */
    public ViewMode view = ViewMode.RAW;
    public UrlMode url = UrlMode.FULL;
    /** Numbers beside the lines of the messages. */
    public boolean lineNumbers = true;

    /** The fixed width of the captured content in pixels, or {@code null} when it follows the preview or the content. */
    public Integer maxContentWidth() {
        return switch (widthMode) {
            case FULL, CONTENT -> null;
            case PIXEL -> widthPixels;
            case A4, LETTER -> widthMode.presetWidth();
        };
    }

    /**
     * The rules of {@code parent} that {@code own} doesn't have, matched by name: a built-in rule edited but still
     * under its name counts as there. Built-in rules all have a name. Returns {@code parent}'s instances, with a new id
     * where {@code own} already uses theirs.
     */
    public static <R extends Rule> List<R> missing(List<R> own, List<R> parent) {
        Set<String> names = own
            .stream()
            .map(rule -> rule.name)
            .collect(Collectors.toSet());
        Set<String> ids = own
            .stream()
            .map(rule -> rule.id)
            .collect(Collectors.toSet());
        List<R> missing = new ArrayList<>();
        for (R rule : parent) {
            if (!rule.name.isBlank() && !names.contains(rule.name)) {
                if (ids.contains(rule.id)) {
                    rule.id = UUID.randomUUID().toString();
                }
                missing.add(rule);
            }
        }
        return missing;
    }

    /** How many settings of the Appearance tab differ from {@code other}'s. */
    public int appearanceChanges(Settings other) {
        return (int) List.of(
            messages != other.messages,
            disposition != other.disposition,
            !Objects.equals(maxContentWidth(), other.maxContentWidth()) || widthMode != other.widthMode,
            heightMode != other.heightMode || (heightMode == HeightMode.PIXEL && heightPixels != other.heightPixels),
            scale != other.scale,
            theme != other.theme,
            url != other.url,
            lineNumbers != other.lineNumbers,
            view != other.view,
            showTimestamp != other.showTimestamp,
            showResponseInfo != other.showResponseInfo
        )
            .stream()
            .filter(changed -> changed)
            .count();
    }

    /** Takes the settings of the Appearance tab from {@code other}. */
    public void copyAppearance(Settings other) {
        messages = other.messages;
        disposition = other.disposition;
        widthMode = other.widthMode;
        widthPixels = other.widthPixels;
        heightMode = other.heightMode;
        heightPixels = other.heightPixels;
        scale = other.scale;
        theme = other.theme;
        url = other.url;
        lineNumbers = other.lineNumbers;
        view = other.view;
        showTimestamp = other.showTimestamp;
        showResponseInfo = other.showResponseInfo;
    }

    public Settings copy() {
        Settings copy = new Settings();
        copy.copyFrom(this);
        return copy;
    }

    /**
     * Takes copies of all of {@code other}'s settings. The rule lists stay the same objects, refilled, since editors
     * hold them.
     */
    public void copyFrom(Settings other) {
        List<HighlightRule> otherHighlights = other.highlights.stream().map(HighlightRule::copy).toList();
        List<RedactionRule> otherRedactions = other.redactions.stream().map(RedactionRule::copy).toList();
        highlights.clear();
        highlights.addAll(otherHighlights);
        redactions.clear();
        redactions.addAll(otherRedactions);
        basedOn = other.basedOn;
        headers = other.headers.copy();
        copyAppearance(other);
    }
}
