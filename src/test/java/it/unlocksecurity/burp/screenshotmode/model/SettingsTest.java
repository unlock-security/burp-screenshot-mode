package it.unlocksecurity.burp.screenshotmode.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettingsTest {

    private static RedactionRule named(String name, String regex) {
        RedactionRule rule = RedactionRule.create(regex, RuleTarget.REQUEST, RedactionMode.REPLACE, MatchMode.REGEX);
        rule.name = name;
        return rule;
    }

    @Test
    void findsTheParentRulesMissingByName() {
        RedactionRule cookies = named("Cookies", "c");
        RedactionRule tokens = named("Tokens", "t");
        List<RedactionRule> parent = List.of(cookies, tokens);

        RedactionRule edited = named("Cookies", "edited");
        edited.id = cookies.id;
        List<RedactionRule> own = new ArrayList<>(List.of(edited, named("", "mine")));
        assertEquals(List.of(tokens), Settings.missing(own, parent), "an edited rule still counts");

        // Renamed: its id stays taken, so the rule added back gets a new one.
        edited.name = "My cookies";
        String id = cookies.id;
        List<RedactionRule> missing = Settings.missing(own, parent);
        assertEquals(List.of(cookies, tokens), missing);
        assertSame(cookies, missing.getFirst());
        assertNotEquals(id, cookies.id);
    }

    @Test
    void countsAndCopiesTheAppearance() {
        Settings parent = new Settings();
        Settings settings = new Settings();
        assertEquals(0, settings.appearanceChanges(parent));

        settings.disposition = Disposition.VERTICAL;
        settings.widthMode = WidthMode.PIXEL;
        settings.widthPixels = 900;
        settings.theme = ThemeMode.DARK;
        settings.showTimestamp = true;
        settings.heightMode = HeightMode.PIXEL;
        settings.heightPixels = 640;
        settings.messages = MessageMode.REQUEST;
        settings.lineNumbers = false;
        settings.scale = 3;
        settings.redactions.add(named("Kept", "k"));
        assertEquals(8, settings.appearanceChanges(parent));

        settings.copyAppearance(parent);
        assertEquals(0, settings.appearanceChanges(parent));
        assertEquals(1, settings.redactions.size(), "rules are not appearance");
        assertEquals(MessageMode.BOTH, settings.messages);
        assertTrue(settings.lineNumbers);
        assertEquals(Settings.DEFAULT_SCALE, settings.scale);
    }

    @Test
    void copiesEverythingIntoTheSameRuleLists() {
        Settings other = new Settings();
        other.basedOn = "defaults";
        other.disposition = Disposition.VERTICAL;
        RedactionRule rule = named("Tokens", "t");
        other.redactions.add(rule);

        Settings settings = new Settings();
        List<RedactionRule> redactions = settings.redactions;
        settings.redactions.add(named("Gone", "g"));
        settings.copyFrom(other);

        assertSame(redactions, settings.redactions, "editors hold the list");
        assertEquals(1, settings.redactions.size());
        assertEquals(rule.id, settings.redactions.getFirst().id);
        assertNotSame(rule, settings.redactions.getFirst());
        assertNotSame(other.headers, settings.headers);
        assertEquals("defaults", settings.basedOn);
        assertEquals(Disposition.VERTICAL, settings.disposition);
        assertEquals(0, settings.appearanceChanges(other));
    }
}
