package me.wolfii.allthelogs.data.playtime;

import me.wolfii.allthelogs.data.ChatLog;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Splits each log file's {@code [start, end)} into spans. The file's stored version applies until a later
 * log message names a different one. The server stays unknown until a message is tagged with a place, then
 * follows each change, including a return to no place.
 */
public final class PlaytimeSessions {
    private PlaytimeSessions() {
    }

    /**
     * @param id      {@code log_file.id}
     * @param version version stored for the file, or blank for {@link ChatLog#UNKNOWN_VERSION}
     */
    public record FileSession(long id, String version, LocalDateTime start, LocalDateTime end) {
    }

    /**
     * A message whose server or world differs from the previous message in the same file.
     * {@code server} is null when the player has left the previous place and the next one is not known yet.
     */
    public record PlaceMark(long fileId, LocalDateTime time, int lineIndex, String server) {
    }

    /**
     * A message that names a Minecraft version. Marks equal to the version already in effect are ignored.
     */
    public record VersionMark(long fileId, LocalDateTime time, int lineIndex, String version) {
    }

    public static List<PlaytimeSpan> build(List<FileSession> files, List<PlaceMark> places, List<VersionMark> versions) {
        Map<Long, List<Event>> events = new HashMap<>();
        if (places != null) {
            for (PlaceMark mark : places) {
                if (mark == null || mark.time() == null) continue;
                events.computeIfAbsent(mark.fileId(), id -> new ArrayList<>())
                    .add(Event.place(mark.time(), mark.lineIndex(), mark.server()));
            }
        }
        if (versions != null) {
            for (VersionMark mark : versions) {
                if (mark == null || mark.time() == null || mark.version() == null || mark.version().isBlank()) continue;
                events.computeIfAbsent(mark.fileId(), id -> new ArrayList<>())
                    .add(Event.version(mark.time(), mark.lineIndex(), mark.version().strip()));
            }
        }
        List<PlaytimeSpan> spans = new ArrayList<>();
        if (files == null) return List.of();
        for (FileSession file : files) {
            if (file == null || file.start() == null || file.end() == null || !file.end().isAfter(file.start())) continue;
            List<Event> fileEvents = events.getOrDefault(file.id(), List.of());
            if (fileEvents.size() > 1) {
                fileEvents.sort(Comparator.comparing(Event::time).thenComparingInt(Event::line).thenComparingInt(Event::order));
            }
            append(spans, file, fileEvents);
        }
        return List.copyOf(spans);
    }

    private static void append(List<PlaytimeSpan> spans, FileSession file, List<Event> events) {
        String version = file.version() == null || file.version().isBlank()
            ? ChatLog.UNKNOWN_VERSION
            : file.version();
        String server = null;
        LocalDateTime cursor = file.start();
        for (Event event : events) {
            if (!event.time().isBefore(file.end())) continue;
            String nextVersion = version;
            String nextServer = server;
            boolean placeChanged = false;
            if (event.version() != null && !event.version().equals(version)) {
                nextVersion = event.version();
            }
            if (event.place()) {
                String place = event.server() == null || event.server().isBlank() ? null : event.server();
                placeChanged = !Objects.equals(place, server);
                nextServer = place;
            }
            if (nextVersion.equals(version) && !placeChanged) continue;
            if (event.time().isAfter(cursor)) {
                spans.add(new PlaytimeSpan(cursor, event.time(), version, server));
                cursor = event.time();
            }
            version = nextVersion;
            server = nextServer;
        }
        if (file.end().isAfter(cursor)) {
            spans.add(new PlaytimeSpan(cursor, file.end(), version, server));
        }
    }

    private record Event(LocalDateTime time, int line, int order, String version, String server, boolean place) {
        static Event version(LocalDateTime time, int line, String version) {
            return new Event(time, line, 0, version, null, false);
        }

        static Event place(LocalDateTime time, int line, String server) {
            return new Event(time, line, 1, null, server, true);
        }
    }
}
