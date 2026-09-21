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
    /**
     * Same prefix the hand parser recognises. Kept for tests and callers that already hold a
     * {@link Matcher}; import uses {@link #match(String)} so every line is not a regex scan.
     */
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
     * A {@link #LINE_START} match: the prefix length and the captured clock, even when the clock
     * is not a valid {@link LocalTime}.
     *
     * @param dateRaw  group 1 of {@link #LINE_START}, or {@code null}
     * @param hour     captured hour
     * @param minute   captured minute
     * @param second   captured second
     * @param end      index after {@code "] "}
     */
    public record Prefix(String dateRaw, int hour, int minute, int second, int end) {
        /**
         * @return the stamp, or {@code null} if the captured clock is not a valid {@link LocalTime}
         */
        public Stamp stamp() {
            if (hour > 23 || minute > 59 || second > 59) return null;
            return new Stamp(parseDate(dateRaw), LocalTime.of(hour, minute, second));
        }
    }

    /**
     * Hand-parses the same prefix {@link #LINE_START} matches, without compiling a matcher.
     *
     * @return the prefix, or {@code null} when the line does not start with a timestamp
     */
    public static Prefix match(String line) {
        if (line == null) return null;
        int length = line.length();
        if (length < 10 || line.charAt(0) != '[') return null;
        int i = 1;
        String dateRaw = null;
        int dateSep = dateSeparator(line, i, length);
        if (dateSep >= 0) {
            dateRaw = line.substring(i, dateSep);
            i = dateSep + 1;
        }
        int hourEnd = digits(line, i, length, 1, 2);
        if (hourEnd < 0 || hourEnd >= length || line.charAt(hourEnd) != ':') return null;
        int hour = value(line, i, hourEnd);
        i = hourEnd + 1;
        int minuteEnd = digits(line, i, length, 2, 2);
        if (minuteEnd < 0 || minuteEnd >= length || line.charAt(minuteEnd) != ':') return null;
        int minute = value(line, i, minuteEnd);
        i = minuteEnd + 1;
        int secondEnd = digits(line, i, length, 2, 2);
        if (secondEnd < 0) return null;
        int second = value(line, i, secondEnd);
        i = secondEnd;
        if (i < length) {
            char fraction = line.charAt(i);
            if (fraction == '.' || fraction == ',') {
                int fracEnd = digits(line, i + 1, length, 1, Integer.MAX_VALUE);
                if (fracEnd < 0) return null;
                i = fracEnd;
            }
        }
        if (i + 2 > length || line.charAt(i) != ']' || line.charAt(i + 1) != ' ') return null;
        return new Prefix(dateRaw, hour, minute, second, i + 2);
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

    /**
     * @return index of the {@code ' '} or {@code 'T'} after a date, or {@code -1}
     */
    private static int dateSeparator(String line, int i, int length) {
        if (i + 11 <= length && isIsoDate(line, i)) {
            char sep = line.charAt(i + 10);
            if (sep == ' ' || sep == 'T') return i + 10;
        }
        int digits = 0;
        int j = i;
        while (j < length && digits < 2 && isDigit(line.charAt(j))) {
            j++;
            digits++;
        }
        if (digits == 0 || j + 8 > length) return -1;
        if (!isLetter(line.charAt(j)) || !isLetter(line.charAt(j + 1)) || !isLetter(line.charAt(j + 2))) {
            return -1;
        }
        int year = j + 3;
        if (!isDigit(line.charAt(year)) || !isDigit(line.charAt(year + 1))
            || !isDigit(line.charAt(year + 2)) || !isDigit(line.charAt(year + 3))) {
            return -1;
        }
        int sepAt = year + 4;
        if (sepAt >= length) return -1;
        char sep = line.charAt(sepAt);
        return sep == ' ' || sep == 'T' ? sepAt : -1;
    }

    private static boolean isIsoDate(String line, int i) {
        return isDigit(line.charAt(i)) && isDigit(line.charAt(i + 1))
            && isDigit(line.charAt(i + 2)) && isDigit(line.charAt(i + 3))
            && line.charAt(i + 4) == '-'
            && isDigit(line.charAt(i + 5)) && isDigit(line.charAt(i + 6))
            && line.charAt(i + 7) == '-'
            && isDigit(line.charAt(i + 8)) && isDigit(line.charAt(i + 9));
    }

    /**
     * @return index after {@code minCount}..{@code maxCount} digits, or {@code -1}
     */
    private static int digits(String line, int i, int length, int minCount, int maxCount) {
        int j = i;
        int limit = maxCount == Integer.MAX_VALUE ? length : Math.min(length, i + maxCount);
        while (j < limit && isDigit(line.charAt(j))) j++;
        int count = j - i;
        return count >= minCount ? j : -1;
    }

    private static int value(String line, int start, int end) {
        int n = 0;
        for (int i = start; i < end; i++) n = n * 10 + (line.charAt(i) - '0');
        return n;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }
}
