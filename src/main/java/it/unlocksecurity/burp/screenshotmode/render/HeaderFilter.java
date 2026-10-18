package it.unlocksecurity.burp.screenshotmode.render;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class HeaderFilter {

    private HeaderFilter() {}

    /**
     * Removes the header lines named by {@code rules} (case-insensitive). A rule prefixed with {@code !} forces
     * the header to be shown even when another rule hides it. The start line and the body are never touched.
     *
     * @param text message text with {@code \n} line breaks
     */
    public static String filter(String text, List<String> rules) {
        if (rules.isEmpty()) {
            return text;
        }

        Set<String> hide = new HashSet<>();
        Set<String> show = new HashSet<>();
        for (String rule : rules) {
            if (rule.startsWith("!")) {
                show.add(rule.substring(1).toLowerCase(Locale.ROOT));
            } else {
                hide.add(rule.toLowerCase(Locale.ROOT));
            }
        }

        StringBuilder out = new StringBuilder(text.length());
        boolean inBody = false;
        boolean first = true;
        for (String line : text.split("\n", -1)) {
            boolean keep = true;
            if (!inBody) {
                if (line.isEmpty()) {
                    inBody = true;
                } else {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        String name = line.substring(0, colon).toLowerCase(Locale.ROOT);
                        keep = show.contains(name) || !hide.contains(name);
                    }
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
}
