package me.wolfii.allthelogs.client.search;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class RegexFlagsTest {
    @Test
    void sanitizeKeepsUniqueValidLettersAndRejectsTheRest() {
        assertEquals("ims", RegexFlags.sanitize("ims"));
        assertEquals("im", RegexFlags.sanitize("imm"));
        assertEquals("is", RegexFlags.sanitize("i!s g"));
        assertEquals("", RegexFlags.sanitize("ggg"));
        assertTrue(RegexFlags.isLegal("imsU"));
        assertFalse(RegexFlags.isLegal("ii"));
        assertFalse(RegexFlags.isLegal("g"));
        assertTrue(RegexFlags.isValidLetter('U'));
        assertFalse(RegexFlags.isValidLetter('g'));
    }

    @Test
    void ignoreCaseFlagTracksTheILetter() {
        assertTrue(RegexFlags.ignoreCase("i"));
        assertTrue(RegexFlags.ignoreCase("mi"));
        assertFalse(RegexFlags.ignoreCase("ms"));
        assertEquals("ms", RegexFlags.withIgnoreCase("msi", false));
        assertEquals("msi", RegexFlags.withIgnoreCase("ms", true));
        assertEquals("i", RegexFlags.withIgnoreCase("", true));
        assertEquals("", RegexFlags.withIgnoreCase("i", false));
    }

    @Test
    void patternBitsMapJavaLetters() {
        assertEquals(Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE, RegexFlags.toPatternFlags("i"));
        assertEquals(Pattern.MULTILINE | Pattern.DOTALL, RegexFlags.toPatternFlags("ms"));
        assertEquals("imsU", RegexFlags.re2Inline("Usimx"));
        assertEquals("ms", RegexFlags.re2Inline("msx"));
        assertEquals("", RegexFlags.re2Inline("dx"));
    }
}
