package me.wolfii.allthelogs.client.playtime;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Inclusive start and exclusive end of the timeline currently on screen. Zoom keeps the instant under the
 * pointer fixed and never leaves the domain of the filtered playtime.
 */
public record PlaytimeWindow(LocalDateTime start, LocalDateTime end) {
    /** Narrowest view: about twenty days, or the whole history when it is shorter. */
    static final long MIN_SECONDS = 20 * 86_400L;
    private static final double ZOOM = 1.25;

    public PlaytimeWindow {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be after start");
        }
    }

    public static PlaytimeWindow clamp(PlaytimeWindow domain, PlaytimeWindow view) {
        if (domain == null) return view;
        if (view == null || !view.end().isAfter(domain.start()) || !view.start().isBefore(domain.end())) {
            return domain;
        }
        LocalDateTime start = view.start().isBefore(domain.start()) ? domain.start() : view.start();
        LocalDateTime end = view.end().isAfter(domain.end()) ? domain.end() : view.end();
        if (!end.isAfter(start)) return domain;
        return new PlaytimeWindow(start, end);
    }

    /**
     * @param fraction 0 at the oldest edge of {@code view}, 1 at the newest
     * @param steps    positive zooms in, negative zooms out
     */
    public static PlaytimeWindow zoom(PlaytimeWindow domain, PlaytimeWindow view, double fraction, double steps) {
        long domainStart = seconds(domain.start());
        long domainEnd = seconds(domain.end());
        long span = Math.max(1L, domainEnd - domainStart);
        long viewStart = seconds(view.start());
        long viewEnd = seconds(view.end());
        long viewSpan = Math.max(1L, viewEnd - viewStart);
        double clamped = Math.clamp(fraction, 0d, 1d);
        long anchor = viewStart + Math.round(clamped * viewSpan);
        long minSpan = Math.min(span, MIN_SECONDS);
        long newSpan = Math.round(viewSpan / Math.pow(ZOOM, steps));
        if (newSpan < minSpan) newSpan = minSpan;
        if (newSpan > span) newSpan = span;
        return place(domainStart, domainEnd, anchor, clamped, newSpan);
    }

    /**
     * @param fractionDelta portion of the current view to move. Positive moves toward newer time.
     */
    public static PlaytimeWindow pan(PlaytimeWindow domain, PlaytimeWindow view, double fractionDelta) {
        long domainStart = seconds(domain.start());
        long domainEnd = seconds(domain.end());
        long viewStart = seconds(view.start());
        long viewEnd = seconds(view.end());
        long viewSpan = Math.max(1L, viewEnd - viewStart);
        long shift = Math.round(fractionDelta * viewSpan);
        long newStart = viewStart + shift;
        long newEnd = newStart + viewSpan;
        if (newStart < domainStart) {
            newStart = domainStart;
            newEnd = newStart + viewSpan;
        }
        if (newEnd > domainEnd) {
            newEnd = domainEnd;
            newStart = newEnd - viewSpan;
            if (newStart < domainStart) newStart = domainStart;
        }
        return new PlaytimeWindow(fromSeconds(newStart), fromSeconds(newEnd));
    }

    private static PlaytimeWindow place(long domainStart, long domainEnd, long anchor, double fraction, long span) {
        long newStart = anchor - Math.round(fraction * span);
        if (newStart < domainStart) newStart = domainStart;
        long newEnd = newStart + span;
        if (newEnd > domainEnd) {
            newEnd = domainEnd;
            newStart = newEnd - span;
            if (newStart < domainStart) newStart = domainStart;
        }
        return new PlaytimeWindow(fromSeconds(newStart), fromSeconds(newEnd));
    }

    private static long seconds(LocalDateTime time) {
        return time.toEpochSecond(ZoneOffset.UTC);
    }

    private static LocalDateTime fromSeconds(long seconds) {
        return LocalDateTime.ofEpochSecond(seconds, 0, ZoneOffset.UTC);
    }
}
