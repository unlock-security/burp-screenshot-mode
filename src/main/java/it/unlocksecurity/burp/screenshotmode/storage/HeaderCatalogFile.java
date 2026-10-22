package it.unlocksecurity.burp.screenshotmode.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unlocksecurity.burp.screenshotmode.model.Direction;
import it.unlocksecurity.burp.screenshotmode.model.HeaderCatalog;
import it.unlocksecurity.burp.screenshotmode.model.Labeled;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the {@link HeaderCatalog} from {@code header-catalog.json} next to this class in the jar, once, at the first
 * use. The file is checked by the build ({@code HeaderCatalogFileTest}), so reading it can only fail on a broken jar.
 */
public final class HeaderCatalogFile {

    static final String FORMAT = "screenshot-mode/header-catalog";
    static final int VERSION = 1;
    private static final String RESOURCE = "header-catalog.json";
    private static final HeaderCatalog CATALOG = parse(json());

    private HeaderCatalogFile() {}

    public static HeaderCatalog bundled() {
        return CATALOG;
    }

    /** @throws IllegalArgumentException if the file is not a valid catalog */
    static HeaderCatalog parse(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!FORMAT.equals(root.get("format").getAsString()) || root.get("version").getAsInt() != VERSION) {
                throw new IllegalArgumentException("Not a version " + VERSION + " header catalog");
            }
            List<HeaderCatalog.Group> groups = new ArrayList<>();
            for (JsonElement groupElement : root.getAsJsonArray("groups")) {
                JsonObject group = groupElement.getAsJsonObject();
                List<HeaderCatalog.Header> headers = new ArrayList<>();
                for (JsonElement headerElement : group.getAsJsonArray("headers")) {
                    JsonObject header = headerElement.getAsJsonObject();
                    headers.add(
                        new HeaderCatalog.Header(
                            header.get("name").getAsString(),
                            Labeled.fromId(Direction.class, header.get("in").getAsString()),
                            header.get("spec").getAsString()
                        )
                    );
                }
                groups.add(
                    new HeaderCatalog.Group(group.get("id").getAsString(), group.get("label").getAsString(), headers)
                );
            }
            return new HeaderCatalog(groups);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid header catalog: " + e.getMessage(), e);
        }
    }

    static String json() {
        try (InputStream in = HeaderCatalogFile.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is missing from the extension jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
    }
}
