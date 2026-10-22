package it.unlocksecurity.burp.screenshotmode.model;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Which headers a template hides. A group setting covers every header the catalog puts in that group, including ones
 * a later release adds; a header setting overrides its group. Headers missing from both maps are shown.
 *
 * <p>Header settings that only repeat their group's are dropped, so equal choices are stored the same way. Headers the
 * catalog doesn't know are the exception: they are kept even when shown, as the template is the only place listing
 * them.
 *
 * <p>A template based on a built-in one stores only its {@link #changesFrom changes} from the built-in's headers, and
 * is read back {@link #over over} them, so whatever it doesn't change follows the built-in template.
 */
public final class HeaderVisibility {

    /**
     * By group id. Only {@link Visibility#HIDE} is kept: a group is shown when it is missing. Changes from a built-in
     * template also keep {@link Visibility#SHOW}, for a group the built-in one hides.
     */
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

    /** The headers of a template whose changes are these, made on top of {@code parent}'s headers. */
    public HeaderVisibility over(HeaderVisibility parent, HeaderCatalog catalog) {
        HeaderVisibility result = new HeaderVisibility();
        result.groups.putAll(parent.groups);
        groups.forEach((id, visibility) -> {
            if (visibility == Visibility.HIDE) {
                result.groups.put(id, Visibility.HIDE);
            } else {
                result.groups.remove(id);
            }
        });
        result.headers.putAll(parent.headers);
        result.headers.putAll(headers);
        result.headers
            .entrySet()
            .removeIf(
                entry ->
                    catalog.header(entry.getKey()).isPresent() &&
                    entry.getValue() == result.groupVisibility(entry.getKey(), catalog)
            );
        return result;
    }

    /**
     * What these headers change from {@code parent}'s: the fewest settings that give the same headers {@link #over}
     * them. Headers the catalog doesn't know are always kept, as for {@link #set}.
     */
    public HeaderVisibility changesFrom(HeaderVisibility parent, HeaderCatalog catalog) {
        HeaderVisibility changes = new HeaderVisibility();
        Set<String> groupIds = new TreeSet<>(groups.keySet());
        groupIds.addAll(parent.groups.keySet());
        for (String id : groupIds) {
            if (isGroupHidden(id) != parent.isGroupHidden(id)) {
                changes.groups.put(id, isGroupHidden(id) ? Visibility.HIDE : Visibility.SHOW);
            }
        }
        HeaderVisibility inherited = changes.over(parent, catalog);
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(headers.keySet());
        names.addAll(parent.headers.keySet());
        for (String name : names) {
            Visibility own = of(name, catalog);
            boolean listed = catalog.header(name).isEmpty() && headers.containsKey(name);
            if (listed || own != inherited.of(name, catalog)) {
                changes.headers.put(name, own);
            }
        }
        return changes;
    }

    /** How many group and header settings there are. */
    public int size() {
        return groups.size() + headers.size();
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
