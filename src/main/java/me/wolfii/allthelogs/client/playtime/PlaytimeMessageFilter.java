package me.wolfii.allthelogs.client.playtime;

import me.wolfii.allthelogs.client.search.MinecraftVersions;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.data.playtime.PlaytimeSpan;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
        return category(current, start, end, breakdown, series, false, List.of(), null);
    }

    /**
     * @param majorVersions   the series version is only the first two components
     * @param spans           stored playtime, used to expand a major version to the full versions it stands for
     * @param allowedVersions full versions the playtime filter keeps, or null when every version is kept
     */
    public static SearchFilter category(SearchFilter current, LocalDateTime start, LocalDateTime end,
                                        PlaytimeBreakdown breakdown, PlaytimeSeries series, boolean majorVersions,
                                        List<PlaytimeSpan> spans, Set<String> allowedVersions) {
        SearchFilter ranged = period(current, start, end);
        if (series == null || breakdown == null) return ranged;
        SearchFilter next = ranged;
        if (breakdown.byVersion() && series.canFilterVersion()) {
            next = next.withVersions(storedVersions(series.version(), majorVersions, spans, allowedVersions));
        }
        if (breakdown.byServer() && series.canFilterServer()) next = next.withServerOrWorld(series.serverKey());
        return next;
    }

    /**
     * Full versions a grouped series stands for. With major versions, every stored version whose first two
     * components match, limited to {@code allowed} when the playtime filter has narrowed versions.
     */
    static Set<String> storedVersions(String grouped, boolean majorVersions, List<PlaytimeSpan> spans,
                                      Set<String> allowed) {
        if (!majorVersions) return Set.of(grouped);
        Set<String> matches = new LinkedHashSet<>();
        if (spans != null) {
            for (PlaytimeSpan span : spans) {
                String version = PlaytimeFilter.key(span.version());
                if (allowed != null && !allowed.contains(version)) continue;
                if (grouped.equals(MinecraftVersions.major(version))) matches.add(version);
            }
        }
        return matches.isEmpty() ? Set.of(grouped) : Set.copyOf(matches);
    }
}
