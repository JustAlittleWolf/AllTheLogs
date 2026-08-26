package me.wolfii.allthelogs.client.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageMatcherTest {
    @Test
    void substringIsCaseInsensitiveByDefault() {
        var matches = MessageMatcher.predicate(SearchFilter.defaults().withText("Needle"));
        assertTrue(matches.test("a needle here"));
        assertFalse(matches.test("haystack"));
    }

    @Test
    void invalidRegexNeverMatches() {
        var matches = MessageMatcher.predicate(SearchFilter.defaults().withText("(").withRegex(true));
        assertFalse(matches.test("hello"));
        assertTrue(MessageMatcher.compiledRegex("(", false).isEmpty());
    }

    @Test
    void emptyTextMatchesEverything() {
        assertTrue(MessageMatcher.predicate(SearchFilter.defaults()).test("anything"));
    }
}
