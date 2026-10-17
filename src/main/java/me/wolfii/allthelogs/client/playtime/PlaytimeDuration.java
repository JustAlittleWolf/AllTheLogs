package me.wolfii.allthelogs.client.playtime;

/**
 * Compact durations for the playtime total, legend, axis, and hover card.
 */
public final class PlaytimeDuration {
    /** From this many days on, the total and the value axis leave out the hours and minutes. */
    static final int DAYS_ONLY_FROM = 3;
    private static final long DAY_MILLIS = 86_400_000L;

    private PlaytimeDuration() {
    }

    /**
     * Full duration in days, hours, and minutes, or seconds below a minute. Used where one value is looked at
     * closely, such as the legend and the hover card.
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
     * Duration for the total above the legend: {@link #format} below {@link #DAYS_ONLY_FROM} days, and from
     * there only the days, rounded, since the hours no longer matter.
     */
    public static String summary(long millis) {
        if (millis < DAYS_ONLY_FROM * DAY_MILLIS) return format(millis);
        return daysOnly(millis);
    }

    /**
     * Value-axis label. From {@link #DAYS_ONLY_FROM} days, only the days, rounded. Below that, {@link #format}
     * under a day, and from a day the days on the first line and the hours and minutes on a second line,
     * separated by {@code \n}.
     */
    public static String axis(long millis) {
        if (millis >= DAYS_ONLY_FROM * DAY_MILLIS) return daysOnly(millis);
        long minutes = Math.max(0, millis) / 60_000;
        long days = minutes / 1_440;
        if (days == 0) return format(millis);
        long rest = minutes % 1_440;
        if (rest == 0) return days + "d";
        return days + "d\n" + format(rest * 60_000);
    }

    private static String daysOnly(long millis) {
        return Math.round((double) millis / DAY_MILLIS) + "d";
    }

    private static void append(StringBuilder text, long value, String unit) {
        if (!text.isEmpty()) text.append(' ');
        text.append(value).append(unit);
    }
}
