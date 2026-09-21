package me.wolfii.allthelogs.data.parse;

import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.extract.LogTimeExtractor;
import me.wolfii.allthelogs.data.extract.MinecraftUserExtractor;
import me.wolfii.allthelogs.data.extract.MinecraftVersionExtractor;
import me.wolfii.allthelogs.data.extract.ServerOrWorldExtractor;
import me.wolfii.allthelogs.data.store.SessionMarker;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts chat lines and log metadata from a log file.
 * <p>
 * Lines start with a bracketed timestamp, optionally prefixed by a date. Chat lines are identified by the
 * {@code [CHAT] } marker. A line that does not start with a timestamp is treated as a continuation of the previous
 * chat line. Time, user, server/world, and version each have their own extractor. Those metadata
 * extractors skip {@code [CHAT]} lines so player text cannot look like a connect, disconnect, or
 * version line, and so import does not run those regexes on every chat message.
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
        List<Integer> pendingPlace = new ArrayList<>();
        StringBuilder pending = null;
        LocalDate pendingDate = null;
        LocalTime pendingTime = null;
        MinecraftVersionExtractor versions = new MinecraftVersionExtractor();
        MinecraftUserExtractor users = new MinecraftUserExtractor();
        ServerOrWorldExtractor places = new ServerOrWorldExtractor();
        boolean resourceManagerReloaded = false;
        LocalDate firstLineDate = null;
        LocalTime firstLineTime = null;
        LocalDate lastLineDate = null;
        LocalTime lastLineTime = null;
        String sessionId = null;

        String line;
        while ((line = reader.readLine()) != null) {
            LogTimeExtractor.Prefix prefix = LogTimeExtractor.match(line);
            if (prefix == null) {
                if (pending != null) {
                    pending.append('\n').append(line);
                } else {
                    versions.accept(line);
                }
                continue;
            }

            if (pending != null) {
                flushPending(entries, pendingPlace, pendingDate, pendingTime, pending, users, places);
                pending = null;
                pendingDate = null;
                pendingTime = null;
            }

            LogTimeExtractor.Stamp stamp = prefix.stamp();
            if (stamp != null) {
                if (firstLineTime == null) {
                    firstLineDate = stamp.date();
                    firstLineTime = stamp.time();
                }
                lastLineDate = stamp.date();
                lastLineTime = stamp.time();
            }

            int chat = line.indexOf(CHAT_MARKER, prefix.end());
            if (chat < 0 && line.endsWith(EMPTY_CHAT_MARKER)) {
                chat = line.length() - EMPTY_CHAT_MARKER.length();
            }
            if (!resourceManagerReloaded && line.contains(RESOURCE_MANAGER_RELOAD_MARKER)) {
                resourceManagerReloaded = true;
            }
            if (chat < 0) {
                if (sessionId == null) {
                    sessionId = SessionMarker.find(line).orElse(null);
                }
                users.accept(line);
                places.accept(line);
                if (places.current() != null) {
                    backfillPlace(entries, pendingPlace, places.current());
                } else if (!places.inSession()) {
                    pendingPlace.clear();
                }
                versions.accept(line);
                continue;
            }

            if (stamp == null) continue;
            pendingDate = stamp.date();
            pendingTime = stamp.time();
            pending = new StringBuilder(line.substring(Math.min(chat + CHAT_MARKER.length(), line.length())));
        }
        if (pending != null) {
            flushPending(entries, pendingPlace, pendingDate, pendingTime, pending, users, places);
        }

        String version = versions.version();
        return new ParsedLog(version == null ? ChatLog.UNKNOWN_VERSION : version, users.user(), places.last(),
            entries, resourceManagerReloaded, firstLineDate, firstLineTime, lastLineDate, lastLineTime, sessionId);
    }

    private static void flushPending(List<ParsedLog.Entry> entries, List<Integer> pendingPlace, LocalDate date,
                                     LocalTime time, StringBuilder pending, MinecraftUserExtractor users,
                                     ServerOrWorldExtractor places) {
        FormattingCodes.Parsed parsed = FormattingCodes.parse(pending.toString());
        entries.add(new ParsedLog.Entry(date, time, parsed.text(), parsed.formatting(), users.user(),
            places.current()));
        if (places.current() == null && places.inSession()) {
            pendingPlace.add(entries.size() - 1);
        }
    }

    private static void backfillPlace(List<ParsedLog.Entry> entries, List<Integer> pendingPlace, String place) {
        for (int index : pendingPlace) {
            ParsedLog.Entry entry = entries.get(index);
            entries.set(index, new ParsedLog.Entry(entry.date(), entry.time(), entry.message(), entry.formatting(),
                entry.minecraftUser(), place));
        }
        pendingPlace.clear();
    }
}
