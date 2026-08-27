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

class MessageComponentsTest {
    @Test
    void matchCountTextCapsAt99() {
        assertEquals("0", MessageComponents.matchCountText(0));
        assertEquals("12", MessageComponents.matchCountText(12));
        assertEquals(">99", MessageComponents.matchCountText(100));
    }

    @Test
    void messageInfoIsDateVersionAndFile() {
        LocalDateTime time = LocalDateTime.of(2026, 8, 27, 19, 22, 14);
        ChatLog log = new ChatLog(new LogSource.File(Path.of("/home/wolf/logs/2026-08-27-1.log.gz")),
            LocalDate.of(2026, 8, 27), "1.12.2", time, time);
        DisplayRow row = new DisplayRow(new ChatEntry(log, time, 0, "hi"), true, Duration.ZERO, List.of());
        List<Component> info = MessageComponents.messageInfo(row);
        assertEquals("2026-08-27 19:22:14", info.get(0).getString());
        assertEquals("1.12.2", info.get(1).getString());
        assertEquals("2026-08-27-1.log.gz", info.get(2).getString());
        assertEquals(ContextColors.INFO_VERSION & 0xFFFFFF, info.get(1).getStyle().getColor().getValue());
    }
}
