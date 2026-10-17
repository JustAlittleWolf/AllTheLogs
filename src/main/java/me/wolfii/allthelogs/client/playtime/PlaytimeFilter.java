package me.wolfii.allthelogs.client.playtime;

import me.wolfii.allthelogs.data.playtime.PlaytimeSpan;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Versions and servers to keep. A null set means every value; an empty set means none.
 * Unknown servers use {@link #UNKNOWN_SERVER}.
 */
public record PlaytimeFilter(Set<String> versions, Set<String> servers) {
    /**
     * Sentinel stored in {@link #servers} for a span that never names a place. Not a legal server address.
     */
    public static final String UNKNOWN_SERVER = "\u0000";
    public static final String UNKNOWN_PLAYER = "\u0000";

    public PlaytimeFilter {
        versions = copy(versions);
        servers = copy(servers);
    }

    public static PlaytimeFilter all() {
        return new PlaytimeFilter(null, null);
    }

    /**
     * Player key of a span: the name, or {@link #UNKNOWN_PLAYER} when the log names none.
     */
    public static String playerKey(String player) {
        return player == null || player.isBlank() ? UNKNOWN_PLAYER : player;
    }

    public static String serverKey(String server) {
        return server == null || server.isBlank() ? UNKNOWN_SERVER : server;
    }

    public boolean allows(PlaytimeSpan span) {
        if (versions != null && !versions.contains(span.version())) return false;
        return servers == null || servers.contains(serverKey(span.server()));
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
