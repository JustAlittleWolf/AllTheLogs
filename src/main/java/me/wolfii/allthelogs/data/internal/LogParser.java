package me.wolfii.allthelogs.data.internal;

import me.wolfii.allthelogs.data.LogFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Extracts chat lines and the Minecraft version from a log file.
///
/// Log formats differ between Minecraft versions and launchers, but every variant seen in the wild starts a line with a
/// bracketed timestamp, optionally prefixed by a date, followed by one or more bracketed tags and a `:`. Chat lines are
/// identified by the `[CHAT] ` marker that the vanilla client writes; a line that does not start with a timestamp is
/// treated as a continuation of the previous line, which is how multi line chat messages such as book pages appear.
public final class LogParser {
    private static final String CHAT_MARKER = "[CHAT] ";
    private static final String RESOURCE_MANAGER_RELOAD_MARKER = "Reloading ResourceManager";

    /// Matches the leading `[HH:mm:ss]` or `[yyyy-MM-dd HH:mm:ss]` of a log line. Fractional seconds, as written by
    /// some launchers, are tolerated and discarded.
    private static final Pattern LINE_START = Pattern.compile(
        "^\\[(?:\\d{4}-\\d{2}-\\d{2}[ T])?(\\d{1,2}):(\\d{2}):(\\d{2})(?:[.,]\\d+)?] ");

    /// Version detection patterns, most trustworthy first. The first pattern that matches anywhere in the file wins,
    /// and scanning for a version stops as soon as the most trustworthy one is found.
    private static final Pattern[] VERSION_PATTERNS = {
        Pattern.compile("Loading Minecraft (\\S+) with (?:Fabric|Quilt) Loader"),
        Pattern.compile("for Minecraft (\\S+) loading"),
        Pattern.compile("Starting integrated minecraft server version (\\S+)"),
        Pattern.compile("Minecraft Version: (\\S+)"),
        Pattern.compile("--version,? (\\S+)")
    };

    private LogParser() {
    }

    /// Reads the whole log and returns its chat entries plus the detected version.
    public static ParsedLog parse(BufferedReader reader) throws IOException {
        List<ParsedLog.Entry> entries = new ArrayList<>();
        StringBuilder pending = null;
        LocalTime pendingTime = null;
        String version = null;
        int versionPriority = Integer.MAX_VALUE;
        boolean resourceManagerReloaded = false;
        LocalTime firstLineTime = null;
        LocalTime lastLineTime = null;

        String line;
        while ((line = reader.readLine()) != null) {
            Matcher start = LINE_START.matcher(line);
            if (!start.find()) {
                // Not a new log line, so it belongs to whatever chat message we are currently collecting.
                if (pending != null) pending.append('\n').append(line);
                continue;
            }

            if (pending != null) {
                entries.add(new ParsedLog.Entry(pendingTime, pending.toString()));
                pending = null;
                pendingTime = null;
            }

            LocalTime lineTime = parseTime(start);
            if (lineTime != null) {
                if (firstLineTime == null) firstLineTime = lineTime;
                lastLineTime = lineTime;
            }

            if (!resourceManagerReloaded && line.contains(RESOURCE_MANAGER_RELOAD_MARKER)) {
                resourceManagerReloaded = true;
            }

            if (versionPriority > 0) {
                for (int i = 0; i < VERSION_PATTERNS.length && i < versionPriority; i++) {
                    Matcher versionMatcher = VERSION_PATTERNS[i].matcher(line);
                    if (versionMatcher.find()) {
                        version = versionMatcher.group(1);
                        versionPriority = i;
                        break;
                    }
                }
            }

            int chat = line.indexOf(CHAT_MARKER, start.end());
            if (chat < 0) {
                // Also accept a chat marker at the very end of the line, which is how empty chat lines are written.
                if (!line.endsWith("[CHAT]")) continue;
                chat = line.length() - "[CHAT]".length();
            }
            if (lineTime == null) continue;
            pendingTime = lineTime;
            pending = new StringBuilder(line.substring(Math.min(chat + CHAT_MARKER.length(), line.length())));
        }
        if (pending != null) entries.add(new ParsedLog.Entry(pendingTime, pending.toString()));

        entries.replaceAll(entry -> new ParsedLog.Entry(entry.time(), FormattingCodes.strip(entry.message())));
        return new ParsedLog(version == null ? LogFile.UNKNOWN_VERSION : version, entries,
            resourceManagerReloaded, firstLineTime, lastLineTime);
    }

    private static LocalTime parseTime(Matcher start) {
        int hour = Integer.parseInt(start.group(1));
        int minute = Integer.parseInt(start.group(2));
        int second = Integer.parseInt(start.group(3));
        if (hour > 23 || minute > 59 || second > 59) return null;
        return LocalTime.of(hour, minute, second);
    }
}
