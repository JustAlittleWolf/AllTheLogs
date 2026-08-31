package me.wolfii.allthelogs.client.ui.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ObfuscatedGlyphsTest {
    @Test
    void scrambleReplacesNonSpaceCharactersOfMatchingWidth() {
        String scrambled = ObfuscatedGlyphs.scramble("--", 19, 0, codePoint -> 6);
        assertEquals(2, scrambled.length());
        assertNotEquals("--", scrambled);
        assertFalse(scrambled.contains(" "));
        for (int i = 0; i < scrambled.length(); i++) {
            assertTrue(ObfuscatedGlyphs.POOL.indexOf(scrambled.charAt(i)) >= 0, scrambled);
        }
    }

    @Test
    void scrambleLeavesSpacesAndFallsBackWhenNoWidthMatchExists() {
        String mixed = ObfuscatedGlyphs.scramble("a b", 0, 1, codePoint -> codePoint == 'a' ? 9 : 1);
        assertEquals('a', mixed.charAt(0));
        assertEquals(' ', mixed.charAt(1));
        assertNotEquals('b', mixed.charAt(2));
        assertEquals("§\n", ObfuscatedGlyphs.scramble("§\n", 0, 3,
            codePoint -> codePoint == '§' ? 4 : 1));
    }

    @Test
    void replacementIsStableForTheSameTickAndIndex() {
        char first = ObfuscatedGlyphs.replacement('-', 12, 19, codePoint -> 6);
        char again = ObfuscatedGlyphs.replacement('-', 12, 19, codePoint -> 6);
        assertEquals(first, again);
        assertNotEquals('-', first);
        assertNotEquals(first, ObfuscatedGlyphs.replacement('-', 13, 19, codePoint -> 6));
    }
}
