package it.unlocksecurity.burp.screenshotmode.model;

import java.util.Map;
import java.util.TreeMap;

/**
 * Which headers a template hides. A group setting covers every header the catalog puts in that group, including ones
 * a later release adds; a header setting overrides its group. Headers missing from both maps are shown.
 *
 * <p>Header settings that only repeat their group's are dropped, so equal choices are stored the same way. Headers the
 * catalog doesn't know are the exception: they are kept even when shown, as the template is the only place listing
 * them.
 */
public final class HeaderVisibility {

    /** By group id. Only {@link Visibility#HIDE} is stored: a group is shown when it is missing. */
    public Map<String, Visibility> groups = new TreeMap<>();
    /** By header name, ignoring case. */
    public Map<String, Visibility> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

    /** What applies to a header: its own setting, else its group's, else shown. */
    public Visibility of(String name, HeaderCatalog catalog) {
        Visibility own = headers.get(name);
        return own != null ? own : groupVisibility(name, catalog);
    }

    public boolean hides(String name, RuleTarget side, HeaderCatalog catalog) {
        return of(name, catalog).hidesIn(side);
    }

    public boolean isGroupHidden(String groupId) {
        return groups.get(groupId) == Visibility.HIDE;
    }

    /** Sets one header, under the catalog's spelling of its name when the catalog knows it. */
    public void set(String name, Visibility visibility, HeaderCatalog catalog) {
        headers.remove(name);
        if (catalog.header(name).isEmpty()) {
            headers.put(name, visibility);
        } else if (visibility != groupVisibility(name, catalog)) {
            headers.put(catalog.header(name).get().name(), visibility);
        }
    }

    /** Sets a group and every header in it, so they all follow the group again. */
    public void setGroup(String groupId, boolean hidden, HeaderCatalog catalog) {
        if (hidden) {
            groups.put(groupId, Visibility.HIDE);
        } else {
            groups.remove(groupId);
        }
        catalog.group(groupId).ifPresent(group -> group.headers().forEach(header -> headers.remove(header.name())));
    }

    public HeaderVisibility copy() {
        HeaderVisibility copy = new HeaderVisibility();
        copy.groups.putAll(groups);
        copy.headers.putAll(headers);
        return copy;
    }

    private Visibility groupVisibility(String name, HeaderCatalog catalog) {
        return catalog
            .groupOf(name)
            .filter(group -> isGroupHidden(group.id()))
            .map(group -> Visibility.HIDE)
            .orElse(Visibility.SHOW);
    }
}
