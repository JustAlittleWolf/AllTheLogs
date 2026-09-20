package me.wolfii.allthelogs.data.extract;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises the timestamp prefix of a Minecraft log line and turns it into a {@link LocalTime},
 * plus a {@link LocalDate} when the brackets include one.
 * <p>
 * Lines start with a bracketed clock, optionally prefixed by a date. Dates may be
 * {@code yyyy-MM-dd} or the unusual {@code dMMMyyyy} form used by some modpacks
 * ({@code [24Feb2026 16:08:07.204]}). Time-only prefixes ({@code [12:16:21]}) have a null date so
 * the file name / mtime still supplies the calendar day.
 */
public final class LogTimeExtractor {
    public static final Pattern LINE_START = Pattern.compile(
        "^\\[(?:(\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[A-Za-z]{3}\\d{4})[ T])?(\\d{1,2}):(\\d{2}):(\\d{2})(?:[.,]\\d+)?] ");
    private static final DateTimeFormatter DAY_MONTH_YEAR =
        DateTimeFormatter.ofPattern("dMMMyyyy", Locale.ENGLISH);

    private LogTimeExtractor() {
    }

    /**
     * Clock and optional calendar date captured from a log line prefix.
     *
     * @param date the embedded date, or {@code null} when the line only has a clock
     * @param time the clock
     */
    public record Stamp(LocalDate date, LocalTime time) {
    }

    /**
     * @param start a matcher that has already {@link Matcher#find()} against {@link #LINE_START}
     * @return the parsed stamp, or {@code null} if the captured clock is not a valid {@link LocalTime}
     */
    public static Stamp parse(Matcher start) {
        int hour = Integer.parseInt(start.group(2));
        int minute = Integer.parseInt(start.group(3));
        int second = Integer.parseInt(start.group(4));
        if (hour > 23 || minute > 59 || second > 59) return null;
        return new Stamp(parseDate(start.group(1)), LocalTime.of(hour, minute, second));
    }

    static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            if (raw.indexOf('-') >= 0) return LocalDate.parse(raw);
            return LocalDate.parse(raw, DAY_MONTH_YEAR);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
