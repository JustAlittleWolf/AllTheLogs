package me.wolfii.allthelogs.view;

import me.wolfii.allthelogs.search.SearchFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchHighlighterTest {
    @Test
    void findsOverlappingInsensitiveSubstrings() {
        List<HighlightSpan> spans = MatchHighlighter.substringSpans("Welcome welcome", "we", false);
        assertEquals(List.of(new HighlightSpan(0, 2), new HighlightSpan(8, 10)), spans);
    }

    @Test
    void regexHighlightsAreCaseInsensitiveByDefault() {
        SearchFilter filter = SearchFilter.defaults().withText("nee+dle").withRegex(true);
        List<HighlightSpan> spans = MatchHighlighter.spans("a needle and a neeeedle", filter);
        assertEquals(2, spans.size());
        assertEquals("needle", "a needle and a neeeedle".substring(spans.get(0).start(), spans.get(0).end()));
        assertEquals("neeeedle", "a needle and a neeeedle".substring(spans.get(1).start(), spans.get(1).end()));
    }

    @Test
    void invalidRegexYieldsNoSpans() {
        SearchFilter filter = SearchFilter.defaults().withText("(").withRegex(true);
        assertTrue(MatchHighlighter.spans("hello", filter).isEmpty());
    }

    @Test
    void blankFilterYieldsNoSpans() {
        assertTrue(MatchHighlighter.spans("hello", SearchFilter.defaults()).isEmpty());
    }
}
