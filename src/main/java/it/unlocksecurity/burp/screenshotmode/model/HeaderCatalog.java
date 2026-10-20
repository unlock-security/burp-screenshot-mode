package it.unlocksecurity.burp.screenshotmode.model;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The HTTP headers Screenshot Mode knows, in groups, read from {@code storage/header-catalog.json} in the jar. It is
 * reference data, not a setting: templates store which groups and headers to hide, never the catalog itself, so a new
 * release can add headers and every template sees them.
 */
public final class HeaderCatalog {

    /** @param spec where the header is defined, such as "RFC 9110 §11.6.2" */
    public record Header(String name, Direction in, String spec) {}

    public record Group(String id, String label, List<Header> headers) {
        public Group {
            headers = List.copyOf(headers);
        }
    }

    private final List<Group> groups;
    private final Map<String, Group> groupsById = new HashMap<>();
    private final Map<String, Header> headersByName = new HashMap<>();
    private final Map<String, Group> groupsByHeader = new HashMap<>();

    /** @throws IllegalArgumentException if a group id or a header name (ignoring case) appears twice */
    public HeaderCatalog(List<Group> groups) {
        this.groups = List.copyOf(groups);
        for (Group group : this.groups) {
            if (groupsById.put(group.id(), group) != null) {
                throw new IllegalArgumentException("Duplicate header group: " + group.id());
            }
            for (Header header : group.headers()) {
                String key = key(header.name());
                if (headersByName.put(key, header) != null) {
                    throw new IllegalArgumentException("Duplicate header: " + header.name());
                }
                groupsByHeader.put(key, group);
            }
        }
    }

    public List<Group> groups() {
        return groups;
    }

    public Optional<Group> group(String id) {
        return Optional.ofNullable(groupsById.get(id));
    }

    /** The catalog entry for a header name, ignoring case (HTTP/2 sends names in lowercase). */
    public Optional<Header> header(String name) {
        return Optional.ofNullable(headersByName.get(key(name)));
    }

    public Optional<Group> groupOf(String name) {
        return Optional.ofNullable(groupsByHeader.get(key(name)));
    }

    public int headerCount() {
        return headersByName.size();
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
