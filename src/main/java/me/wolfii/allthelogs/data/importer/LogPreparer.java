package me.wolfii.allthelogs.data.importer;

import me.wolfii.allthelogs.data.discover.LogCandidate;
import me.wolfii.allthelogs.data.parse.LogDates;
import me.wolfii.allthelogs.data.parse.LogParser;
import me.wolfii.allthelogs.data.parse.ParsedLog;
import me.wolfii.allthelogs.data.store.PreparedLog;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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
    /**
     * Legacy Windows code page that old Minecraft launchers wrote logs in, before UTF-8 became the norm.
     */
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

    /**
     * Decodes as UTF-8 when the bytes are valid UTF-8, otherwise Windows-1252.
     * Older Windows clients wrote logs in the system code page; treating those bytes as UTF-8 would
     * replace the section sign used for formatting codes.
     */
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
