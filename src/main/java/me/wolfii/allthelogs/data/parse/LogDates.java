package me.wolfii.allthelogs.data.parse;

import java.time.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves a log file's calendar date and converts naive log timestamps into the JVM's local zone.
 */
public final class LogDates {
    private static final Pattern DATE_IN_NAME = Pattern.compile("(\\d{4})[-_]?(\\d{2})[-_]?(\\d{2})");

    private LogDates() {
    }

    /**
     * Resolves the date from the file name, falling back to the last modification time interpreted in
     * {@code timezone}.
     *
     * @param lastModified may be {@code null} when the source reports no modification time; the current date in
     *                     {@code timezone} is used then
     */
    public static LocalDate resolve(String fileName, Instant lastModified, ZoneId timezone) {
        LocalDate fromName = fromFileName(fileName);
        if (fromName != null) return fromName;
        return lastModified == null
            ? LocalDate.now(timezone)
            : lastModified.atZone(timezone).toLocalDate();
    }

    /**
     * Combines a log's calendar date with a line time and converts the result from {@code sourceZone} to the JVM
     * default timezone.
     */
    public static LocalDateTime toSystemLocal(LocalDate date, LocalTime time, ZoneId sourceZone) {
        if (date == null || time == null) return null;
        return toSystemLocal(LocalDateTime.of(date, time), sourceZone);
    }

    /**
     * Converts a naive timestamp from {@code sourceZone} to the JVM default timezone.
     * Passing the default timezone leaves the value unchanged, including during DST gaps where a round-trip through
     * that zone would shift the clock.
     */
    public static LocalDateTime toSystemLocal(LocalDateTime dateTime, ZoneId sourceZone) {
        if (dateTime == null) return null;
        ZoneId local = ZoneId.systemDefault();
        if (sourceZone.equals(local)) return dateTime;
        return dateTime.atZone(sourceZone).withZoneSameInstant(local).toLocalDateTime();
    }

    /**
     * @return the date encoded in the file name, or {@code null} if it carries none
     */
    public static LocalDate fromFileName(String fileName) {
        Matcher matcher = DATE_IN_NAME.matcher(fileName);
        while (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            int month = Integer.parseInt(matcher.group(2));
            int day = Integer.parseInt(matcher.group(3));
            if (year < 2009 || month < 1 || month > 12 || day < 1 || day > 31) continue;
            try {
                return LocalDate.of(year, month, day);
            } catch (java.time.DateTimeException ignored) {
            }
        }
        return null;
    }
}
