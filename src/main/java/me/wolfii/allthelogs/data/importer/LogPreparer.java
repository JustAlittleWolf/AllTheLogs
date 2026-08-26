package me.wolfii.allthelogs.data.importer;

import me.wolfii.allthelogs.data.discover.LogCandidate;
import me.wolfii.allthelogs.data.parse.LogDates;
import me.wolfii.allthelogs.data.parse.LogParser;
import me.wolfii.allthelogs.data.parse.ParsedLog;
import me.wolfii.allthelogs.data.store.PreparedLog;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
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
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

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
        LocalDate date = LogDates.resolve(candidate.fileName(), candidate.lastModified(), timezone);

        List<LocalDateTime> times = new ArrayList<>(parsed.entries().size());
        List<String> messages = new ArrayList<>(parsed.entries().size());
        for (ParsedLog.Entry entry : parsed.entries()) {
            times.add(LogDates.toSystemLocal(date, entry.time(), timezone));
            messages.add(entry.message());
        }
        LocalDateTime firstLineTime = LogDates.toSystemLocal(date, parsed.firstLineTime(), timezone);
        LocalDateTime lastLineTime = LogDates.toSystemLocal(date, parsed.lastLineTime(), timezone);
        return new PreparedLog(candidate.fileName(), candidate.sourceKind(), candidate.sourcePath(),
            candidate.entryPath(), date, parsed.minecraftVersion(),
            times, messages, parsed.resourceManagerReloaded(),
            firstLineTime, lastLineTime, parsed.sessionId());
    }

    private static BufferedReader open(LogCandidate candidate) throws IOException {
        InputStream stream = new ByteArrayInputStream(candidate.content());
        if (candidate.fileName().toLowerCase(Locale.ROOT).endsWith(".gz")) {
            stream = new GZIPInputStream(stream);
        }
        byte[] bytes = stream.readAllBytes();
        return new BufferedReader(new StringReader(decode(bytes)), 1 << 16);
    }

    private static String decode(byte[] bytes) {
        CharsetDecoder strictUtf8 = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return strictUtf8.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, WINDOWS_1252);
        }
    }
}
