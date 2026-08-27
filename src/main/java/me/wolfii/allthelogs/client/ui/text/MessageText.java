package me.wolfii.allthelogs.client.ui.text;

import me.wolfii.allthelogs.client.list.DisplayRow;
import me.wolfii.allthelogs.client.list.MessageWrap;
import me.wolfii.allthelogs.client.list.VisualMessage;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;
import me.wolfii.allthelogs.data.parse.PackedFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

/**
 * Minecraft {@link Component}s for a displayed chat line: timestamp gutter, coloured message text,
 * date headers, hover cards, and the list status chip.
 */
public final class MessageText {
    /**
     * Timestamp column sample used to reserve width for {@code HH:mm:ss} plus a gap before the message.
     */
    public static final String TIMESTAMP_GUTTER = "00:00:00  ";
    public static final float INFO_SCALE = 0.75f;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US);
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private MessageText() {
    }

    /**
     * Visible match count. Unknown totals above 99 are shown as {@code >99} until the exact total arrives.
     */
    static String matchCountText(long matches, boolean exact) {
        if (!exact && matches > 99) return ">99";
        return Long.toString(Math.max(0, matches));
    }

    static boolean singularMatch(long matches, boolean exact) {
        return exact && matches == 1;
    }

    /**
     * Search duration for the status chip: seconds with one decimal, or empty when under 0.1s after rounding.
     */
    static String searchDurationText(long elapsedMs) {
        double seconds = Math.round(Math.max(0, elapsedMs) / 100.0) / 10.0;
        if (seconds < 0.1) return "";
        return "%.1f".formatted(seconds);
    }

    /**
     * Text for the list's status chip: a persistent overlay, then loading, then the match count and search time.
     */
    public static Component listStatus(Component overlay, boolean loading, boolean showMatches, long matchCount,
                                       boolean exactCount, long elapsedMs) {
        if (overlay != null && !overlay.getString().isEmpty()) return overlay;
        if (loading) return Component.translatable("allthelogs.status.loading");
        if (!showMatches) return Component.empty();
        String duration = searchDurationText(elapsedMs);
        String count = matchCountText(matchCount, exactCount);
        boolean singular = singularMatch(matchCount, exactCount);
        if (duration.isEmpty()) {
            return Component.translatable(singular ? "allthelogs.status.match" : "allthelogs.status.matches", count);
        }
        return Component.translatable(
            singular ? "allthelogs.status.match.timed" : "allthelogs.status.matches.timed", count, duration);
    }

    public static Component timestamp(DisplayRow row) {
        int color = row.match() ? Colors.TIMESTAMP : Colors.CONTEXT_TIMESTAMP;
        return colored(row.entry().timestamp().toLocalTime().withNano(0).format(TIME), color);
    }

    /**
     * Compact hover card for a message timestamp: full date, labelled version/user, path and archive entry.
     */
    public static List<Component> messageInfo(DisplayRow row, int maxWidth, ToIntFunction<String> widthOf) {
        List<Component> lines = new ArrayList<>();
        String date = row.entry().timestamp().withNano(0).format(FULL_DATE);
        lines.add(colored(date, Colors.INFO_DATE));
        String version = displayVersion(row.chatLog());
        if (version != null) {
            lines.add(labeled("allthelogs.info.version", colored(version, Colors.INFO_VERSION)));
        }
        String user = row.chatLog().minecraftUser();
        if (user != null && !user.isBlank()) {
            lines.add(labeled("allthelogs.info.playing", colored(user, Colors.INFO_VERSION)));
        }
        int width = Math.max(16, maxWidth);
        switch (row.chatLog().source()) {
            case LogSource.File file -> wrapLabeled(lines, "allthelogs.info.path",
                file.path().toAbsolutePath().normalize().toString(), width, widthOf, Colors.INFO_FILE);
            case LogSource.Archive archive -> {
                wrapLabeled(lines, "allthelogs.info.path",
                    archive.path().toAbsolutePath().normalize().toString(), width, widthOf, Colors.INFO_FILE);
                wrapLabeled(lines, "allthelogs.info.entry", archive.entryPath(), width, widthOf, Colors.INFO_FILE);
            }
            case LogSource.Session ignored -> {
            }
        }
        return lines;
    }

    private static String displayVersion(ChatLog log) {
        if (log.minecraftVersion() == null || log.minecraftVersion().isBlank()) return null;
        if (ChatLog.UNKNOWN_VERSION.equals(log.minecraftVersion())) return null;
        return log.minecraftVersion();
    }

    public static Component dateHeader(LocalDate date) {
        return Component.literal(date.format(DATE));
    }

    public static Component messageRange(DisplayRow row, int from, int to) {
        String full = row.message();
        int start = Math.clamp(from, 0, full.length());
        int end = Math.clamp(to, start, full.length());
        String text = full.substring(start, end);
        if (text.isEmpty()) return Component.empty();
        boolean interpret = VisualMessage.interpretEscapes(row.chatLog());
        long[] formatting = row.visualFormatting();
        MutableComponent result = Component.empty();
        int runStart = 0;
        int runFormat = PackedFormatting.at(formatting, start);
        int runColor = stackedColor(row, start, interpret, runFormat);
        for (int i = 1; i <= text.length(); i++) {
            int format = i < text.length() ? PackedFormatting.at(formatting, start + i) : runFormat ^ 1;
            int color = i < text.length() ? stackedColor(row, start + i, interpret, format) : runColor ^ 1;
            if (color != runColor || format != runFormat) {
                result.append(styled(text.substring(runStart, i), runColor, runFormat));
                runStart = i;
                runFormat = format;
                runColor = color;
            }
        }
        return result.getSiblings().size() == 1 ? result.getSiblings().getFirst() : result;
    }

    /**
     * Chat colour with context dimming and {@code \n} darkening multiplied in that order.
     * Search hits are marked with a background fill, not a text tint.
     */
    static int stackedColor(DisplayRow row, int index, boolean interpretEscapes) {
        return stackedColor(row, index, interpretEscapes, PackedFormatting.at(row.visualFormatting(), index));
    }

    /**
     * Glyph used when measuring wrap width. Obfuscation is omitted so width stays stable and cheap;
     * Minecraft picks same-advance replacements when drawing {@code §k}.
     */
    public static Component measureChar(char c, int format) {
        return styled(String.valueOf(c), 0xFFFFFFFF, format & ~PackedFormatting.OBFUSCATED);
    }

    private static int stackedColor(DisplayRow row, int index, boolean interpretEscapes, int format) {
        int color = PackedFormatting.hasColor(format)
            ? 0xFF000000 | PackedFormatting.rgb(format)
            : Colors.MATCH_TEXT;
        if (!row.match()) {
            color = Colors.multiply(color, Colors.CONTEXT_TEXT);
        }
        if (VisualMessage.escapeChar(row.message(), index, interpretEscapes)) {
            color = Colors.multiply(color, Colors.ESCAPE_TEXT);
        }
        return color;
    }

    private static void wrapLabeled(List<Component> lines, String key, String value, int maxWidth,
                                    ToIntFunction<String> widthOf, int valueColor) {
        if (value == null || value.isBlank()) return;
        String prefix = Component.translatable(key, "").getString();
        int prefixWidth = widthOf.applyAsInt(prefix);
        int valueWidth = Math.max(8, maxWidth - prefixWidth);
        List<MessageWrap.Line> wrapped = MessageWrap.wrap(value, valueWidth,
            MessageWrap.substringWidths(value, widthOf));
        if (wrapped.isEmpty()) {
            lines.add(labeled(key, colored(value, valueColor)));
            return;
        }
        lines.add(labeled(key, colored(wrapped.getFirst().text(), valueColor)));
        for (int i = 1; i < wrapped.size(); i++) {
            lines.add(colored(wrapped.get(i).text(), valueColor));
        }
    }

    private static Component labeled(String key, Component value) {
        return muted(Component.translatable(key, value));
    }

    private static Component muted(Component component) {
        return component.copy().withStyle(Style.EMPTY.withColor(Colors.META_LABEL & 0xFFFFFF));
    }

    private static Component styled(String text, int argb, int format) {
        Style style = Style.EMPTY.withColor(argb & 0xFFFFFF);
        if (PackedFormatting.bold(format)) style = style.withBold(true);
        if (PackedFormatting.italic(format)) style = style.withItalic(true);
        if (PackedFormatting.underline(format)) style = style.withUnderlined(true);
        if (PackedFormatting.strikethrough(format)) style = style.withStrikethrough(true);
        if (PackedFormatting.obfuscated(format)) style = style.withObfuscated(true);
        return Component.literal(text).withStyle(style);
    }

    private static Component colored(String text, int argb) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(argb & 0xFFFFFF));
    }
}
