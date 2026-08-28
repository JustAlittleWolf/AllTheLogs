package me.wolfii.allthelogs.data.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class Re2RegexTest {
    @Test
    void acceptsRe2FeaturesUsedInChatSearch() {
        assertNull(Re2Regex.unsupportedConstruct("(?i)foo.*bar"));
        assertNull(Re2Regex.unsupportedConstruct("(?i:hello)"));
        assertNull(Re2Regex.unsupportedConstruct("(?:^|[\\]\\)\\>»\\s])name"));
        assertNull(Re2Regex.unsupportedConstruct("(?P<user>[a-z]+)"));
        assertNull(Re2Regex.unsupportedConstruct("\\bword\\b"));
        assertNull(Re2Regex.unsupportedConstruct("a{2,4}"));
        assertNull(Re2Regex.unsupportedConstruct("[}]+"));
    }

    @Test
    void rejectsLookaroundsBackreferencesAndPossessiveQuantifiers() {
        assertEquals("negative lookahead", Re2Regex.unsupportedConstruct("(?!Offline)"));
        assertEquals("positive lookahead", Re2Regex.unsupportedConstruct("foo(?=bar)"));
        assertEquals("positive lookbehind", Re2Regex.unsupportedConstruct("(?<=abc)d"));
        assertEquals("negative lookbehind", Re2Regex.unsupportedConstruct("(?<!abc)d"));
        assertEquals("atomic group", Re2Regex.unsupportedConstruct("(?>a)"));
        assertEquals("named capturing group", Re2Regex.unsupportedConstruct("(?<name>a)"));
        assertEquals("backreference \\1", Re2Regex.unsupportedConstruct("(a)\\1"));
        assertEquals("possessive quantifier *+", Re2Regex.unsupportedConstruct("a*+"));
        assertEquals("possessive quantifier", Re2Regex.unsupportedConstruct("a{2,}+"));
        String chatName = "(?:^|[\\]\\)\\>»\\s])([a-zA-Z0-9_]{3,16})(?:\\s*»|:)\\s+(?!(?:Offline|Online)\\b)\\S+.*";
        assertEquals("negative lookahead", Re2Regex.unsupportedConstruct(chatName));
    }

    @Test
    void doesNotTreatEscapesInsideCharacterClassesAsBackreferences() {
        assertNull(Re2Regex.unsupportedConstruct("[\\1]"));
        assertNull(Re2Regex.unsupportedConstruct("\\\\1"));
    }
}
