package me.wolfii.allthelogs.data.importer;

import me.wolfii.allthelogs.data.importer.discover.LogCandidate;
import me.wolfii.allthelogs.data.parse.LogDates;
import me.wolfii.allthelogs.data.store.PreparedLog;
import me.wolfii.allthelogs.data.store.SourceKind;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogPreparerTest {
    @Test
    void embeddedDatesWinOverLatestLogMtime() throws IOException {
        Instant modified = Instant.parse("2026-09-20T12:00:00Z");
        ZoneId zone = ZoneId.systemDefault();
        PreparedLog prepared = LogPreparer.prepare(candidate("latest.log", modified, """
            [24Feb2026 16:08:08] [Render thread/INFO]: [CHAT] dated
            """), zone);
        assertEquals(LocalDateTime.of(2026, 2, 24, 16, 8, 8), prepared.entryTimes().getFirst());
        assertEquals(LocalDateTime.of(2026, 2, 24, 16, 8, 8), prepared.firstLineTime());
    }

    @Test
    void timeOnlyLinesStillUseTheFileDate() throws IOException {
        Instant modified = Instant.parse("2026-09-20T12:00:00Z");
        ZoneId zone = ZoneId.systemDefault();
        LocalDate fileDate = LogDates.resolve("latest.log", modified, zone);
        PreparedLog prepared = LogPreparer.prepare(candidate("latest.log", modified, """
            [16:08:08] [Render thread/INFO]: [CHAT] clock
            """), zone);
        assertEquals(fileDate.atTime(16, 8, 8), prepared.entryTimes().getFirst());
        assertEquals(fileDate.atTime(16, 8, 8), prepared.firstLineTime());
    }

    private static LogCandidate candidate(String fileName, Instant modified, String log) {
        byte[] bytes = log.getBytes(StandardCharsets.UTF_8);
        return new LogCandidate(fileName, SourceKind.FILE, "/tmp/" + fileName, "", modified, bytes, "hash");
    }
}
