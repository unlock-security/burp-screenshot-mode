package it.unlocksecurity.burp.screenshotmode.storage;

import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.Template;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The templates bundled with the extension, read from {@code built-in-templates.json} next to this class in the jar:
 * Defaults (our hidden headers and redactions) and Empty (nothing hidden, no rules). They are never stored, so every
 * start reads them as this release ships them, and they can't be edited or deleted: users duplicate them instead. The
 * file is an ordinary template export in the current storage format, so {@link TemplateCodec} reads it like any import:
 * update it whenever {@link TemplateCodec#VERSION} changes.
 *
 * <p>What Defaults hides and redacts, and why:
 * <ul>
 *   <li>Hidden headers are split by the messages each is defined for; vendor headers added by servers and CDNs are only
 *       hidden in responses. Headers that can take part in request smuggling or other desync attacks stay visible, as
 *       they can be the evidence: Content-Length, Transfer-Encoding, TE, Trailer, Connection, Keep-Alive, Upgrade,
 *       Expect, Host, HTTP2-Settings and Proxy-Connection.</li>
 *   <li>Values that are only noise in a report (User-Agent, Cookie) become {@code [OMITTED]}, secrets
 *       {@code [REDACTED]}. The Authorization scheme and the Set-Cookie name and attributes stay visible: they are often
 *       the finding.</li>
 *   <li>Header patterns are anchored with {@code (?<=\n)} (without the MULTILINE flag, {@code ^} only matches the start
 *       of the message) and match names with {@code (?i:...)}, as Burp shows HTTP/2 headers in lowercase. No repeated
 *       alternation like {@code (?:[^"\\]|\\.)*}, which overflows the stack on long values.</li>
 * </ul>
 */
public final class BuiltInTemplates {

    public static final String DEFAULTS_ID = "built-in-defaults";
    public static final String EMPTY_ID = "built-in-empty";

    private static final String RESOURCE = "built-in-templates.json";
    private static final String JSON = load();

    private BuiltInTemplates() {}

    /** New copies of the built-in templates, Defaults first. */
    public static List<Template> all() {
        return TemplateCodec.decode(JSON)
            .data()
            .templates()
            .stream()
            .map(template -> new Template(template.id, template.name, template.settings, true))
            .toList();
    }

    /** A new copy of the Defaults settings, free to edit. */
    public static Settings defaults() {
        return all().getFirst().settings;
    }

    /** The bundled file as is. */
    static String json() {
        return JSON;
    }

    private static String load() {
        try (InputStream in = BuiltInTemplates.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is missing from the extension jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
    }
}
