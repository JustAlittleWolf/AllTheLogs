package me.wolfii.allthelogs.client.search;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Java-side matching for a {@link SearchFilter}. SQL matching stays in {@link ChatQueryFactory}.
 */
public final class MessageMatcher {
    private MessageMatcher() {
    }

    public static Predicate<String> predicate(SearchFilter filter) {
        if (!filter.hasText()) {
            return message -> true;
        }
        if (filter.regex()) {
            return compiledRegex(filter.text(), filter.caseSensitive())
                .map(pattern -> (Predicate<String>) message -> pattern.matcher(message).find())
                .orElse(message -> false);
        }
        if (filter.caseSensitive()) {
            String needle = filter.text();
            return message -> message.contains(needle);
        }
        String needle = filter.text().toLowerCase(Locale.ROOT);
        return message -> message.toLowerCase(Locale.ROOT).contains(needle);
    }

    public static Optional<Pattern> compiledRegex(String regex, boolean caseSensitive) {
        try {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            return Optional.of(Pattern.compile(regex, flags));
        } catch (PatternSyntaxException e) {
            return Optional.empty();
        }
    }
}
