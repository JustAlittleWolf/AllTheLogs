package me.wolfii.allthelogs.client.playtime;

import me.wolfii.allthelogs.client.search.SearchFilter;

import java.time.LocalDateTime;

/**
 * Search filter produced by the playtime bar menu. The period is always applied. A colored section also
 * restricts the version, the server, or both, matching the chart's grouping.
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
        return switch (breakdown) {
            case VERSION -> series.canFilterVersion() ? ranged.withVersion(series.version()) : ranged;
            case SERVER -> series.canFilterServer() ? ranged.withServerOrWorld(series.serverKey()) : ranged;
            case COMBINED -> {
                SearchFilter next = ranged;
                if (series.canFilterVersion()) next = next.withVersion(series.version());
                if (series.canFilterServer()) next = next.withServerOrWorld(series.serverKey());
                yield next;
            }
        };
    }
}
