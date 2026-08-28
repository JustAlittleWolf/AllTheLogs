package me.wolfii.allthelogs.client.files;

import java.time.*;
import java.util.*;

/**
 * Curated import-timezone list: one well-known city per distinct offset pair (winter and summer),
 * plus parsing and daylight-saving helpers for the import form.
 */
public final class ImportTimezones {
    /**
     * Cities preferred as the label for a zone group; earlier entries win when several IANA ids share
     * the same winter/summer offsets.
     */
    private static final List<String> PREFERRED_CITIES = List.of(
        "UTC",
        "London", "Dublin", "Lisbon",
        "Berlin", "Paris", "Rome", "Madrid", "Amsterdam", "Vienna", "Warsaw",
        "Athens", "Helsinki", "Bucharest", "Cairo", "Johannesburg",
        "Moscow", "Istanbul",
        "Dubai",
        "Karachi",
        "Kolkata",
        "Dhaka",
        "Bangkok", "Jakarta",
        "Shanghai", "Singapore", "Hong_Kong", "Perth",
        "Tokyo", "Seoul",
        "Adelaide",
        "Sydney", "Melbourne",
        "Auckland",
        "Honolulu",
        "Anchorage",
        "Los_Angeles", "Vancouver",
        "Denver", "Phoenix",
        "Chicago", "Mexico_City",
        "New_York", "Toronto",
        "Halifax",
        "Sao_Paulo", "Buenos_Aires",
        "St_Johns",
        "Azores"
    );

    private ImportTimezones() {
    }

    /**
     * One representative location per distinct winter/summer offset pair, labelled with the current
     * UTC offset. The JVM default zone is preferred as the representative of its group.
     */
    private static List<Choice> cached;

    public static List<Choice> important() {
        if (cached == null) {
            cached = important(ZoneId.systemDefault(), Instant.now());
        }
        return cached;
    }

    static List<Choice> important(ZoneId preferred, Instant at) {
        Map<OffsetPair, List<ZoneId>> groups = new LinkedHashMap<>();
        for (String id : ZoneId.getAvailableZoneIds()) {
            if (!isRepresentativeId(id)) continue;
            ZoneId zone = ZoneId.of(id);
            groups.computeIfAbsent(OffsetPair.of(zone, at), ignored -> new ArrayList<>()).add(zone);
        }
        groups.computeIfAbsent(OffsetPair.of(ZoneOffset.UTC, at), ignored -> new ArrayList<>())
            .add(ZoneOffset.UTC);

        List<Choice> choices = new ArrayList<>();
        Set<OffsetPair> seen = new HashSet<>();
        for (Map.Entry<OffsetPair, List<ZoneId>> entry : groups.entrySet()) {
            if (!seen.add(entry.getKey())) continue;
            ZoneId representative = pickRepresentative(entry.getValue(), preferred);
            choices.add(Choice.of(representative, at, entry.getValue()));
        }
        choices.sort(Comparator
            .comparingInt((Choice choice) -> choice.offset().getTotalSeconds())
            .thenComparing(choice -> choice.city().toLowerCase(Locale.ROOT)));
        return List.copyOf(choices);
    }

    /**
     * Choices whose city, IANA id, or offset label contains {@code query}. An empty query returns
     * {@link #important()}.
     */
    public static List<Choice> matching(String query) {
        return matching(query, important());
    }

    static List<Choice> matching(String query, List<Choice> choices) {
        if (query == null || query.isBlank()) return choices;
        String needle = normalize(query);
        return choices.stream()
            .filter(choice -> matches(choice, needle))
            .toList();
    }

    /**
     * Resolves typed text to a zone: a listed city or label, then an IANA id, then the system default
     * when blank.
     */
    public static Optional<ZoneId> parse(String text) {
        return parse(text, important(), ZoneId.systemDefault());
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

    private static boolean isRepresentativeId(String id) {
        if ("UTC".equals(id) || "GMT".equals(id)) return true;
        if (!id.contains("/")) return false;
        if (id.startsWith("Etc/") || id.startsWith("SystemV/") || id.startsWith("US/")
            || id.startsWith("Canada/") || id.startsWith("Brazil/") || id.startsWith("Chile/")
            || id.startsWith("Mexico/") || id.startsWith("Antarctica/") || id.startsWith("Arctic/")
            || id.startsWith("Pacific/Johnston")) {
            return false;
        }
        return true;
    }

    private static ZoneId pickRepresentative(List<ZoneId> zones, ZoneId preferred) {
        for (ZoneId zone : zones) {
            if (zone.getId().equals(preferred.getId())) return zone;
        }
        ZoneId best = zones.getFirst();
        int bestRank = rank(best);
        for (ZoneId zone : zones) {
            int rank = rank(zone);
            if (rank < bestRank || (rank == bestRank && zone.getId().length() < best.getId().length())) {
                best = zone;
                bestRank = rank;
            }
        }
        return best;
    }

    private static int rank(ZoneId zone) {
        String city = zone.getId();
        int slash = city.lastIndexOf('/');
        if (slash >= 0) city = city.substring(slash + 1);
        if ("UTC".equals(zone.getId()) || zone instanceof ZoneOffset) {
            return -1;
        }
        int index = PREFERRED_CITIES.indexOf(city);
        return index < 0 ? PREFERRED_CITIES.size() + city.length() : index;
    }

    /**
     * @param offset the zone's offset at the instant the choice was built, used in {@link #label()}
     * @param names  city names and IANA ids in the same offset group, used for autocomplete
     */
    public record Choice(ZoneId zone, String city, ZoneOffset offset, String label, List<String> names) {
        public Choice {
            names = List.copyOf(names);
        }

        public static Choice of(ZoneId zone, Instant at) {
            return of(zone, at, List.of(zone));
        }

        public static Choice of(ZoneId zone, Instant at, List<ZoneId> group) {
            ZoneOffset offset = zone.getRules().getOffset(at);
            String city = cityName(zone);
            String label = city + " (" + formatOffset(offset) + ")";
            LinkedHashSet<String> names = new LinkedHashSet<>();
            names.add(city);
            names.add(zone.getId());
            names.add(label);
            names.add(formatOffset(offset));
            names.add(compactOffset(offset));
            for (ZoneId member : group) {
                names.add(cityName(member));
                names.add(member.getId());
            }
            return new Choice(zone, city, offset, label, List.copyOf(names));
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
