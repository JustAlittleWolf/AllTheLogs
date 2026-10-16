package me.wolfii.allthelogs.client.playtime;

/**
 * Compact durations for the playtime total, legend, axis, and hover card.
 */
public final class PlaytimeDuration {
    /** From this many days on, {@link #summary} leaves out the hours and minutes. */
    static final int SUMMARY_DAYS_ONLY_FROM = 5;
    private static final long DAY_MILLIS = 86_400_000L;

    private PlaytimeDuration() {
    }

    /**
     * Full duration in days, hours, and minutes, or seconds below a minute. Used where one value is looked at
     * closely, such as the hover card.
     */
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
     * Duration for overviews such as the total and the legend: {@link #format} below
     * {@link #SUMMARY_DAYS_ONLY_FROM} days, and from there only the days, rounded, since the hours no longer
     * matter.
     */
    public static String summary(long millis) {
        if (millis < SUMMARY_DAYS_ONLY_FROM * DAY_MILLIS) return format(millis);
        return Math.round((double) millis / DAY_MILLIS) + "d";
    }

    /**
     * Axis label: {@link #format} below a day. From a day on, the days on the first line and the hours and
     * minutes on a second line, separated by {@code \n}.
     */
    public static String axis(long millis) {
        long minutes = Math.max(0, millis) / 60_000;
        long days = minutes / 1_440;
        if (days == 0) return format(millis);
        long rest = minutes % 1_440;
        if (rest == 0) return days + "d";
        return days + "d\n" + format(rest * 60_000);
    }

    private static void append(StringBuilder text, long value, String unit) {
        if (!text.isEmpty()) text.append(' ');
        text.append(value).append(unit);
    }
}
