package it.unlocksecurity.burp.screenshotmode.render;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Predicate;

public final class HeaderFilter {

    private HeaderFilter() {}

    /**
     * Removes the header lines whose name {@code hidden} accepts. The start line and the body are never touched.
     *
     * @param text message text with {@code \n} line breaks
     */
    public static String filter(String text, Predicate<String> hidden) {
        StringBuilder out = new StringBuilder(text.length());
        boolean inBody = false;
        boolean first = true;
        for (String line : text.split("\n", -1)) {
            boolean keep = true;
            if (!inBody) {
                if (line.isEmpty()) {
                    inBody = true;
                } else {
                    String name = name(line);
                    keep = name == null || !hidden.test(name);
                }
            }
            if (keep) {
                if (!first) {
                    out.append('\n');
                }
                out.append(line);
                first = false;
            }
        }
        return out.toString();
    }

    /** The header names of a message, in order, each once (ignoring case) as first written. */
    public static List<String> names(String text) {
        List<String> names = new ArrayList<>();
        TreeSet<String> seen = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        int start = text.indexOf('\n') + 1; // after the start line
        while (start > 0 && start < text.length()) {
            int end = text.indexOf('\n', start);
            String line = text.substring(start, end < 0 ? text.length() : end);
            if (line.isEmpty()) {
                break;
            }
            String name = name(line);
            if (name != null && seen.add(name)) {
                names.add(name);
            }
            start = end + 1;
        }
        return names;
    }

    private static String name(String line) {
        int colon = line.indexOf(':');
        return colon > 0 ? line.substring(0, colon) : null;
    }
}
