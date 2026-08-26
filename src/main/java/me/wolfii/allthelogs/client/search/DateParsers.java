package me.wolfii.allthelogs.client.search;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Parses the date fields on the filter panel. Empty input means an open bound.
 */
public final class DateParsers {
    private static final List<DateTimeFormatter> DATE_TIMES = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );

    private DateParsers() {
    }

    public static Optional<LocalDateTime> parse(String raw) {
        if (raw == null) return Optional.empty();
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) return Optional.empty();
        try {
            return Optional.of(LocalDate.parse(trimmed).atStartOfDay());
        } catch (DateTimeParseException ignored) {
        }
        for (DateTimeFormatter formatter : DATE_TIMES) {
            try {
                return Optional.of(LocalDateTime.parse(trimmed, formatter));
            } catch (DateTimeParseException ignored) {
            }
        }
        return Optional.empty();
    }

    public static boolean isBlankOrValid(String raw) {
        if (raw == null || raw.trim().isEmpty()) return true;
        return parse(raw).isPresent();
    }
}
