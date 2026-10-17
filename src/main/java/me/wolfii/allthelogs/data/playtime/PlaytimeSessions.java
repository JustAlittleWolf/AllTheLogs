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
 * Splits each log file's {@code [start, end)} at place and player changes. The version stored on the file covers
 * the whole file: one game process runs one version, so a version named in chat is not a change.
 * The server stays unknown until a message is tagged with a place, then follows each change, including a return
 * to no place. The player starts as the one stored on the file and follows the messages' players; a message
 * without one keeps the current player.
 * <p>
 * Spans from different files are then clipped so the same moment is counted once. Ordered by start, a longer span
 * wins when two start together. That drops a duplicate such as Forge's {@code debug.log} when it covers the same
 * session as the dated log, and it also covers two clients running at once.
 */
public final class PlaytimeSessions {
    private PlaytimeSessions() {
    }

    /**
     * @param id      {@code log_file.id}
     * @param version version stored for the file, or blank for {@link ChatLog#UNKNOWN_VERSION}
     * @param user    player stored for the file, or null when unknown
     */
    public record FileSession(long id, String version, LocalDateTime start, LocalDateTime end, String user) {
        public FileSession(long id, String version, LocalDateTime start, LocalDateTime end) {
            this(id, version, start, end, null);
        }
    }

    /**
     * A message whose server or world, or player, differs from the previous message in the same file.
     * {@code server} is null when the player has left the previous place and the next one is not known yet.
     * {@code user} is null when the message names no player.
     */
    public record PlaceMark(long fileId, LocalDateTime time, int lineIndex, String server, String user) {
        public PlaceMark(long fileId, LocalDateTime time, int lineIndex, String server) {
            this(fileId, time, lineIndex, server, null);
        }
    }

    public static List<PlaytimeSpan> build(List<FileSession> files, List<PlaceMark> places) {
        Map<Long, List<Event>> events = new HashMap<>();
        if (places != null) {
            for (PlaceMark mark : places) {
                if (mark == null || mark.time() == null) continue;
                events.computeIfAbsent(mark.fileId(), id -> new ArrayList<>())
                    .add(new Event(mark.time(), mark.lineIndex(), mark.server(), mark.user()));
            }
        }
        List<PlaytimeSpan> spans = new ArrayList<>();
        if (files == null) return List.of();
        for (FileSession file : files) {
            if (file == null || file.start() == null || file.end() == null || !file.end().isAfter(file.start())) continue;
            List<Event> fileEvents = events.getOrDefault(file.id(), List.of());
            if (fileEvents.size() > 1) {
                fileEvents.sort(Comparator.comparing(Event::time).thenComparingInt(Event::line));
            }
            append(spans, file, fileEvents);
        }
        return withoutOverlap(spans);
    }

    /**
     * Drops any part of a span that another span, starting no later, already covers. Half-open ranges that only
     * touch at an endpoint both stay.
     */
    private static List<PlaytimeSpan> withoutOverlap(List<PlaytimeSpan> spans) {
        if (spans.isEmpty()) return List.of();
        List<PlaytimeSpan> ordered = new ArrayList<>(spans);
        ordered.sort(Comparator.comparing(PlaytimeSpan::start).thenComparing(PlaytimeSpan::end, Comparator.reverseOrder()));
        List<PlaytimeSpan> kept = new ArrayList<>();
        LocalDateTime coveredUntil = null;
        for (PlaytimeSpan span : ordered) {
            LocalDateTime start = span.start();
            if (coveredUntil != null && start.isBefore(coveredUntil)) start = coveredUntil;
            if (!span.end().isAfter(start)) continue;
            kept.add(start.equals(span.start()) ? span : span.withStartAndEnd(start, span.end()));
            coveredUntil = span.end();
        }
        return List.copyOf(kept);
    }

    private static void append(List<PlaytimeSpan> spans, FileSession file, List<Event> events) {
        String version = file.version() == null || file.version().isBlank()
            ? ChatLog.UNKNOWN_VERSION
            : file.version();
        String server = null;
        String user = blankToNull(file.user());
        LocalDateTime cursor = file.start();
        for (Event event : events) {
            if (!event.time().isBefore(file.end())) continue;
            String place = blankToNull(event.server());
            String player = event.user() == null || event.user().isBlank() ? user : event.user();
            if (Objects.equals(place, server) && Objects.equals(player, user)) continue;
            if (event.time().isAfter(cursor)) {
                spans.add(new PlaytimeSpan(cursor, event.time(), version, server, user));
                cursor = event.time();
            }
            server = place;
            user = player;
        }
        if (file.end().isAfter(cursor)) {
            spans.add(new PlaytimeSpan(cursor, file.end(), version, server, user));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record Event(LocalDateTime time, int line, String server, String user) {
    }
}
