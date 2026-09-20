package me.wolfii.allthelogs.client.search;

import me.wolfii.allthelogs.client.ui.theme.Colors;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegexHighlightTest {
    @Test
    void coloursEscapesClassesGroupsAndQuantifiers() {
        assertEquals(Colors.REGEX_ESCAPE & 0xFFFFFF, colorOf(RegexHighlight.highlight("\\d"), 0));
        assertEquals(Colors.REGEX_CLASS & 0xFFFFFF, colorOf(RegexHighlight.highlight("[a-z]"), 0));
        assertEquals(Colors.REGEX_GROUP & 0xFFFFFF, colorOf(RegexHighlight.highlight("(ab|c)"), 0));
        assertEquals(Colors.REGEX_QUANTIFIER & 0xFFFFFF, colorOf(RegexHighlight.highlight("a+"), 1));
        assertEquals(Colors.REGEX_ANCHOR & 0xFFFFFF, colorOf(RegexHighlight.highlight("^hi$"), 0));
        assertEquals(Colors.SEARCH_TEXT & 0xFFFFFF, colorOf(RegexHighlight.highlight("hello"), 0));
        assertEquals("\\s+", RegexHighlight.highlight("\\s+").getString());
    }

    private static int colorOf(Component component, int index) {
        String text = component.getString();
        Component leaf = component.getSiblings().isEmpty() ? component : component.getSiblings().getFirst();
        int cursor = 0;
        if (!component.getSiblings().isEmpty()) {
            for (Component sibling : component.getSiblings()) {
                String part = sibling.getString();
                if (index < cursor + part.length()) {
                    leaf = sibling;
                    break;
                }
                cursor += part.length();
            }
        } else {
            assertEquals(text, leaf.getString());
        }
        return leaf.getStyle().getColor().getValue();
    }
}
