package me.wolfii.allthelogs.data.internal;

import me.wolfii.allthelogs.data.DateSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Determines which calendar date a log file belongs to.
public final class LogDates {
    /// Matches the `yyyy-MM-dd` prefix that the vanilla client uses for rolled log files, and the `yyyy_MM_dd` and
    /// `yyyyMMdd` spellings used by some launchers.
    private static final Pattern DATE_IN_NAME = Pattern.compile("(\\d{4})[-_]?(\\d{2})[-_]?(\\d{2})");

    private LogDates() {
    }

    /// @param date   the resolved date
    /// @param source how it was resolved
    public record Resolved(LocalDate date, DateSource source) {
    }

    /// Resolves the date from the file name, falling back to the last modification time.
    ///
    /// @param lastModified may be `null` when the source reports no modification time; the current date is used then
    public static Resolved resolve(String fileName, LocalDateTime lastModified) {
        LocalDate fromName = fromFileName(fileName);
        if (fromName != null) return new Resolved(fromName, DateSource.FILE_NAME);
        LocalDate fallback = lastModified == null ? LocalDate.now() : lastModified.toLocalDate();
        return new Resolved(fallback, DateSource.LAST_MODIFIED);
    }

    /// @return the date encoded in the file name, or `null` if it carries none
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
                // A number that looks like a date but is not, e.g. February 30th; keep looking.
            }
        }
        return null;
    }
}
