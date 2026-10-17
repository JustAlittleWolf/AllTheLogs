package me.wolfii.allthelogs.client.search;

import me.wolfii.allthelogs.client.ui.theme.Colors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchDecorationsTest {
    @Test
    void regexChromeSitsBesideThePattern() {
        SearchFilter regex = SearchFilter.defaults().withRegex(true).withText("foo.*");
        assertTrue(SearchDecorations.wraps(regex));
        assertEquals("/", SearchDecorations.prefix(regex));
        assertEquals("/i", SearchDecorations.suffix(regex));
        assertEquals(Colors.REGEX_CHROME, SearchDecorations.decorationColor("/i", 0));
        assertEquals(Colors.REGEX_CHROME_FLAG, SearchDecorations.decorationColor("/i", 1));
    }

    @Test
    void caseSensitiveRegexOmitsTheIFlag() {
        SearchFilter regex = SearchFilter.defaults().withRegex(true).withCaseSensitive(true).withText("Bar");
        assertEquals("/", SearchDecorations.prefix(regex));
        assertEquals("/", SearchDecorations.suffix(regex));
        assertEquals(Colors.REGEX_CHROME, SearchDecorations.decorationColor("/", 0));
    }

    @Test
    void closingMarkHasTwoExtraPixelsOfLeftMargin() {
        assertEquals(2, SearchDecorations.SUFFIX_EXTRA_MARGIN);
        assertEquals(SearchDecorations.GAP + 2, SearchDecorations.suffixGap());
    }

    @Test
    void substringSearchHasNoChrome() {
        SearchFilter text = SearchFilter.defaults().withText("hello");
        assertFalse(SearchDecorations.wraps(text));
        assertEquals("", SearchDecorations.prefix(text));
        assertEquals("", SearchDecorations.suffix(text));
    }
}
