package me.wolfii.allthelogs.data.importer;

import me.wolfii.allthelogs.data.importer.discover.LogCandidate;
import me.wolfii.allthelogs.data.parse.LogCharset;
import me.wolfii.allthelogs.data.parse.LogDates;
import me.wolfii.allthelogs.data.parse.LogParser;
import me.wolfii.allthelogs.data.parse.ParsedLog;
import me.wolfii.allthelogs.data.store.PreparedLog;

import java.io.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

/**
 * Turns a discovered log file into a {@link PreparedLog} ready to write.
 */
public final class LogPreparer {
    private LogPreparer() {
    }

    /**
     * Parses {@code candidate} and converts its timestamps from {@code timezone} to the JVM default zone.
     */
    public static PreparedLog prepare(LogCandidate candidate, ZoneId timezone) throws IOException {
        ParsedLog parsed;
        try (BufferedReader reader = open(candidate)) {
            parsed = LogParser.parse(reader);
        }
        LocalDate fileDate = LogDates.resolve(candidate.fileName(), candidate.lastModified(), timezone);

        List<LocalDateTime> times = new ArrayList<>(parsed.entries().size());
        List<String> messages = new ArrayList<>(parsed.entries().size());
        List<long[]> formattings = new ArrayList<>(parsed.entries().size());
        List<String> users = new ArrayList<>(parsed.entries().size());
        List<String> places = new ArrayList<>(parsed.entries().size());
        for (ParsedLog.Entry entry : parsed.entries()) {
            times.add(LogDates.toSystemLocal(calendarDate(entry.date(), fileDate), entry.time(), timezone));
            messages.add(entry.message());
            formattings.add(entry.formatting());
            users.add(entry.minecraftUser());
            places.add(entry.serverOrWorld());
        }
        LocalDateTime firstLineTime = LogDates.toSystemLocal(
            calendarDate(parsed.firstLineDate(), fileDate), parsed.firstLineTime(), timezone);
        LocalDateTime lastLineTime = LogDates.toSystemLocal(
            calendarDate(parsed.lastLineDate(), fileDate), parsed.lastLineTime(), timezone);
        return new PreparedLog(candidate.fileName(), candidate.sourceKind(), candidate.sourcePath(),
            candidate.entryPath(), fileDate, parsed.minecraftVersion(),
            times, messages, formattings, parsed.resourceManagerReloaded(),
            firstLineTime, lastLineTime, parsed.sessionId(), parsed.minecraftUser(),
            users, places, candidate.contentHash());
    }

    /**
     * Uses the date printed in the log line when one is present. Time-only lines still use the file
     * name / mtime date, so ordinary {@code [12:16:21]} logs keep the same calendar day as before.
     */
    private static LocalDate calendarDate(LocalDate lineDate, LocalDate fileDate) {
        return lineDate != null ? lineDate : fileDate;
    }

    private static BufferedReader open(LogCandidate candidate) throws IOException {
        InputStream stream = new ByteArrayInputStream(candidate.content());
        if (candidate.fileName().toLowerCase(Locale.ROOT).endsWith(".gz")) {
            stream = new GZIPInputStream(stream);
        }
        byte[] bytes = stream.readAllBytes();
        return new BufferedReader(new StringReader(LogCharset.decode(bytes)), 1 << 16);
    }
}
