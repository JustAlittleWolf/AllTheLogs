package me.wolfii.allthelogs.client.ui;

import me.wolfii.allthelogs.client.view.ContextColors;
import me.wolfii.allthelogs.client.view.DisplayRow;
import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.LogSource;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageComponentsTest {
    @Test
    void matchCountTextCapsAt99() {
        assertEquals("0", MessageComponents.matchCountText(0));
        assertEquals("12", MessageComponents.matchCountText(12));
        assertEquals(">99", MessageComponents.matchCountText(100));
    }

    @Test
    void messageInfoIsDatePlayedLineAndWrappedPath() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 19, 22, 14);
        ChatLog log = new ChatLog(new LogSource.File(Path.of("/home/wolf/logs/2026-08-27-1.log.gz")),
            LocalDate.of(2026, 8, 27), "1.12.2", time, time, "Steve");
        DisplayRow row = new DisplayRow(new ChatEntry(log, time, 0, "hi"), true, Duration.ZERO, List.of());
        List<Component> info = MessageComponents.messageInfo(row);
        assertEquals("2026-08-27 19:22:14", info.get(0).getString());
        assertEquals("Version 1.12.2 as Steve", info.get(1).getString());
        assertTrue(info.get(2).getString().contains("2026-08-27-1.log.gz"));
        assertEquals(ContextColors.INFO_VERSION & 0xFFFFFF, info.get(1).getStyle().getColor().getValue());
        ChatLog unnamed = new ChatLog(new LogSource.File(Path.of("/tmp/a.log")),
            LocalDate.of(2026, 8, 27), ChatLog.UNKNOWN_VERSION, time, time, "Alex");
        assertEquals("Played as Alex", MessageComponents.playedLine(unnamed));
        ChatLog session = new ChatLog(new LogSource.Session("id"), LocalDate.of(2026, 8, 27), "26.2", time, time);
        List<Component> sessionInfo = MessageComponents.messageInfo(
            new DisplayRow(new ChatEntry(session, time, 0, "hi"), true, Duration.ZERO, List.of()));
        assertEquals(2, sessionInfo.size());
        assertEquals("Version 26.2", sessionInfo.get(1).getString());
    }

    @Test
    void messageInfoWrapsLongPaths() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 19, 22, 14);
        ChatLog log = new ChatLog(new LogSource.File(Path.of("/very/long/path/to/the/log/file.log")),
            LocalDate.of(2026, 8, 27), "26.2", time, time);
        DisplayRow row = new DisplayRow(new ChatEntry(log, time, 0, "hi"), true, Duration.ZERO, List.of());
        List<Component> info = MessageComponents.messageInfo(row, 12, String::length);
        assertTrue(info.size() > 2);
    }

    @Test
    void listStatusIncludesSearchDuration() {
        Component status = MessageComponents.listStatus(Component.empty(), false, true, 4, 12);
        assertEquals("allthelogs.status.matches",
            ((net.minecraft.network.chat.contents.TranslatableContents) status.getContents()).getKey());
        Object[] args = ((net.minecraft.network.chat.contents.TranslatableContents) status.getContents()).getArgs();
        assertEquals("4", args[0]);
        assertEquals("12", args[1]);
    }

    @Test
    void literalEscapesAreGreyedWhenDrawn() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 12, 0);
        ChatLog log = new ChatLog(new LogSource.File(Path.of("a.log")), LocalDate.of(2026, 8, 27), "26.2", time, time);
        DisplayRow row = new DisplayRow(new ChatEntry(log, time, 0, "hello\\nworld"), true, Duration.ZERO, List.of());
        assertEquals("hello\\n\nworld", row.message());
        Component drawn = MessageComponents.message(row);
        assertTrue(drawn.getString().contains("\\n"));
    }
}
