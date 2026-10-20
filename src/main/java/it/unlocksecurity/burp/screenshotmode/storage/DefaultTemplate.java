package it.unlocksecurity.burp.screenshotmode.storage;

import it.unlocksecurity.burp.screenshotmode.model.Settings;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * The settings new templates start from, read from {@code default-template.json} next to this class in the jar. The
 * file is an ordinary template export in the current storage format, so {@link TemplateCodec} reads it like any import:
 * update it whenever {@link TemplateCodec#VERSION} changes.
 *
 * <p>What the defaults hide and redact, and why:
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
public final class DefaultTemplate {

    private static final String RESOURCE = "default-template.json";
    private static final String JSON = load();

    private DefaultTemplate() {}

    /** A new copy of the default settings, free to edit. */
    public static Settings settings() {
        return TemplateCodec.decode(JSON).data().templates().getFirst().settings;
    }

    /** The bundled file as is. */
    static String json() {
        return JSON;
    }

    private static String load() {
        try (InputStream in = DefaultTemplate.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is missing from the extension jar");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + RESOURCE, e);
        }
    }
}
