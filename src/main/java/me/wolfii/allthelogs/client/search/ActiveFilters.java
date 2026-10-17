package me.wolfii.allthelogs.client.search;

import me.wolfii.allthelogs.api.ChatQuery;
import me.wolfii.allthelogs.client.playtime.PlaytimeSeries;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sidebar filters that differ from {@link SearchFilter#defaults()}, in sidebar order. The search text
 * is left out: it stays visible in the search box. Regex mode is included.
 */
public final class ActiveFilters {
    public enum Kind {
        REGEX, CASE_SENSITIVE, CONTEXT, FROM, UNTIL, VERSION, SERVER
    }

    /**
     * @param detail display value for fields that have one, or {@code null} for a checkbox
     */
    public record Entry(Kind kind, String detail) {
    }

    private ActiveFilters() {
    }

    public static List<Entry> of(SearchFilter filter) {
        List<Entry> active = new ArrayList<>();
        if (filter == null) return active;
        if (filter.regex()) active.add(new Entry(Kind.REGEX, null));
        if (filter.caseSensitive()) active.add(new Entry(Kind.CASE_SENSITIVE, null));
        if (filter.contextLines() != SearchFilter.DEFAULT_CONTEXT_LINES) {
            active.add(new Entry(Kind.CONTEXT, Integer.toString(filter.contextLines())));
        }
        if (filter.startingAt() != null) {
            active.add(new Entry(Kind.FROM, DateParser.format(filter.startingAt())));
        }
        if (filter.upUntil() != null) {
            active.add(new Entry(Kind.UNTIL, DateParser.formatUntil(filter.upUntil())));
        }
        if (filter.hasVersion()) active.add(new Entry(Kind.VERSION, describe(filter.versions())));
        if (filter.hasServerOrWorld()) {
            active.add(new Entry(Kind.SERVER, describe(filter.serversOrWorlds())));
        }
        return List.copyOf(active);
    }

    /**
     * The chosen values by name, sorted and comma separated, "None" when the set is empty, or {@code null}
     * when nothing is chosen because every value is kept.
     */
    public static String describe(Set<String> values) {
        if (values == null) return null;
        if (values.isEmpty()) return Component.translatable("allthelogs.playtime.filter.none").getString();
        return values.stream().map(ActiveFilters::name).sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.joining(", "));
    }

    /**
     * A version, server, or world as shown to the player: without formatting codes, or "unknown" for
     * {@link ChatQuery#NULL} and missing values.
     */
    public static String name(String value) {
        if (value == null || value.isBlank() || ChatQuery.NULL.equals(value)) {
            return Component.translatable("allthelogs.unknown").getString();
        }
        return PlaytimeSeries.displayName(value);
    }

    public static Component buttonLabel(int count) {
        if (count <= 0) return Component.translatable("allthelogs.filter");
        return Component.translatable("allthelogs.filter.count", count);
    }
}
