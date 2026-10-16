package me.wolfii.allthelogs.client.playtime;

/**
 * Compact durations for the playtime total, axis, and hover card.
 */
public final class PlaytimeDuration {
    private PlaytimeDuration() {
    }

    public static String format(long millis) {
        if (millis <= 0) return "0s";
        long seconds = millis / 1000;
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3_600;
        long minutes = (seconds % 3_600) / 60;
        long secs = seconds % 60;
        StringBuilder text = new StringBuilder();
        if (days > 0) append(text, days, "d");
        if (hours > 0) append(text, hours, "h");
        if (minutes > 0) append(text, minutes, "m");
        if (days == 0 && hours == 0 && (minutes == 0 || secs > 0)) append(text, secs, "s");
        if (text.isEmpty()) append(text, 0, "s");
        return text.toString();
    }

    /**
     * Shorter form for axis labels: {@code 2d 06:36} once days are reached, otherwise {@link #format}.
     */
    public static String axis(long millis) {
        long minutes = Math.max(0, millis) / 60_000;
        long days = minutes / 1_440;
        if (days == 0) return format(millis);
        return String.format("%dd %02d:%02d", days, (minutes % 1_440) / 60, minutes % 60);
    }

    private static void append(StringBuilder text, long value, String unit) {
        if (!text.isEmpty()) text.append(' ');
        text.append(value).append(unit);
    }
}
