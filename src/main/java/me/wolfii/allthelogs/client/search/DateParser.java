package me.wolfii.allthelogs.client.search;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Parses the from/until fields on the filter overlay. Empty input means an open bound.
 * <p>
 * Date-only input is midnight at the start of that day for {@link #parse}, and midnight at the start of the
 * next day for {@link #parseUntil}, so an exclusive upper bound still includes the typed date.
 */
public final class DateParser {
    private static final List<DateTimeFormatter> DATE_TIMES = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );

    private DateParser() {
    }

    public static Optional<LocalDateTime> parse(String raw) {
        return parseDate(raw).map(LocalDate::atStartOfDay).or(() -> parseDateTime(raw));
    }

    /**
     * Upper bound for "no later than". A date with no time means the whole of that day, stored as the exclusive
     * midnight that starts the following day. A date and time is used as-is.
     */
    public static Optional<LocalDateTime> parseUntil(String raw) {
        return parseDate(raw).map(date -> date.plusDays(1).atStartOfDay()).or(() -> parseDateTime(raw));
    }

    public static String format(LocalDateTime time) {
        return time == null ? "" : time.toString().replace('T', ' ');
    }

    /**
     * Inverse of {@link #parseUntil} for exclusive midnight bounds, so the overlay shows the inclusive date.
     */
    public static String formatUntil(LocalDateTime time) {
        if (time == null) return "";
        if (time.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            return time.toLocalDate().minusDays(1).toString();
        }
        return format(time);
    }

    public static boolean isBlankOrValid(String raw) {
        return raw == null || raw.trim().isEmpty() || parse(raw).isPresent();
    }

    private static Optional<LocalDate> parseDate(String raw) {
        String trimmed = trimmedOrNull(raw);
        if (trimmed == null) return Optional.empty();
        try {
            return Optional.of(LocalDate.parse(trimmed));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<LocalDateTime> parseDateTime(String raw) {
        String trimmed = trimmedOrNull(raw);
        if (trimmed == null) return Optional.empty();
        for (DateTimeFormatter formatter : DATE_TIMES) {
            try {
                return Optional.of(LocalDateTime.parse(trimmed, formatter));
            } catch (DateTimeParseException ignored) {
            }
        }
        return Optional.empty();
    }

    private static String trimmedOrNull(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
