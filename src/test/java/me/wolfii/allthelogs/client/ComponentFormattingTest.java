package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.data.parse.FormattingCodes;
import me.wolfii.allthelogs.data.parse.PackedFormatting;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ComponentFormattingTest {
    @Test
    void flattensNestedStylesToOneFormatPerCharacter() {
        Component message = Component.literal("Red ")
            .withStyle(Style.EMPTY.withColor(ChatFormatting.RED))
            .append(Component.literal("Bold").withStyle(Style.EMPTY.withBold(true)));
        FormattingCodes.Parsed parsed = ComponentFormatting.flatten(message);
        assertEquals("Red Bold", parsed.text());
        int red = PackedFormatting.color(0xFF5555);
        assertEquals(red, PackedFormatting.at(parsed.formatting(), 0));
        assertEquals(red | PackedFormatting.BOLD, PackedFormatting.at(parsed.formatting(), 4));
        assertEquals(red | PackedFormatting.BOLD, PackedFormatting.at(parsed.formatting(), 7));
    }

    @Test
    void ignoresHoverAndClickAndStripsLegacyCodes() {
        Component message = Component.literal("\u00a7lHi")
            .withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN));
        FormattingCodes.Parsed parsed = ComponentFormatting.flatten(message);
        assertEquals("Hi", parsed.text());
        int greenBold = PackedFormatting.color(0x55FF55) | PackedFormatting.BOLD;
        assertEquals(greenBold, PackedFormatting.at(parsed.formatting(), 0));
    }

    @Test
    void plainComponentsStoreNoFormatting() {
        assertNull(ComponentFormatting.flatten(Component.literal("hello")).formatting());
    }
}
