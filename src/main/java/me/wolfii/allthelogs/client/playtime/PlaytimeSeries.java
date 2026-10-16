package me.wolfii.allthelogs.client.playtime;

import me.wolfii.allthelogs.client.search.MinecraftVersions;
import me.wolfii.allthelogs.data.ChatLog;
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
 * series with similar colors still look different. {@code version} is null when the chart is grouped only by server.
 * {@code serverKey} is null when it is grouped only by version, and {@link PlaytimeFilter#UNKNOWN_SERVER}
 * when a combined or server chart has no place.
 */
public record PlaytimeSeries(String version, String serverKey, int color, PlaytimePattern pattern) {
    /** Longest name shown in hover cards before it is cut with an ellipsis. */
    public static final int MAX_LABEL_LENGTH = 32;
    public static final Comparator<PlaytimeSeries> STACK_ORDER = (left, right) -> {
        int versionOrder = compareVersion(left.version(), right.version());
        if (versionOrder != 0) return versionOrder;
        return compareServer(left.serverKey(), right.serverKey());
    };
    private static final Pattern FORMATTING = Pattern.compile("§.?");

    /**
     * Uncolored series for {@code breakdown}. Used as a lookup key; {@link #collect} assigns the colors.
     */
    public static PlaytimeSeries of(PlaytimeBreakdown breakdown, String version, String server) {
        String versionKey = breakdown == PlaytimeBreakdown.SERVER ? null : version;
        String place = breakdown == PlaytimeBreakdown.VERSION ? null : PlaytimeFilter.serverKey(server);
        return new PlaytimeSeries(versionKey, place, 0, PlaytimePattern.SOLID);
    }

    /**
     * Series found in {@code kept}, in stack order, colored by their first appearance in {@code all}.
     */
    public static Index collect(List<PlaytimeSpan> kept, List<PlaytimeSpan> all, PlaytimeBreakdown breakdown) {
        Map<PlaytimeSeries, Boolean> found = new LinkedHashMap<>();
        for (PlaytimeSpan span : kept) found.put(of(breakdown, span.version(), span.server()), Boolean.TRUE);
        Map<PlaytimeSeries, Integer> ranks = ranks(all == null ? kept : all, breakdown);
        List<PlaytimeSeries> keys = new ArrayList<>(found.keySet());
        keys.sort(STACK_ORDER);
        List<PlaytimeSeries> series = new ArrayList<>(keys.size());
        Map<PlaytimeSeries, Integer> index = new HashMap<>();
        for (PlaytimeSeries key : keys) {
            index.put(key, series.size());
            int rank = ranks.getOrDefault(key, -1);
            series.add(new PlaytimeSeries(key.version, key.serverKey, key.colorFor(rank),
                rank >= 0 ? PlaytimeColors.pattern(rank) : PlaytimePattern.SOLID));
        }
        return new Index(List.copyOf(series), index, breakdown);
    }

    public String label(String unknownServer) {
        if (version == null) return serverLabel(unknownServer);
        if (serverKey == null) return version;
        return version + " - " + serverLabel(unknownServer);
    }

    /**
     * {@link #label} cut to {@link #MAX_LABEL_LENGTH} characters.
     */
    public String shortLabel(String unknownServer) {
        return shorten(label(unknownServer), MAX_LABEL_LENGTH);
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
     * Gray when nothing is known, a desaturated color when only one half of a combined series is known.
     */
    private int colorFor(int rank) {
        if (rank < 0) return PlaytimeColors.UNKNOWN;
        boolean partlyUnknown = (version != null && isUnknown(version))
            || (serverKey != null && !canFilterServer());
        return partlyUnknown ? PlaytimeColors.ofRankMuted(rank) : PlaytimeColors.ofRank(rank);
    }

    private String serverLabel(String unknownServer) {
        if (serverKey == null || serverKey.equals(PlaytimeFilter.UNKNOWN_SERVER)) return unknownServer;
        return displayName(serverKey);
    }

    private static Map<PlaytimeSeries, Integer> ranks(List<PlaytimeSpan> spans, PlaytimeBreakdown breakdown) {
        List<PlaytimeSpan> byStart = new ArrayList<>(spans);
        byStart.sort(Comparator.comparing(PlaytimeSpan::start));
        Map<PlaytimeSeries, Integer> ranks = new HashMap<>();
        for (PlaytimeSpan span : byStart) {
            PlaytimeSeries key = of(breakdown, span.version(), span.server());
            if (!key.canFilterServer() && (key.version == null || isUnknown(key.version))) continue;
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

    private static int compareServer(String left, String right) {
        boolean leftUnknown = left == null || left.equals(PlaytimeFilter.UNKNOWN_SERVER);
        boolean rightUnknown = right == null || right.equals(PlaytimeFilter.UNKNOWN_SERVER);
        if (leftUnknown != rightUnknown) return leftUnknown ? 1 : -1;
        if (leftUnknown) return 0;
        return left.compareToIgnoreCase(right);
    }

    private static boolean isUnknown(String version) {
        return version.isBlank() || ChatLog.UNKNOWN_VERSION.equalsIgnoreCase(version);
    }

    /**
     * Colored series in stack order, and the position of the series each span belongs to.
     */
    public record Index(List<PlaytimeSeries> series, Map<PlaytimeSeries, Integer> positions,
                        PlaytimeBreakdown breakdown) {
        public int of(PlaytimeSpan span) {
            return positions.get(PlaytimeSeries.of(breakdown, span.version(), span.server()));
        }

        public int size() {
            return series.size();
        }
    }
}
