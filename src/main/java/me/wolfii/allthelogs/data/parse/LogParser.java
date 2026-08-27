package me.wolfii.allthelogs.data.parse;

import me.wolfii.allthelogs.data.ChatLog;
import me.wolfii.allthelogs.data.store.SessionMarker;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts chat lines and the Minecraft version from a log file.
 * <p>
 * Lines start with a bracketed timestamp, optionally prefixed by a date. Chat lines are identified by the
 * {@code [CHAT] } marker. A line that does not start with a timestamp is treated as a continuation of the previous
 * chat line.
 */
public final class LogParser {
    private static final String CHAT_MARKER = "[CHAT] ";
    private static final String EMPTY_CHAT_MARKER = "[CHAT]";
    private static final String RESOURCE_MANAGER_RELOAD_MARKER = "Reloading ResourceManager";

    private static final Pattern LINE_START = Pattern.compile(
        "^\\[(?:\\d{4}-\\d{2}-\\d{2}[ T])?(\\d{1,2}):(\\d{2}):(\\d{2})(?:[.,]\\d+)?] ");

    private static final Pattern USER = Pattern.compile("Setting user: (\\S+)");

    /**
     * Lower index wins when several version lines appear in one file.
     */
    private static final Pattern[] VERSION_PATTERNS = {
        Pattern.compile("Loading Minecraft (\\S+) with (?:Fabric|Quilt) Loader"),
        Pattern.compile("for Minecraft (\\S+) loading"),
        Pattern.compile("OptiFine[_ ](\\d+\\.\\d+(?:\\.\\d+)?)"),
        Pattern.compile("(?i)^\\s*-\\s*minecraft\\s+(\\S+)"),
        Pattern.compile("Starting integrated minecraft server version (\\S+)"),
        Pattern.compile("Minecraft Version: (\\S+)"),
        Pattern.compile("--version,? (\\S+)")
    };

    private LogParser() {
    }

    /**
     * Reads the whole log and returns its chat entries plus the detected version.
     */
    public static ParsedLog parse(BufferedReader reader) throws IOException {
        List<ParsedLog.Entry> entries = new ArrayList<>();
        StringBuilder pending = null;
        LocalTime pendingTime = null;
        String version = null;
        int versionPriority = Integer.MAX_VALUE;
        String minecraftUser = null;
        boolean resourceManagerReloaded = false;
        LocalTime firstLineTime = null;
        LocalTime lastLineTime = null;
        String sessionId = null;

        String line;
        while ((line = reader.readLine()) != null) {
            Matcher start = LINE_START.matcher(line);
            if (!start.find()) {
                if (pending != null) {
                    pending.append('\n').append(line);
                } else {
                    VersionHit hit = findVersion(line, versionPriority);
                    if (hit != null) {
                        version = hit.value;
                        versionPriority = hit.priority;
                    }
                }
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

            if (sessionId == null) {
                sessionId = SessionMarker.find(line).orElse(null);
            }

            if (minecraftUser == null) {
                Matcher user = USER.matcher(line);
                if (user.find()) minecraftUser = user.group(1);
            }

            if (!resourceManagerReloaded && line.contains(RESOURCE_MANAGER_RELOAD_MARKER)) {
                resourceManagerReloaded = true;
            }

            VersionHit hit = findVersion(line, versionPriority);
            if (hit != null) {
                version = hit.value;
                versionPriority = hit.priority;
            }

            int chat = line.indexOf(CHAT_MARKER, start.end());
            if (chat < 0) {
                if (!line.endsWith(EMPTY_CHAT_MARKER)) continue;
                chat = line.length() - EMPTY_CHAT_MARKER.length();
            }
            if (lineTime == null) continue;
            pendingTime = lineTime;
            pending = new StringBuilder(line.substring(Math.min(chat + CHAT_MARKER.length(), line.length())));
        }
        if (pending != null) entries.add(new ParsedLog.Entry(pendingTime, pending.toString()));

        entries.replaceAll(entry -> new ParsedLog.Entry(entry.time(), FormattingCodes.strip(entry.message())));
        return new ParsedLog(version == null ? ChatLog.UNKNOWN_VERSION : version, minecraftUser, entries,
            resourceManagerReloaded, firstLineTime, lastLineTime, sessionId);
    }

    private static VersionHit findVersion(String line, int currentPriority) {
        if (currentPriority <= 0) return null;
        for (int i = 0; i < VERSION_PATTERNS.length && i < currentPriority; i++) {
            Matcher versionMatcher = VERSION_PATTERNS[i].matcher(line);
            if (versionMatcher.find()) {
                return new VersionHit(versionMatcher.group(1), i);
            }
        }
        return null;
    }

    private static LocalTime parseTime(Matcher start) {
        int hour = Integer.parseInt(start.group(1));
        int minute = Integer.parseInt(start.group(2));
        int second = Integer.parseInt(start.group(3));
        if (hour > 23 || minute > 59 || second > 59) return null;
        return LocalTime.of(hour, minute, second);
    }

    private record VersionHit(String value, int priority) {
    }
}
