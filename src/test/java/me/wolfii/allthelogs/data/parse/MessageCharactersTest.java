package me.wolfii.allthelogs.data.parse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCharactersTest {
    @AfterEach
    void restorePrivateUseFilter() {
        MessageCharacters.setDropPrivateUseCharacters(false);
    }

    @Test
    void alwaysDropsObjectReplacementCharacters() {
        MessageCharacters.setDropPrivateUseCharacters(false);
        FormattingCodes.Parsed parsed = MessageCharacters.filter(FormattingCodes.Parsed.plain("\uFFFCHi\uFFFC"));
        assertEquals("Hi", parsed.text());
        assertNull(parsed.formatting());
    }

    @Test
    void dropsPrivateUseCharactersWhenEnabled() {
        String supplementary = new String(Character.toChars(0xF0000)) + new String(Character.toChars(0x10FFFD));
        FormattingCodes.Parsed parsed = MessageCharacters.filter(
            FormattingCodes.Parsed.plain("A\uE000" + supplementary + "\uF8FFB"), true);
        assertEquals("AB", parsed.text());
    }

    @Test
    void keepsPrivateUseCharactersWhenDisabled() {
        String text = "A\uE000" + new String(Character.toChars(0xF0000)) + "B";
        FormattingCodes.Parsed parsed = MessageCharacters.filter(FormattingCodes.Parsed.plain(text), false);
        assertSame(text, parsed.text());
    }

    @Test
    void keepsNoncharactersAtTheEndOfTheSupplementaryPlanes() {
        String text = new String(Character.toChars(0xFFFFE)) + new String(Character.toChars(0x10FFFF));
        FormattingCodes.Parsed parsed = MessageCharacters.filter(FormattingCodes.Parsed.plain(text), true);
        assertSame(text, parsed.text());
        assertFalse(MessageCharacters.isPrivateUse(0xFFFFE));
        assertFalse(MessageCharacters.isPrivateUse(0x10FFFF));
        assertTrue(MessageCharacters.isPrivateUse(0xE000));
        assertTrue(MessageCharacters.isPrivateUse(0xF0000));
        assertTrue(MessageCharacters.isPrivateUse(0x10FFFD));
    }

    @Test
    void shiftsFormattingOffARemovedPrefix() {
        int red = PackedFormatting.color(0xFF5555);
        FormattingCodes.Parsed parsed = MessageCharacters.filter(new FormattingCodes.Parsed(
            "\uFFFCHello", new long[]{PackedFormatting.run(1, 5, red)}));
        assertEquals("Hello", parsed.text());
        assertEquals(red, PackedFormatting.at(parsed.formatting(), 0));
        assertEquals(red, PackedFormatting.at(parsed.formatting(), 4));
    }

    @Test
    void returnsTheSameParsedValueWhenNothingIsRemoved() {
        FormattingCodes.Parsed parsed = FormattingCodes.Parsed.plain("hello");
        assertSame(parsed, MessageCharacters.filter(parsed, true));
    }
}
