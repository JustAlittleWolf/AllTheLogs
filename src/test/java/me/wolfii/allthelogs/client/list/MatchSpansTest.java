package me.wolfii.allthelogs.client.list;

import me.wolfii.allthelogs.client.search.SearchFilter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchSpansTest {
    @Test
    void findsOverlappingInsensitiveSubstrings() {
        List<HighlightSpan> spans = MatchSpans.substringSpans("Welcome welcome", "we", false);
        assertEquals(List.of(new HighlightSpan(0, 2), new HighlightSpan(8, 10)), spans);
    }

    @Test
    void regexHighlightsAreCaseInsensitiveByDefault() {
        SearchFilter filter = SearchFilter.defaults().withText("nee+dle").withRegex(true);
        List<HighlightSpan> spans = MatchSpans.spans("a needle and a neeeedle", filter);
        assertEquals(2, spans.size());
        assertEquals("needle", "a needle and a neeeedle".substring(spans.get(0).start(), spans.get(0).end()));
        assertEquals("neeeedle", "a needle and a neeeedle".substring(spans.get(1).start(), spans.get(1).end()));
    }

    @Test
    void regexHighlightsUseDuckDbInlineFlags() {
        String message = "a\nneedle";
        SearchFilter dotall = SearchFilter.defaults().withText("a.needle").withRegex(true).withRegexFlags("s");
        List<HighlightSpan> spans = MatchSpans.spans(message, dotall);
        assertEquals(1, spans.size());
        assertEquals(message, message.substring(spans.get(0).start(), spans.get(0).end()));
        SearchFilter anchored = SearchFilter.defaults().withText("^needle").withRegex(true)
            .withCaseSensitive(true).withRegexFlags("m");
        List<HighlightSpan> line = MatchSpans.spans(message, anchored);
        assertEquals(1, line.size());
        assertEquals("needle", message.substring(line.get(0).start(), line.get(0).end()));
    }

    @Test
    void invalidRegexYieldsNoSpans() {
        SearchFilter filter = SearchFilter.defaults().withText("(").withRegex(true);
        assertTrue(MatchSpans.spans("hello", filter).isEmpty());
    }

    @Test
    void blankFilterYieldsNoSpans() {
        assertTrue(MatchSpans.spans("hello", SearchFilter.defaults()).isEmpty());
    }
}
