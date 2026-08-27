package me.wolfii.allthelogs.client;

import me.wolfii.allthelogs.data.parse.FormattingCodes;
import me.wolfii.allthelogs.data.parse.PackedFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayList;
import java.util.Optional;

/**
 * Flattens a live {@link Component} tree into stripped text plus packed, non-overlapping formatting.
 * Nested styles are resolved first; hover and click events are ignored. Legacy {@code §} codes inside
 * a run are then applied on top, using Java Edition colour-reset rules.
 */
public final class ComponentFormatting {
    private ComponentFormatting() {
    }

    public static FormattingCodes.Parsed flatten(Component message) {
        if (message == null) return FormattingCodes.Parsed.plain("");
        StringBuilder text = new StringBuilder();
        ArrayList<Integer> perChar = new ArrayList<>();
        message.visit((style, string) -> {
            FormattingCodes.Parsed parsed = FormattingCodes.parse(string, pack(style));
            text.append(parsed.text());
            long[] packed = parsed.formatting();
            for (int i = 0; i < parsed.text().length(); i++) {
                perChar.add(PackedFormatting.at(packed, i));
            }
            return Optional.empty();
        }, Style.EMPTY);
        if (text.isEmpty()) return FormattingCodes.Parsed.plain(text.toString());
        int[] formats = new int[perChar.size()];
        for (int i = 0; i < formats.length; i++) {
            formats[i] = perChar.get(i);
        }
        return new FormattingCodes.Parsed(text.toString(), PackedFormatting.pack(formats));
    }

    static int pack(Style style) {
        if (style == null || style.isEmpty()) return 0;
        int format = 0;
        TextColor color = style.getColor();
        if (color != null) {
            format = PackedFormatting.color(color.getValue());
        }
        if (style.isBold()) format |= PackedFormatting.BOLD;
        if (style.isItalic()) format |= PackedFormatting.ITALIC;
        if (style.isUnderlined()) format |= PackedFormatting.UNDERLINE;
        if (style.isStrikethrough()) format |= PackedFormatting.STRIKETHROUGH;
        if (style.isObfuscated()) format |= PackedFormatting.OBFUSCATED;
        return format;
    }
}
