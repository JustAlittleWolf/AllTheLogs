package me.wolfii.allthelogs.client.search;

import me.wolfii.allthelogs.data.ChatLog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Orders Minecraft version strings newest-first for the filter menu. Numeric segments follow semantic versioning;
 * letter suffixes such as {@code pre} or {@code rc} count as older than the same numbers with no suffix.
 * {@link ChatLog#UNKNOWN_VERSION} is always last.
 */
public final class MinecraftVersions {
    private static final Pattern TOKEN = Pattern.compile("\\d+|[A-Za-z]+");

    private MinecraftVersions() {
    }

    public static List<String> newestFirst(List<String> versions) {
        if (versions == null || versions.isEmpty()) return List.of();
        List<String> sorted = new ArrayList<>(versions);
        sorted.sort(newestFirstOrder());
        return List.copyOf(sorted);
    }

    public static Comparator<String> newestFirstOrder() {
        return (left, right) -> {
            if (isUnknown(left) != isUnknown(right)) {
                return isUnknown(left) ? 1 : -1;
            }
            int oldestFirst = compareReleaseOldestFirst(left, right);
            if (oldestFirst != 0) return -oldestFirst;
            return String.CASE_INSENSITIVE_ORDER.compare(left, right);
        };
    }

    static boolean isUnknown(String version) {
        return version == null || version.isBlank() || ChatLog.UNKNOWN_VERSION.equalsIgnoreCase(version);
    }

    /**
     * Negative when {@code left} is an older release than {@code right}. Unknown versions are not compared here.
     */
    static int compareReleaseOldestFirst(String left, String right) {
        List<String> a = tokens(left);
        List<String> b = tokens(right);
        int n = Math.max(a.size(), b.size());
        for (int i = 0; i < n; i++) {
            boolean aMissing = i >= a.size();
            boolean bMissing = i >= b.size();
            if (aMissing && bMissing) break;
            if (aMissing) return trailingIsPreRelease(b.get(i)) ? 1 : -1;
            if (bMissing) return trailingIsPreRelease(a.get(i)) ? -1 : 1;
            int cmp = compareToken(a.get(i), b.get(i));
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    private static List<String> tokens(String version) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(version == null ? "" : version);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private static int compareToken(String left, String right) {
        Long a = parseNumber(left);
        Long b = parseNumber(right);
        if (a != null && b != null) return Long.compare(a, b);
        if (a != null) return 1;
        if (b != null) return -1;
        return left.compareToIgnoreCase(right);
    }

    private static boolean trailingIsPreRelease(String token) {
        return parseNumber(token) == null;
    }

    private static Long parseNumber(String token) {
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
