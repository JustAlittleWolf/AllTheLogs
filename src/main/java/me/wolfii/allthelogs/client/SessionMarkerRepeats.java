package me.wolfii.allthelogs.client;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * When to write the session marker again. Minecraft starts a new log file at midnight, and that file
 * would not name the running session, so a later file import could not tell it is already stored. The
 * marker is repeated at {@link #TIMES} on every day after the session started; the repeats after
 * midnight are a fallback in case the first one is missed, e.g. while the game hangs.
 */
final class SessionMarkerRepeats {
    static final List<LocalTime> TIMES = List.of(LocalTime.MIDNIGHT, LocalTime.of(0, 5), LocalTime.of(0, 30));

    private final LocalDate sessionDate;
    private LocalDateTime checked;

    SessionMarkerRepeats(LocalDateTime sessionStart) {
        this.sessionDate = sessionStart.toLocalDate();
        this.checked = sessionStart;
    }

    /**
     * Whether one of {@link #TIMES} on a day after the session's first day passed since the last call.
     */
    boolean due(LocalDateTime now) {
        if (!now.isAfter(checked)) return false;
        boolean due = false;
        for (LocalDate day = checked.toLocalDate(); !day.isAfter(now.toLocalDate()); day = day.plusDays(1)) {
            if (!day.isAfter(sessionDate)) continue;
            for (LocalTime time : TIMES) {
                LocalDateTime at = day.atTime(time);
                if (at.isAfter(checked) && !at.isAfter(now)) due = true;
            }
        }
        checked = now;
        return due;
    }
}
