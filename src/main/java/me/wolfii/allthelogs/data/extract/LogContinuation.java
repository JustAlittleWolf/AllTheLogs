package me.wolfii.allthelogs.data.extract;

import me.wolfii.allthelogs.data.ChatLog;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tells when a log continues a game session from the day before. Minecraft starts a new log file at
 * midnight, and that file has none of the start lines that name the version or the player.
 */
public final class LogContinuation {
    /**
     * How long before midnight the previous log may end, how long after midnight the continuation may start,
     * and the largest gap between the two.
     */
    public static final Duration MAX_GAP = Duration.ofMinutes(30);
    /** How far the two logs may overlap, or reach past midnight, when their clocks disagree a little. */
    public static final Duration MAX_OVERLAP = Duration.ofMinutes(5);

    private LogContinuation() {
    }

    /**
     * Whether a log has no start line: neither a version nor a player was found in it. A game start always
     * logs the player, so such a log is usually the part of a session after midnight.
     */
    public static boolean lacksStart(String minecraftVersion, String minecraftUser) {
        return isUnknown(minecraftVersion) && minecraftUser == null;
    }

    /**
     * Whether a log has a start line: a version or a player.
     */
    public static boolean hasStart(String minecraftVersion, String minecraftUser) {
        return !lacksStart(minecraftVersion, minecraftUser);
    }

    /**
     * Whether a log dated {@code nextDate} whose first line is at {@code nextStart} picks up where a log dated
     * {@code previousDate} ending at {@code previousEnd} stopped: it is dated one day later, the previous log
     * ends late in the evening and the next one starts just after midnight, each within {@link #MAX_GAP} of
     * midnight, and the gap between them is at most {@link #MAX_GAP}. {@link #MAX_OVERLAP} allows for clocks
     * that disagree a little. Both logs must come from the same {@link #folder}, and only a log that
     * {@link #lacksStart} can continue one that {@link #hasStart}.
     */
    public static boolean continues(LocalDate previousDate, LocalDateTime previousEnd,
                                    LocalDate nextDate, LocalDateTime nextStart) {
        if (!nextDate.equals(previousDate.plusDays(1))) return false;
        LocalDateTime midnight = nextDate.atStartOfDay();
        return within(previousEnd, midnight) && within(midnight, nextStart) && within(previousEnd, nextStart);
    }

    /**
     * Whether {@code later} is at most {@link #MAX_GAP} after {@code earlier}, or at most {@link #MAX_OVERLAP}
     * before it.
     */
    private static boolean within(LocalDateTime earlier, LocalDateTime later) {
        Duration gap = Duration.between(earlier, later);
        return gap.compareTo(MAX_GAP) <= 0 && gap.compareTo(MAX_OVERLAP.negated()) >= 0;
    }

    /**
     * The folder a log was found in: the file's directory, or for an archive entry the archive plus the
     * directory inside it. Only logs from the same folder belong to the same game instance.
     */
    public static String folder(boolean archive, String sourcePath, String entryPath) {
        return archive ? sourcePath + "!" + parent(entryPath) : parent(sourcePath);
    }

    public static boolean isUnknown(String minecraftVersion) {
        return minecraftVersion == null || minecraftVersion.isBlank()
            || ChatLog.UNKNOWN_VERSION.equalsIgnoreCase(minecraftVersion);
    }

    private static String parent(String path) {
        if (path == null) return "";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash < 0 ? "" : path.substring(0, slash);
    }
}
