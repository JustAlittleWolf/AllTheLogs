package me.wolfii.allthelogs.data.parse;

import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.store.SessionMarker;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Extracts chat lines and log metadata from a log file.
 * <p>
 * Lines start with a bracketed timestamp, optionally prefixed by a date. Chat lines are identified by the
 * {@code [CHAT] } marker. A line that does not start with a timestamp is treated as a continuation of the previous
 * chat line. Time, user, server/world, and version each have their own extractor.
 */
public final class LogParser {
    private static final String CHAT_MARKER = "[CHAT] ";
    private static final String EMPTY_CHAT_MARKER = "[CHAT]";
    private static final String RESOURCE_MANAGER_RELOAD_MARKER = "Reloading ResourceManager";

    private LogParser() {
    }

    /**
     * Reads the whole log and returns its chat entries plus the detected metadata.
     */
    public static ParsedLog parse(BufferedReader reader) throws IOException {
        List<ParsedLog.Entry> entries = new ArrayList<>();
        StringBuilder pending = null;
        LocalTime pendingTime = null;
        MinecraftVersionExtractor versions = new MinecraftVersionExtractor();
        MinecraftUserExtractor users = new MinecraftUserExtractor();
        ServerPlaceExtractor places = new ServerPlaceExtractor();
        boolean resourceManagerReloaded = false;
        LocalTime firstLineTime = null;
        LocalTime lastLineTime = null;
        String sessionId = null;

        String line;
        while ((line = reader.readLine()) != null) {
            Matcher start = LogTimeExtractor.LINE_START.matcher(line);
            if (!start.find()) {
                if (pending != null) {
                    pending.append('\n').append(line);
                } else {
                    versions.accept(line);
                }
                continue;
            }

            if (pending != null) {
                flushPending(entries, pendingTime, pending, places);
                pending = null;
                pendingTime = null;
            }

            LocalTime lineTime = LogTimeExtractor.parse(start);
            if (lineTime != null) {
                if (firstLineTime == null) firstLineTime = lineTime;
                lastLineTime = lineTime;
            }

            if (sessionId == null) {
                sessionId = SessionMarker.find(line).orElse(null);
            }

            users.accept(line);
            places.accept(line);

            if (!resourceManagerReloaded && line.contains(RESOURCE_MANAGER_RELOAD_MARKER)) {
                resourceManagerReloaded = true;
            }

            versions.accept(line);

            int chat = line.indexOf(CHAT_MARKER, start.end());
            if (chat < 0) {
                if (!line.endsWith(EMPTY_CHAT_MARKER)) continue;
                chat = line.length() - EMPTY_CHAT_MARKER.length();
            }
            if (lineTime == null) continue;
            pendingTime = lineTime;
            pending = new StringBuilder(line.substring(Math.min(chat + CHAT_MARKER.length(), line.length())));
        }
        if (pending != null) flushPending(entries, pendingTime, pending, places);

        entries.replaceAll(entry -> {
            FormattingCodes.Parsed parsed = FormattingCodes.parse(entry.message());
            return new ParsedLog.Entry(entry.time(), parsed.text(), parsed.formatting(), entry.serverPlace());
        });
        String version = versions.version();
        return new ParsedLog(version == null ? ChatLog.UNKNOWN_VERSION : version, users.user(), places.place(),
            entries, resourceManagerReloaded, firstLineTime, lastLineTime, sessionId);
    }

    private static void flushPending(List<ParsedLog.Entry> entries, LocalTime time, StringBuilder pending,
                                     ServerPlaceExtractor places) {
        entries.add(new ParsedLog.Entry(time, pending.toString(), null, places.current()));
    }
}
