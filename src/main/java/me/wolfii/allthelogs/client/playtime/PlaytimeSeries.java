package me.wolfii.allthelogs.client.playtime;

import me.wolfii.allthelogs.client.search.MinecraftVersions;
import me.wolfii.allthelogs.data.playtime.PlaytimeSpan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * One colored slice of a bar. The {@code pattern} is drawn over the color where bars are wide enough, so
 * series with similar colors still look different. Each key is null when the chart is not grouped by it:
 * {@code version}, {@code serverKey}, which is {@link PlaytimeFilter#UNKNOWN_SERVER} for no place, and
 * {@code playerKey}, which is {@link PlaytimeFilter#UNKNOWN_PLAYER} for no player.
 */
public record PlaytimeSeries(String version, String serverKey, String playerKey, int color, PlaytimePattern pattern) {
    /** Longest name shown in hover cards before it is cut with an ellipsis. */
    public static final int MAX_LABEL_LENGTH = 32;
    public static final Comparator<PlaytimeSeries> STACK_ORDER = (left, right) -> {
        int versionOrder = compareVersion(left.version(), right.version());
        if (versionOrder != 0) return versionOrder;
        int serverOrder = compareName(left.serverKey(), right.serverKey(), PlaytimeFilter.UNKNOWN_SERVER);
        if (serverOrder != 0) return serverOrder;
        return compareName(left.playerKey(), right.playerKey(), PlaytimeFilter.UNKNOWN_PLAYER);
    };
    private static final Pattern FORMATTING = Pattern.compile("§.?");

    /**
     * Uncolored series for {@code breakdown}. Used as a lookup key; {@link #collect} assigns the colors.
     */
    public static PlaytimeSeries of(PlaytimeBreakdown breakdown, String version, String server, String player) {
        return new PlaytimeSeries(
            breakdown.byVersion() ? version : null,
            breakdown.byServer() ? PlaytimeFilter.serverKey(server) : null,
            breakdown.byPlayer() ? PlaytimeFilter.playerKey(player) : null,
            0, PlaytimePattern.SOLID);
    }

    public static PlaytimeSeries of(PlaytimeBreakdown breakdown, String version, String server) {
        return of(breakdown, version, server, null);
    }

    public static PlaytimeSeries of(PlaytimeBreakdown breakdown, PlaytimeSpan span) {
        return of(breakdown, span.version(), span.server(), span.player());
    }

    /**
     * Series found in {@code kept}, in stack order, colored by their first appearance in {@code all}.
     */
    public static Index collect(List<PlaytimeSpan> kept, List<PlaytimeSpan> all, PlaytimeBreakdown breakdown) {
        Map<PlaytimeSeries, Boolean> found = new LinkedHashMap<>();
        for (PlaytimeSpan span : kept) found.put(of(breakdown, span), Boolean.TRUE);
        Map<PlaytimeSeries, Integer> ranks = ranks(all == null ? kept : all, breakdown);
        List<PlaytimeSeries> keys = new ArrayList<>(found.keySet());
        keys.sort(STACK_ORDER);
        List<PlaytimeSeries> series = new ArrayList<>(keys.size());
        Map<PlaytimeSeries, Integer> index = new HashMap<>();
        for (PlaytimeSeries key : keys) {
            index.put(key, series.size());
            int rank = ranks.getOrDefault(key, -1);
            series.add(new PlaytimeSeries(key.version, key.serverKey, key.playerKey, key.colorFor(rank),
                rank >= 0 ? PlaytimeColors.pattern(rank) : PlaytimePattern.SOLID));
        }
        return new Index(List.copyOf(series), index, breakdown);
    }

    /**
     * The grouped names joined with " - ", version first, then server, then player.
     *
     * @param unknown shown for a version, server, or player that is not known
     */
    public String label(String unknown) {
        List<String> parts = new ArrayList<>(3);
        if (version != null) parts.add(isUnknown(version) ? unknown : version);
        if (serverKey != null) parts.add(nameLabel(serverKey, PlaytimeFilter.UNKNOWN_SERVER, unknown));
        if (playerKey != null) parts.add(nameLabel(playerKey, PlaytimeFilter.UNKNOWN_PLAYER, unknown));
        return String.join(" - ", parts);
    }

    /**
     * {@link #label} cut to {@link #MAX_LABEL_LENGTH} characters.
     */
    public String shortLabel(String unknown) {
        return shorten(label(unknown), MAX_LABEL_LENGTH);
    }

    public boolean canFilterVersion() {
        return version != null && !version.isBlank();
    }

    public boolean canFilterServer() {
        return serverKey != null && !serverKey.equals(PlaytimeFilter.UNKNOWN_SERVER);
    }

    /**
     * Server or world name without Minecraft {@code §} formatting codes.
     */
    public static String displayName(String name) {
        return name == null ? "" : FORMATTING.matcher(name).replaceAll("");
    }

    public static String shorten(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) return text;
        return text.substring(0, Math.max(0, maxLength - 1)).stripTrailing() + "…";
    }

    /**
     * Gray when nothing is known, a desaturated color when only part of a combined series is known.
     */
    private int colorFor(int rank) {
        if (rank < 0) return PlaytimeColors.UNKNOWN;
        boolean partlyUnknown = (version != null && isUnknown(version))
            || (serverKey != null && !canFilterServer())
            || (playerKey != null && !knowsPlayer());
        return partlyUnknown ? PlaytimeColors.ofRankMuted(rank) : PlaytimeColors.ofRank(rank);
    }

    private boolean knowsPlayer() {
        return playerKey != null && !playerKey.equals(PlaytimeFilter.UNKNOWN_PLAYER);
    }

    private static String nameLabel(String key, String unknownKey, String unknown) {
        return key.equals(unknownKey) ? unknown : displayName(key);
    }

    private static Map<PlaytimeSeries, Integer> ranks(List<PlaytimeSpan> spans, PlaytimeBreakdown breakdown) {
        List<PlaytimeSpan> byStart = new ArrayList<>(spans);
        byStart.sort(Comparator.comparing(PlaytimeSpan::start));
        Map<PlaytimeSeries, Integer> ranks = new HashMap<>();
        for (PlaytimeSpan span : byStart) {
            PlaytimeSeries key = of(breakdown, span);
            if (!key.canFilterServer() && !key.knowsPlayer() && (key.version == null || isUnknown(key.version))) {
                continue;
            }
            ranks.putIfAbsent(key, ranks.size());
        }
        return ranks;
    }

    private static int compareVersion(String left, String right) {
        if (left == null && right == null) return 0;
        if (left == null) return -1;
        if (right == null) return 1;
        boolean leftUnknown = isUnknown(left);
        boolean rightUnknown = isUnknown(right);
        if (leftUnknown != rightUnknown) return leftUnknown ? 1 : -1;
        return MinecraftVersions.newestFirstOrder().reversed().compare(left, right);
    }

    /**
     * Server or player names alphabetically, unknown last.
     */
    private static int compareName(String left, String right, String unknownKey) {
        boolean leftUnknown = left == null || left.equals(unknownKey);
        boolean rightUnknown = right == null || right.equals(unknownKey);
        if (leftUnknown != rightUnknown) return leftUnknown ? 1 : -1;
        if (leftUnknown) return 0;
        return left.compareToIgnoreCase(right);
    }

    private static boolean isUnknown(String version) {
        return MinecraftVersions.isUnknown(version);
    }

    /**
     * Colored series in stack order, and the position of the series each span belongs to.
     */
    public record Index(List<PlaytimeSeries> series, Map<PlaytimeSeries, Integer> positions,
                        PlaytimeBreakdown breakdown) {
        public int of(PlaytimeSpan span) {
            return positions.get(PlaytimeSeries.of(breakdown, span));
        }

        public int size() {
            return series.size();
        }
    }
}
