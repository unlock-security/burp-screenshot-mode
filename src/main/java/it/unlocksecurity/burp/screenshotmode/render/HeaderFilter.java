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

    /** A header line of a message: its name, and where the line starts and ends. */
    public record Line(String name, int from, int to) {}

    /** The header lines of a message, in order. */
    public static List<Line> lines(String text) {
        List<Line> lines = new ArrayList<>();
        int start = text.indexOf('\n') + 1; // after the start line
        while (start > 0 && start < text.length()) {
            int end = text.indexOf('\n', start);
            if (end < 0) {
                end = text.length();
            }
            if (end == start) {
                break; // the end of the headers
            }
            String name = name(text.substring(start, end));
            if (name != null) {
                lines.add(new Line(name, start, end));
            }
            start = end + 1;
        }
        return lines;
    }

    /** The header names of a message, in order, each once (ignoring case) as first written. */
    public static List<String> names(String text) {
        List<String> names = new ArrayList<>();
        TreeSet<String> seen = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Line line : lines(text)) {
            if (seen.add(line.name())) {
                names.add(line.name());
            }
        }
        return names;
    }

    private static String name(String line) {
        int colon = line.indexOf(':');
        return colon > 0 ? line.substring(0, colon) : null;
    }
}
