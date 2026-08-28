package me.wolfii.allthelogs.client.files;

import java.time.*;
import java.util.*;

/**
 * IANA timezone list and daylight-saving helpers for the import form.
 */
public final class ImportTimezones {
    private static List<Choice> cached;

    private ImportTimezones() {
    }

    /**
     * Every listed IANA zone, labelled with its id and current UTC offset. Typing filters this list;
     * an empty query shows no suggestions.
     */
    public static List<Choice> choices() {
        if (cached == null) {
            cached = buildChoices(Instant.now());
        }
        return cached;
    }

    static List<Choice> buildChoices(Instant at) {
        Set<String> ids = new TreeSet<>(ZoneId.getAvailableZoneIds());
        ids.add("UTC");
        List<Choice> choices = new ArrayList<>(ids.size());
        for (String id : ids) {
            if (!isListedId(id)) continue;
            choices.add(Choice.of(ZoneId.of(id), at));
        }
        choices.sort(Comparator.comparing(choice -> choice.zone().getId()));
        return List.copyOf(choices);
    }

    /**
     * Choices whose id, label, city name, or offset contains {@code query}. Blank input returns none.
     */
    public static List<Choice> matching(String query) {
        return matching(query, choices());
    }

    static List<Choice> matching(String query, List<Choice> choices) {
        if (query == null || query.isBlank()) return List.of();
        String needle = normalize(query);
        return choices.stream()
            .filter(choice -> matches(choice, needle))
            .toList();
    }

    /**
     * Resolves typed text to a zone: an exact listed label or id, a single autocomplete match, a valid
     * IANA id, or the system default when blank.
     */
    public static Optional<ZoneId> parse(String text) {
        return parse(text, choices(), ZoneId.systemDefault());
    }

    static Optional<ZoneId> parse(String text, List<Choice> choices, ZoneId fallback) {
        if (text == null || text.isBlank()) return Optional.of(fallback);
        String trimmed = text.trim();
        String needle = normalize(trimmed);
        for (Choice choice : choices) {
            if (choice.names().stream().anyMatch(name -> needle.equals(normalize(name)))) {
                return Optional.of(choice.zone());
            }
        }
        List<Choice> matches = matching(trimmed, choices);
        if (matches.size() == 1) return Optional.of(matches.getFirst().zone());
        try {
            return Optional.of(ZoneId.of(trimmed));
        } catch (DateTimeException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Zone used when converting log timestamps. Daylight saving is applied from each log's date when
     * {@code applySummerTime} is true; otherwise the zone's standard (winter) offset is used for every
     * file.
     */
    public static ZoneId forImport(ZoneId zone, boolean applySummerTime) {
        return forImport(zone, applySummerTime, Instant.now());
    }

    static ZoneId forImport(ZoneId zone, boolean applySummerTime, Instant at) {
        Objects.requireNonNull(zone, "zone");
        if (applySummerTime) return zone;
        return zone.getRules().getStandardOffset(at);
    }

    /**
     * Whether {@code zone} uses a different offset in January and July of the year of {@code at}.
     */
    public static boolean observesDaylightSaving(ZoneId zone) {
        return observesDaylightSaving(zone, Instant.now());
    }

    static boolean observesDaylightSaving(ZoneId zone, Instant at) {
        OffsetPair pair = OffsetPair.of(zone, at);
        return pair.januarySeconds != pair.julySeconds;
    }

    public static boolean isDaylightSaving(ZoneId zone, Instant at) {
        return zone.getRules().isDaylightSavings(at);
    }

    public static String formatOffset(ZoneOffset offset) {
        if (offset == null || offset.getTotalSeconds() == 0) return "UTC";
        String id = offset.getId();
        if (id.startsWith("+") || id.startsWith("-")) return "UTC" + id;
        return "UTC" + offset;
    }

    static String compactOffset(ZoneOffset offset) {
        if (offset == null || offset.getTotalSeconds() == 0) return "UTC";
        int total = offset.getTotalSeconds();
        String sign = total >= 0 ? "+" : "-";
        int abs = Math.abs(total);
        int hours = abs / 3600;
        int minutes = (abs % 3600) / 60;
        if (minutes == 0) return "UTC" + sign + hours;
        return "UTC" + sign + hours + ":" + String.format("%02d", minutes);
    }

    public static String cityName(ZoneId zone) {
        if (zone instanceof ZoneOffset || "UTC".equalsIgnoreCase(zone.getId()) || "Z".equals(zone.getId())) {
            return "UTC";
        }
        String id = zone.getId();
        int slash = id.lastIndexOf('/');
        String city = slash < 0 ? id : id.substring(slash + 1);
        return city.replace('_', ' ');
    }

    private static boolean matches(Choice choice, String needle) {
        return choice.names().stream().anyMatch(name -> normalize(name).contains(needle));
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
    }

    private static boolean isListedId(String id) {
        if ("UTC".equals(id) || "GMT".equals(id)) return true;
        if (!id.contains("/")) return false;
        return !id.startsWith("Etc/") && !id.startsWith("SystemV/");
    }

    /**
     * @param label full IANA id and current UTC offset, for example {@code Europe/Vienna (UTC+02:00)}
     */
    public record Choice(ZoneId zone, ZoneOffset offset, String label, List<String> names) {
        public Choice {
            names = List.copyOf(names);
        }

        public static Choice of(ZoneId zone, Instant at) {
            ZoneOffset offset = zone.getRules().getOffset(at);
            String id = zone.getId();
            String label = id + " (" + formatOffset(offset) + ")";
            LinkedHashSet<String> names = new LinkedHashSet<>();
            names.add(id);
            names.add(label);
            names.add(formatOffset(offset));
            names.add(compactOffset(offset));
            names.add(cityName(zone));
            return new Choice(zone, offset, label, List.copyOf(names));
        }
    }

    private record OffsetPair(int januarySeconds, int julySeconds) {
        static OffsetPair of(ZoneId zone, Instant at) {
            int year = at.atZone(ZoneOffset.UTC).getYear();
            var rules = zone.getRules();
            int january = rules.getOffset(LocalDateTime.of(year, 1, 15, 12, 0).toInstant(ZoneOffset.UTC))
                .getTotalSeconds();
            int july = rules.getOffset(LocalDateTime.of(year, 7, 15, 12, 0).toInstant(ZoneOffset.UTC))
                .getTotalSeconds();
            return new OffsetPair(january, july);
        }
    }
}
