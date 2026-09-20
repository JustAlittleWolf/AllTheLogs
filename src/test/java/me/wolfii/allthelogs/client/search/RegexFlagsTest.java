package me.wolfii.allthelogs.client.search;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class RegexFlagsTest {
    @Test
    void sanitizeKeepsUniqueDuckDbLettersAndRejectsTheRest() {
        assertEquals("ims", RegexFlags.sanitize("ims"));
        assertEquals("im", RegexFlags.sanitize("imm"));
        assertEquals("is", RegexFlags.sanitize("i!s g"));
        assertEquals("i", RegexFlags.sanitize("ixUdu"));
        assertEquals("", RegexFlags.sanitize("ggg"));
        assertTrue(RegexFlags.isLegal("ims"));
        assertFalse(RegexFlags.isLegal("ii"));
        assertFalse(RegexFlags.isLegal("g"));
        assertFalse(RegexFlags.isLegal("imsU"));
        assertTrue(RegexFlags.isValidLetter('i'));
        assertTrue(RegexFlags.isValidLetter('m'));
        assertTrue(RegexFlags.isValidLetter('s'));
        assertFalse(RegexFlags.isValidLetter('U'));
        assertFalse(RegexFlags.isValidLetter('d'));
        assertFalse(RegexFlags.isValidLetter('u'));
        assertFalse(RegexFlags.isValidLetter('x'));
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
    void duckDbInlineOmitsJavaOnlyLetters() {
        assertEquals("ims", RegexFlags.re2Inline("smi"));
        assertEquals("ims", RegexFlags.re2Inline("Usimx"));
        assertEquals("ms", RegexFlags.re2Inline("msx"));
        assertEquals("", RegexFlags.re2Inline("dxU"));
        assertEquals(Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE, RegexFlags.highlightBits("i"));
        assertEquals(0, RegexFlags.highlightBits("ms"));
    }
}
