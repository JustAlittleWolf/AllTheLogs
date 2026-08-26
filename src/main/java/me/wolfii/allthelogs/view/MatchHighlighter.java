package me.wolfii.allthelogs.view;

import me.wolfii.allthelogs.search.SearchFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Finds the character ranges of a search query inside a message. Substring search is literal, not regex.
 */
public final class MatchHighlighter {
    private MatchHighlighter() {
    }

    public static List<HighlightSpan> spans(String message, SearchFilter filter) {
        if (message == null || !filter.hasText()) {
            return List.of();
        }
        if (filter.regex()) {
            return regexSpans(message, filter.text(), filter.caseSensitive());
        }
        return substringSpans(message, filter.text(), filter.caseSensitive());
    }

    static List<HighlightSpan> substringSpans(String message, String query, boolean caseSensitive) {
        if (query.isEmpty()) return List.of();
        String haystack = caseSensitive ? message : message.toLowerCase(Locale.ROOT);
        String needle = caseSensitive ? query : query.toLowerCase(Locale.ROOT);
        List<HighlightSpan> spans = new ArrayList<>();
        int from = 0;
        while (from <= haystack.length() - needle.length()) {
            int index = haystack.indexOf(needle, from);
            if (index < 0) break;
            spans.add(new HighlightSpan(index, index + needle.length()));
            from = index + Math.max(1, needle.length());
        }
        return List.copyOf(spans);
    }

    static List<HighlightSpan> regexSpans(String message, String regex, boolean caseSensitive) {
        Pattern pattern;
        try {
            int flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            pattern = Pattern.compile(regex, flags);
        } catch (PatternSyntaxException e) {
            return List.of();
        }
        Matcher matcher = pattern.matcher(message);
        List<HighlightSpan> spans = new ArrayList<>();
        while (matcher.find()) {
            if (matcher.start() == matcher.end()) {
                if (matcher.end() == message.length()) break;
                matcher.region(matcher.end() + 1, message.length());
                continue;
            }
            spans.add(new HighlightSpan(matcher.start(), matcher.end()));
        }
        return List.copyOf(spans);
    }
}
