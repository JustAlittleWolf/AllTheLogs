package me.wolfii.allthelogs.client.playtime;

import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.data.playtime.PlaytimeSpan;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Versions and servers to keep. A null set means every value; an empty set means none.
 * A missing version or server is stored as {@link ChatQuery#NULL}; see {@link #key}.
 */
public record PlaytimeFilter(Set<String> versions, Set<String> servers) {
    public PlaytimeFilter {
        versions = copy(versions);
        servers = copy(servers);
    }

    public static PlaytimeFilter all() {
        return new PlaytimeFilter(null, null);
    }

    /**
     * Key of a span's version, server, or player: the value, or {@link ChatQuery#NULL} when the log names none.
     */
    public static String key(String value) {
        return value == null || value.isBlank() ? ChatQuery.NULL : value;
    }

    public boolean allows(PlaytimeSpan span) {
        if (versions != null && !versions.contains(key(span.version()))) return false;
        return servers == null || servers.contains(key(span.server()));
    }

    public boolean isNarrowed() {
        return versions != null || servers != null;
    }

    public PlaytimeFilter withVersions(Set<String> versions) {
        return new PlaytimeFilter(versions, servers);
    }

    public PlaytimeFilter withServers(Set<String> servers) {
        return new PlaytimeFilter(versions, servers);
    }

    /**
     * Checks or unchecks one value. Selecting every known value returns to "all" (a null set).
     */
    public PlaytimeFilter toggleVersion(String version, boolean selected, List<String> all) {
        return new PlaytimeFilter(select(versions, List.of(version), selected, all), servers);
    }

    public PlaytimeFilter toggleServer(String server, boolean selected, List<String> all) {
        return new PlaytimeFilter(versions, select(servers, List.of(server), selected, all));
    }

    /**
     * {@code current} with {@code values} checked or unchecked. Every value of {@code all} checked is
     * "all" (null); none checked is an empty set.
     */
    public static Set<String> select(Set<String> current, Collection<String> values, boolean selected,
                                     List<String> all) {
        Set<String> next = current == null ? new LinkedHashSet<>(all) : new LinkedHashSet<>(current);
        if (selected) next.addAll(values);
        else next.removeAll(values);
        if (all != null && next.containsAll(all)) return null;
        return Set.copyOf(next);
    }

    private static Set<String> copy(Set<String> values) {
        return values == null ? null : Set.copyOf(values);
    }
}
