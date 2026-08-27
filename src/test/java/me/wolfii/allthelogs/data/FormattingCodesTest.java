package me.wolfii.allthelogs.data;

import me.wolfii.allthelogs.data.parse.FormattingCodes;
import me.wolfii.allthelogs.data.parse.PackedFormatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class FormattingCodesTest {
    @Test
    void removesColourAndStyleCodes() {
        assertEquals("[Click Here] to watch a short ad",
                FormattingCodes.strip("\u00a7r\u00a7a[Click Here\u00a7a] \u00a77to watch a short ad"));
        assertEquals("bold and obfuscated",
                FormattingCodes.strip("\u00a7lbold\u00a7r and \u00a7kobfuscated\u00a7r"));
    }

    @Test
    void removesEveryValidCodeCharacterIncludingF() {
        for (char code : "0123456789abcdefklmnor".toCharArray()) {
            assertEquals("text", FormattingCodes.strip("\u00a7" + code + "text"), "code " + code);
        }
    }

    @Test
    void keepsSectionSignsThatAreNotFollowedByACode() {
        assertEquals("\u00a7 alone", FormattingCodes.strip("\u00a7 alone"));
        assertEquals("\u00a7z not a code", FormattingCodes.strip("\u00a7z not a code"));
        assertEquals("trailing \u00a7", FormattingCodes.strip("trailing \u00a7"));
    }

    @Test
    void stripsCodesRegardlessOfWhatPrecedesThem() {
        assertEquals("Art. 5 of the rules", FormattingCodes.strip("Art. 5\u00a72 of the rules"));
        assertEquals("see below", FormattingCodes.strip("see\u00a73 below"));
    }

    @Test
    void stripsRunsOfConsecutiveCodes() {
        assertEquals("text", FormattingCodes.strip("\u00a7a\u00a7b\u00a7ctext"));
        assertEquals("hi there", FormattingCodes.strip("\u00a7l\u00a7nhi \u00a7r\u00a76there"));
    }

    @Test
    void stripsCodesAfterNonAlphanumericCharacters() {
        assertEquals("] warning", FormattingCodes.strip("]\u00a7c warning"));
        assertEquals("a b", FormattingCodes.strip("a \u00a7cb"));
    }

    @Test
    void returnsTheSameInstanceWhenThereIsNothingToStrip() {
        String message = "plain chat line";
        assertSame(message, FormattingCodes.strip(message));
    }

    @Test
    void handlesEmptyAndCodeOnlyMessages() {
        assertEquals("", FormattingCodes.strip(""));
        assertEquals("", FormattingCodes.strip("\u00a7a\u00a7b"));
    }

    @Test
    void storesFlattenedColourAndStyleRunsAndOmitsReset() {
        FormattingCodes.Parsed parsed = FormattingCodes.parse(
            "\u00a7c\u00a7lHello \u00a79World\u00a7r!");
        assertEquals("Hello World!", parsed.text());
        int redBold = PackedFormatting.color(0xFF5555) | PackedFormatting.BOLD;
        int blue = PackedFormatting.color(0x5555FF);
        assertArrayEquals(new int[]{0, 6, redBold, 6, 5, blue}, parsed.formatting());
    }

    @Test
    void colourCodeClearsStylesLikeJavaEdition() {
        FormattingCodes.Parsed parsed = FormattingCodes.parse("\u00a7lX\u00a7cY");
        assertEquals("XY", parsed.text());
        assertEquals(PackedFormatting.BOLD, PackedFormatting.at(parsed.formatting(), 0));
        assertEquals(PackedFormatting.color(0xFF5555), PackedFormatting.at(parsed.formatting(), 1));
    }

    @Test
    void acceptsUppercaseCodes() {
        FormattingCodes.Parsed parsed = FormattingCodes.parse("\u00a7CRed");
        assertEquals("Red", parsed.text());
        assertEquals(PackedFormatting.color(0xFF5555), PackedFormatting.at(parsed.formatting(), 0));
    }

    @Test
    void unformattedMessagesHaveNullFormatting() {
        assertNull(FormattingCodes.parse("plain").formatting());
        assertNull(FormattingCodes.parse("\u00a7rreset only").formatting());
        assertNull(FormattingCodes.parse("\u00a7a\u00a7b").formatting());
    }

    @Test
    void seedsLiveFlatteningWithAnInitialStyle() {
        int green = PackedFormatting.color(0x55FF55);
        FormattingCodes.Parsed parsed = FormattingCodes.parse("Hi\u00a7l!", green);
        assertEquals("Hi!", parsed.text());
        assertEquals(green, PackedFormatting.at(parsed.formatting(), 0));
        assertEquals(green | PackedFormatting.BOLD, PackedFormatting.at(parsed.formatting(), 2));
    }

    @Test
    void packedSqlLiteralRoundTrips() {
        int[] packed = {0, 3, PackedFormatting.color(0xFF5555)};
        String literal = PackedFormatting.toSqlLiteral(packed);
        assertEquals("0,3," + PackedFormatting.color(0xFF5555), literal);
        assertArrayEquals(packed, PackedFormatting.fromSqlLiteral(literal));
        assertArrayEquals(packed, PackedFormatting.fromSqlLiteral("[0, 3, " + PackedFormatting.color(0xFF5555) + "]"));
        assertNull(PackedFormatting.fromSqlLiteral(null));
        assertNull(PackedFormatting.fromSqlLiteral("[]"));
    }
}
