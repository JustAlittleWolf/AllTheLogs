package me.wolfii.allthelogs.client.playtime;

import me.wolfii.allthelogs.client.search.SearchFilter;

import java.time.LocalDateTime;

/**
 * Search filter produced by the playtime bar menu. The period is always applied. A colored section also
 * restricts the version and the server it stands for, when the chart groups by them and they are known.
 * Messages cannot be filtered by player, so a player grouping only narrows the period.
 */
public final class PlaytimeMessageFilter {
    private PlaytimeMessageFilter() {
    }

    public static SearchFilter period(SearchFilter current, LocalDateTime start, LocalDateTime end) {
        SearchFilter base = current == null ? SearchFilter.defaults() : current;
        return base.withoutOffset().withStartingAt(start).withUpUntil(end).withVersion(null).withServerOrWorld(null);
    }

    public static SearchFilter category(SearchFilter current, LocalDateTime start, LocalDateTime end,
                                        PlaytimeBreakdown breakdown, PlaytimeSeries series) {
        SearchFilter ranged = period(current, start, end);
        if (series == null || breakdown == null) return ranged;
        SearchFilter next = ranged;
        if (breakdown.byVersion() && series.canFilterVersion()) next = next.withVersion(series.version());
        if (breakdown.byServer() && series.canFilterServer()) next = next.withServerOrWorld(series.serverKey());
        return next;
    }
}
