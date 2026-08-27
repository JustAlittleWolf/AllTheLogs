package me.wolfii.allthelogs.client.ui.text;

import me.wolfii.allthelogs.client.ui.theme.Colors;
import me.wolfii.allthelogs.client.list.DisplayRow;
import me.wolfii.allthelogs.client.list.HighlightSpan;
import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;
import me.wolfii.allthelogs.data.parse.PackedFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageTextTest {
    @Test
    void matchCountTextCapsUntilTheExactTotalIsKnown() {
        assertEquals("0", MessageText.matchCountText(0));
        assertEquals("12", MessageText.matchCountText(12));
        assertEquals(">99", MessageText.matchCountText(100, false));
        assertEquals("150", MessageText.matchCountText(150, true));
    }

    @Test
    void searchDurationHidesSubTenthSecondsAndRoundsToOneDecimal() {
        assertEquals("", MessageText.searchDurationText(49));
        assertEquals("0.1", MessageText.searchDurationText(50));
        assertEquals("0.1", MessageText.searchDurationText(120));
        assertEquals("1.5", MessageText.searchDurationText(1540));
    }

    @Test
    void messageInfoUsesMutedLabelsAndSplitsArchivePaths() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 19, 22, 14);
        ChatLog log = new ChatLog(new LogSource.File(Path.of("/home/wolf/logs/2026-08-27-1.log.gz")),
            LocalDate.of(2026, 8, 27), "1.12.2", time, time, "Steve");
        DisplayRow row = new DisplayRow(new ChatEntry(log, time, 0, "hi"), true, Duration.ZERO, List.of());
        List<Component> info = MessageText.messageInfo(row);
        assertEquals("2026-08-27 19:22:14", info.get(0).getString());
        assertEquals("allthelogs.info.version", key(info.get(1)));
        assertEquals("allthelogs.info.playing", key(info.get(2)));
        assertEquals("allthelogs.info.path", key(info.get(3)));
        assertEquals(Colors.META_LABEL & 0xFFFFFF, info.get(1).getStyle().getColor().getValue());
        assertEquals("1.12.2", ((Component) args(info.get(1))[0]).getString());
        assertEquals("Steve", ((Component) args(info.get(2))[0]).getString());
        assertTrue(((Component) args(info.get(3))[0]).getString().contains("2026-08-27-1.log.gz"));
        ChatLog unnamed = new ChatLog(new LogSource.File(Path.of("/tmp/a.log")),
            LocalDate.of(2026, 8, 27), ChatLog.UNKNOWN_VERSION, time, time, "Alex");
        assertEquals("Played as Alex", MessageText.playedLine(unnamed));
        ChatLog archive = new ChatLog(new LogSource.Archive(Path.of("/tmp/logs.zip"), "instance/logs/latest.log"),
            LocalDate.of(2026, 8, 27), "26.2", time, time);
        List<Component> archiveInfo = MessageText.messageInfo(
            new DisplayRow(new ChatEntry(archive, time, 0, "hi"), true, Duration.ZERO, List.of()));
        assertEquals("allthelogs.info.path", key(archiveInfo.get(2)));
        assertEquals("allthelogs.info.entry", key(archiveInfo.get(3)));
        assertEquals("instance/logs/latest.log", ((Component) args(archiveInfo.get(3))[0]).getString());
    }

    @Test
    void messageInfoWrapsLongPaths() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 19, 22, 14);
        ChatLog log = new ChatLog(new LogSource.File(Path.of("/very/long/path/to/the/log/file.log")),
            LocalDate.of(2026, 8, 27), "26.2", time, time);
        DisplayRow row = new DisplayRow(new ChatEntry(log, time, 0, "hi"), true, Duration.ZERO, List.of());
        List<Component> info = MessageText.messageInfo(row, 12, String::length);
        assertTrue(info.size() > 2);
    }

    @Test
    void listStatusOmitsDurationUnderATenthOfASecond() {
        Component status = MessageText.listStatus(Component.empty(), false, true, 4, 12);
        assertEquals("allthelogs.status.matches", key(status));
        assertEquals("4", args(status)[0]);
        Component timed = MessageText.listStatus(Component.empty(), false, true, 4, 120);
        assertEquals("allthelogs.status.matches.timed", key(timed));
        assertEquals("4", args(timed)[0]);
        assertEquals("0.1", args(timed)[1]);
    }

    @Test
    void listStatusUsesSingularMatchForOneHit() {
        Component status = MessageText.listStatus(Component.empty(), false, true, 1, true, 0);
        assertEquals("allthelogs.status.match", key(status));
        assertEquals("1", args(status)[0]);
        Component timed = MessageText.listStatus(Component.empty(), false, true, 1, true, 120);
        assertEquals("allthelogs.status.match.timed", key(timed));
        Component capped = MessageText.listStatus(Component.empty(), false, true, 100, false, 0);
        assertEquals("allthelogs.status.matches", key(capped));
        assertEquals(">99", args(capped)[0]);
    }

    @Test
    void literalEscapesAreGreyedWhenDrawn() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 12, 0);
        ChatLog log = new ChatLog(new LogSource.File(Path.of("a.log")), LocalDate.of(2026, 8, 27), "26.2", time, time);
        DisplayRow row = new DisplayRow(new ChatEntry(log, time, 0, "hello\\nworld"), true, Duration.ZERO, List.of());
        assertEquals("hello\\n\nworld", row.message());
        Component drawn = MessageText.message(row);
        assertTrue(drawn.getString().contains("\\n"));
        int escape = MessageText.stackedColor(row, 5, true);
        assertEquals(Colors.ESCAPE_TEXT & 0xFFFFFF, escape & 0xFFFFFF);
    }

    @Test
    void highlightAndContextAndEscapesStack() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 12, 0);
        ChatLog log = new ChatLog(new LogSource.File(Path.of("a.log")), LocalDate.of(2026, 8, 27), "26.2", time, time);
        DisplayRow match = new DisplayRow(new ChatEntry(log, time, 0, "needle"), true, Duration.ZERO,
            List.of(new HighlightSpan(0, 3)));
        assertEquals(Colors.MATCH_HIGHLIGHT, MessageText.stackedColor(match, 0, false));
        assertEquals(Colors.MATCH_TEXT, MessageText.stackedColor(match, 3, false));
        DisplayRow context = new DisplayRow(new ChatEntry(log, time, 1, "hello\\nworld"), false, Duration.ofSeconds(1),
            List.of());
        assertEquals(Colors.CONTEXT_TEXT, MessageText.stackedColor(context, 0, true));
        assertEquals(Colors.multiply(Colors.CONTEXT_TEXT, Colors.ESCAPE_TEXT),
            MessageText.stackedColor(context, 5, true));
        int red = PackedFormatting.color(0xFF5555);
        DisplayRow coloured = new DisplayRow(
            new ChatEntry(log, time, 2, "abc", new long[]{PackedFormatting.run(0, 3, red)}), true, Duration.ZERO, List.of());
        assertEquals(0xFFFF5555, MessageText.stackedColor(coloured, 1, false));
        Component drawn = MessageText.message(coloured);
        assertEquals(0xFF5555, drawn.getStyle().getColor().getValue());
        assertEquals("abc", drawn.getString());
    }

    @Test
    void measureCharDropsObfuscationAndKeepsBold() {
        int format = PackedFormatting.OBFUSCATED | PackedFormatting.BOLD;
        Component glyph = MessageText.measureChar('k', format);
        assertTrue(glyph.getStyle().isBold());
        assertFalse(glyph.getStyle().isObfuscated());
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 12, 0);
        ChatLog log = new ChatLog(new LogSource.File(Path.of("a.log")), LocalDate.of(2026, 8, 27), "26.2", time, time);
        DisplayRow row = new DisplayRow(
            new ChatEntry(log, time, 0, "abc", new long[]{PackedFormatting.run(0, 3, PackedFormatting.OBFUSCATED)}),
            true, Duration.ZERO, List.of());
        Component drawn = MessageText.message(row);
        assertTrue(drawn.getStyle().isObfuscated());
        assertEquals("abc", drawn.getString());
    }

    private static String key(Component component) {
        return ((TranslatableContents) component.getContents()).getKey();
    }

    private static Object[] args(Component component) {
        return ((TranslatableContents) component.getContents()).getArgs();
    }
}
