package it.unlocksecurity.burp.screenshotmode.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unlocksecurity.burp.screenshotmode.model.HeaderCatalog;
import it.unlocksecurity.burp.screenshotmode.model.HeaderVisibility;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.Settings;
import it.unlocksecurity.burp.screenshotmode.model.Visibility;
import it.unlocksecurity.burp.screenshotmode.render.RuleEngine;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DefaultTemplateTest {

    @Test
    void isStoredInTheCurrentFormat() {
        // Bump the file together with TemplateCodec.VERSION: a migrated default would lose settings.
        assertFalse(TemplateCodec.decode(DefaultTemplate.json()).migrated());
    }

    @Test
    void isWhatTheCodecWrites() {
        // So no setting is missing from the file and silently left to its fallback.
        assertEquals(
            DefaultTemplate.json().strip(),
            TemplateCodec.encode(TemplateCodec.decode(DefaultTemplate.json()).data())
        );
    }

    @Test
    void givesEachCallerItsOwnCopy() {
        Settings settings = DefaultTemplate.settings();
        settings.redactions.clear();
        settings.headers.groups.clear();
        assertEquals(10, DefaultTemplate.settings().redactions.size());
        assertEquals(8, DefaultTemplate.settings().headers.groups.size());
    }

    @Test
    void hidesNoiseAndKeepsWhatCanBeEvidence() {
        HeaderVisibility headers = DefaultTemplate.settings().headers;
        HeaderCatalog catalog = HeaderCatalogFile.bundled();
        for (String hidden : List.of("Sec-Fetch-Mode", "ETag", "Date", "Accept", "CF-RAY", "Sec-CH-UA")) {
            assertEquals(Visibility.HIDE, headers.of(hidden, catalog), hidden);
        }
        // Security headers, technology and anything that can take part in request smuggling stay visible.
        for (String shown : List.of(
            "Content-Length",
            "Transfer-Encoding",
            "TE",
            "Host",
            "Strict-Transport-Security",
            "Content-Security-Policy",
            "X-Powered-By",
            "Server",
            "Set-Cookie",
            "Location",
            "X-Unknown"
        )) {
            assertEquals(Visibility.SHOW, headers.of(shown, catalog), shown);
        }
    }

    @Test
    void namesOnlyGroupsOfTheCatalog() {
        HeaderCatalog catalog = HeaderCatalogFile.bundled();
        Settings settings = DefaultTemplate.settings();
        settings.headers.groups.keySet().forEach(id -> assertTrue(catalog.group(id).isPresent(), id));
        settings.headers.headers.keySet().forEach(name -> assertTrue(catalog.header(name).isPresent(), name));
    }

    private static final String JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.c2ln";

    private static String redact(String text, RuleTarget target) {
        List<RedactionRule> rules = DefaultTemplate.settings()
            .redactions.stream()
            .filter(rule -> rule.appliesTo(target))
            .toList();
        return RuleEngine.apply(text, List.of(), rules).text();
    }

    @Test
    void namesEveryDefaultRuleOnce() {
        List<String> names = DefaultTemplate.settings()
            .redactions.stream()
            .map(rule -> rule.name)
            .toList();
        assertEquals(names.size(), names.stream().distinct().count(), names.toString());
        assertTrue(names.stream().noneMatch(String::isBlank));
    }

    @Test
    void namesOnlyHeadersOfTheCatalogInHeaderRules() {
        HeaderCatalog catalog = HeaderCatalogFile.bundled();
        for (RedactionRule rule : DefaultTemplate.settings().redactions) {
            rule.headers.forEach(name -> assertTrue(catalog.header(name).isPresent(), name));
        }
    }

    @Test
    void defaultRedactionsAreValidPatterns() {
        for (RedactionRule rule : DefaultTemplate.settings().redactions) {
            assertEquals(Optional.empty(), RuleEngine.validate(rule), rule.regex);
        }
    }

    @Test
    void redactsSecretsAndNoiseInRequests() {
        String request = String.join(
            "\n",
            "POST /login?token=abc&x=1&sig=s HTTP/2",
            "host: example.com",
            "user-agent: Mozilla/5.0 (X11; Linux x86_64)",
            "cookie: session=" + JWT + "; theme=dark",
            "Authorization: Bearer " + JWT,
            "Proxy-Authorization: Basic dXNlcjpwYXNz",
            "X-API-Key: k3y",
            "X-Original-User-Agent: kept",
            "",
            "{\"user\":\"bob\",\"password\": \"hunter2\",\"note\":\"" + JWT + "\"}"
        );
        assertEquals(
            String.join(
                "\n",
                "POST /login?token=[REDACTED]&x=1&sig=[REDACTED] HTTP/2",
                "host: example.com",
                "user-agent: [OMITTED]",
                "cookie: [OMITTED]",
                "Authorization: Bearer [REDACTED]",
                "Proxy-Authorization: Basic [REDACTED]",
                "X-API-Key: [REDACTED]",
                "X-Original-User-Agent: kept",
                "",
                "{\"user\":\"bob\",\"password\": \"[REDACTED]\",\"note\":\"[REDACTED]\"}"
            ),
            redact(request, RuleTarget.REQUEST)
        );
    }

    @Test
    void redactsAuthorizationParametersWhateverTheScheme() {
        assertEquals(
            "GET / HTTP/1.1\nAuthorization: Digest [REDACTED]",
            redact("GET / HTTP/1.1\nAuthorization: Digest username=\"a\", response=\"b\"", RuleTarget.REQUEST)
        );
        assertEquals(
            "GET / HTTP/1.1\nauthorization: [REDACTED]",
            redact("GET / HTTP/1.1\nauthorization: s3cr3t", RuleTarget.REQUEST)
        );
    }

    @Test
    void redactsSecretsInTheUrl() {
        assertEquals(
            "https://example.com:443/reset?access_token=[REDACTED]&id=1",
            redact("https://example.com:443/reset?access_token=abc&id=1", RuleTarget.URL)
        );
        assertEquals(
            "https://example.com:443/s/[REDACTED]/view",
            redact("https://example.com:443/s/" + JWT + "/view", RuleTarget.URL)
        );
    }

    @Test
    void redactsPasswordsInForms() {
        assertEquals(
            "POST / HTTP/1.1\n\nuser=bob&password=[REDACTED]&PWD=[REDACTED]",
            redact("POST / HTTP/1.1\n\nuser=bob&password=x&PWD=y", RuleTarget.REQUEST)
        );
    }

    @Test
    void redactsSecretsInResponsesKeepingCookieAttributes() {
        String response = String.join(
            "\n",
            "HTTP/2 200 OK",
            "set-cookie: sid=abc; Path=/; Secure; HttpOnly",
            "Set-Cookie: theme=dark",
            "",
            "{\"access_token\":\"" + JWT + "\",\"refresh_token\":\"r\",\"expires_in\":3600,\"jwt\":\"" + JWT + "\"}"
        );
        assertEquals(
            String.join(
                "\n",
                "HTTP/2 200 OK",
                "set-cookie: sid=[REDACTED]; Path=/; Secure; HttpOnly",
                "Set-Cookie: theme=[REDACTED]",
                "",
                "{\"access_token\":\"[REDACTED]\",\"refresh_token\":\"[REDACTED]\",\"expires_in\":3600,\"jwt\":\"[REDACTED]\"}"
            ),
            redact(response, RuleTarget.RESPONSE)
        );
    }
}
