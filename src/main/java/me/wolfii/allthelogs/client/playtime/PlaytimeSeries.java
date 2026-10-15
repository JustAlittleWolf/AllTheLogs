package me.wolfii.allthelogs.client.playtime;

import me.wolfii.allthelogs.client.search.MinecraftVersions;
import me.wolfii.allthelogs.data.ChatLog;

import java.util.Comparator;

/**
 * One colored slice of a bar. {@code version} is null when the chart is grouped only by server.
 * {@code serverKey} is null when it is grouped only by version, and {@link PlaytimeFilter#UNKNOWN_SERVER}
 * when a combined or server chart has no place.
 */
public record PlaytimeSeries(String version, String serverKey, int color) {
    public static final Comparator<PlaytimeSeries> STACK_ORDER = (left, right) -> {
        int versionOrder = compareVersion(left.version(), right.version());
        if (versionOrder != 0) return versionOrder;
        return compareServer(left.serverKey(), right.serverKey());
    };

    public static PlaytimeSeries of(PlaytimeBreakdown breakdown, String version, String server) {
        String versionKey = breakdown == PlaytimeBreakdown.SERVER ? null : version;
        String place = breakdown == PlaytimeBreakdown.VERSION ? null : PlaytimeFilter.serverKey(server);
        return new PlaytimeSeries(versionKey, place, PlaytimeColors.of(versionKey, place));
    }

    public String label(String unknownServer) {
        if (version == null) return serverLabel(unknownServer);
        if (serverKey == null) return version;
        return version + " - " + serverLabel(unknownServer);
    }

    public boolean canFilterVersion() {
        return version != null && !version.isBlank();
    }

    public boolean canFilterServer() {
        return serverKey != null && !serverKey.equals(PlaytimeFilter.UNKNOWN_SERVER);
    }

    private String serverLabel(String unknownServer) {
        if (serverKey == null || serverKey.equals(PlaytimeFilter.UNKNOWN_SERVER)) return unknownServer;
        return serverKey;
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
}
