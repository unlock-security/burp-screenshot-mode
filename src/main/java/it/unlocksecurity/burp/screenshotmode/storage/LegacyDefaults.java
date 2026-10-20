package it.unlocksecurity.burp.screenshotmode.storage;

import it.unlocksecurity.burp.screenshotmode.model.MatchMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionMode;
import it.unlocksecurity.burp.screenshotmode.model.RedactionRule;
import it.unlocksecurity.burp.screenshotmode.model.RuleTarget;
import it.unlocksecurity.burp.screenshotmode.model.RuleType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The default redactions of storage versions 0 to 2, one per pattern and target, and the named rule of the current
 * default template each one became. Templates saved before version 3 get the named rules in place of exact copies of
 * them; a default the user edited stays as it is.
 */
final class LegacyDefaults {

    private record Old(String regex, RuleTarget target, List<Integer> groups, String replacement, String name) {
        boolean matches(RedactionRule rule) {
            return (
                rule.type == RuleType.PATTERN &&
                rule.enabled &&
                rule.name.isEmpty() &&
                rule.regex.equals(regex) &&
                rule.targets.equals(EnumSet.of(target)) &&
                rule.matchMode == MatchMode.REGEX &&
                rule.mode == RedactionMode.REPLACE &&
                rule.replacementText.equals(replacement) &&
                rule.useCaptureGroups == !groups.isEmpty() &&
                rule.selectedGroups.equals(groups)
            );
        }
    }

    private static final List<Old> RULES = List.of(
        new Old("(?<=\\n)(?i:User-Agent): (?<UserAgent>.+)", RuleTarget.REQUEST, List.of(1), "[OMITTED]", "User-Agent"),
        new Old("(?<=\\n)(?i:Cookie): (?<Cookies>.+)", RuleTarget.REQUEST, List.of(1), "[OMITTED]", "Cookie values"),
        new Old(
            "(?<=\\n)(?i:(?:Proxy-)?Authorization): (?:(?<AuthScheme>\\S+) )?(?<AuthParameters>.+)",
            RuleTarget.REQUEST,
            List.of(2),
            "[REDACTED]",
            "Authorization credentials"
        ),
        new Old(
            "(?<=\\n)(?i:X-Api-Key|Api-Key|X-Auth-Token|X-Access-Token|X-Amz-Security-Token): (?<Token>.+)",
            RuleTarget.REQUEST,
            List.of(1),
            "[REDACTED]",
            "API key headers"
        ),
        new Old(
            "[?&](?i:access_token|api_?key|token|signature|sig|X-Amz-Signature|X-Amz-Credential|X-Amz-Security-Token)=(?<Value>[^&\\s#]*)",
            RuleTarget.REQUEST,
            List.of(1),
            "[REDACTED]",
            "Secrets in query string"
        ),
        new Old(
            "\"(?i:password|passwd|pwd|secret|client_secret)\"\\s*:\\s*\"(?<Value>[^\"]*)\"",
            RuleTarget.REQUEST,
            List.of(1),
            "[REDACTED]",
            "Passwords and secrets in JSON requests"
        ),
        new Old(
            "(?<=[&\\n])(?i:password|passwd|pwd|client_secret)=(?<Value>[^&\\n]*)",
            RuleTarget.REQUEST,
            List.of(1),
            "[REDACTED]",
            "Passwords in form data"
        ),
        new Old("eyJ[\\w-]+\\.eyJ[\\w-]+\\.[\\w-]*", RuleTarget.REQUEST, List.of(), "[REDACTED]", "JSON Web Tokens"),
        new Old(
            "(?<=\\n)(?i:Set-Cookie): [^=;\\n]+=(?<Value>[^;\\n]*)",
            RuleTarget.RESPONSE,
            List.of(1),
            "[REDACTED]",
            "Set-Cookie values"
        ),
        new Old(
            "\"(?i:access_token|refresh_token|id_token|token|session_?id|api_?key|secret)\"\\s*:\\s*\"(?<Value>[^\"]*)\"",
            RuleTarget.RESPONSE,
            List.of(1),
            "[REDACTED]",
            "Tokens in JSON responses"
        ),
        new Old("eyJ[\\w-]+\\.eyJ[\\w-]+\\.[\\w-]*", RuleTarget.RESPONSE, List.of(), "[REDACTED]", "JSON Web Tokens"),
        new Old(
            "[?&](?i:access_token|api_?key|token|signature|sig|X-Amz-Signature|X-Amz-Credential|X-Amz-Security-Token)=(?<Value>[^&\\s#]*)",
            RuleTarget.URL,
            List.of(1),
            "[REDACTED]",
            "Secrets in query string"
        ),
        new Old("eyJ[\\w-]+\\.eyJ[\\w-]+\\.[\\w-]*", RuleTarget.URL, List.of(), "[REDACTED]", "JSON Web Tokens")
    );

    private LegacyDefaults() {}

    /**
     * Replaces the old default rules with the named ones, where they were. Rules the old defaults split by target
     * (the JSON Web Tokens and the query string secrets) become one rule for the same targets: the URL is not added
     * where it was not there.
     */
    static List<RedactionRule> name(List<RedactionRule> rules) {
        List<RedactionRule> named = new ArrayList<>();
        Map<String, RedactionRule> byName = new HashMap<>();
        for (RedactionRule rule : rules) {
            Old old = RULES.stream()
                .filter(candidate -> candidate.matches(rule))
                .findFirst()
                .orElse(null);
            if (old == null) {
                named.add(rule);
                continue;
            }
            RedactionRule current = byName.get(old.name());
            if (current == null) {
                current = DefaultTemplate.settings()
                    .redactions.stream()
                    .filter(candidate -> candidate.name.equals(old.name()))
                    .findFirst()
                    .orElseThrow();
                current.id = rule.id;
                if (current.type == RuleType.PATTERN) {
                    current.targets = EnumSet.of(old.target());
                }
                byName.put(old.name(), current);
                named.add(current);
            } else {
                current.targets.add(old.target());
            }
        }
        return named;
    }
}
