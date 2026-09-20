package me.wolfii.allthelogs.data.extract;

import java.time.LocalTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises the timestamp prefix of a Minecraft log line and turns it into a {@link LocalTime}.
 * <p>
 * Lines start with a bracketed clock, optionally prefixed by a date. Dates may be
 * {@code yyyy-MM-dd} or the unusual {@code dMMMyyyy} form used by some modpacks
 * ({@code [24Feb2026 16:08:07.204]}).
 */
public final class LogTimeExtractor {
    public static final Pattern LINE_START = Pattern.compile(
        "^\\[(?:(?:\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[A-Za-z]{3}\\d{4})[ T])?(\\d{1,2}):(\\d{2}):(\\d{2})(?:[.,]\\d+)?] ");

    private LogTimeExtractor() {
    }

    /**
     * @param start a matcher that has already {@link Matcher#find()} against {@link #LINE_START}
     * @return the parsed time, or {@code null} if the captured clock is not a valid {@link LocalTime}
     */
    public static LocalTime parse(Matcher start) {
        int hour = Integer.parseInt(start.group(1));
        int minute = Integer.parseInt(start.group(2));
        int second = Integer.parseInt(start.group(3));
        if (hour > 23 || minute > 59 || second > 59) return null;
        return LocalTime.of(hour, minute, second);
    }
}
