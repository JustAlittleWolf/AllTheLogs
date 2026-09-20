package me.wolfii.allthelogs.client.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchDecorationsTest {
    @Test
    void wrapsRegexWithUneditableSlashesAndAnIFlagWhenInsensitive() {
        SearchFilter regex = SearchFilter.defaults().withRegex(true).withText("foo.*");
        assertTrue(SearchDecorations.wraps(regex));
        assertEquals("/foo.*/i", SearchDecorations.wrap(regex, "foo.*"));
        assertEquals("foo.*", SearchDecorations.unwrap(regex, "/foo.*/i"));
        assertEquals(1, SearchDecorations.clampCursor(regex, "/foo.*/i", 0));
        assertEquals(6, SearchDecorations.clampCursor(regex, "/foo.*/i", 99));
    }

    @Test
    void omitsTheIFlagWhenCaseSensitive() {
        SearchFilter regex = SearchFilter.defaults().withRegex(true).withCaseSensitive(true).withText("Bar");
        assertEquals("/Bar/", SearchDecorations.wrap(regex, "Bar"));
        assertEquals("Bar", SearchDecorations.unwrap(regex, "/Bar/"));
    }

    @Test
    void substringSearchHasNoSlashWrapping() {
        SearchFilter text = SearchFilter.defaults().withText("hello");
        assertFalse(SearchDecorations.wraps(text));
        assertEquals("hello", SearchDecorations.wrap(text, "hello"));
        assertEquals("hello", SearchDecorations.unwrap(text, "hello"));
    }
}
