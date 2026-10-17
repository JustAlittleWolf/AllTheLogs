package me.wolfii.allthelogs.client.ui.widget;

import me.wolfii.allthelogs.client.list.MessageListLayout;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchErrorChipTest {
    @Test
    void chipIsTheSearchBarWidthAndWrapsOnlyPastThat() {
        int searchBar = 420;
        assertEquals(searchBar - SearchErrorChip.PAD_X * 2, SearchErrorChip.innerWidth(searchBar));
        assertFalse(SearchErrorChip.wraps(180, searchBar));
        assertTrue(SearchErrorChip.wraps(searchBar, searchBar));
    }

    @Test
    void oneLineUsesTheMatchChipHeight() {
        assertEquals(0, SearchErrorChip.heightFor(0, 9));
        assertEquals(MessageListLayout.DATE_HEIGHT, SearchErrorChip.heightFor(1, 9));
        assertTrue(SearchErrorChip.heightFor(2, 9) > MessageListLayout.DATE_HEIGHT);
    }

    @Test
    void errorTextIsRedButNotFullySaturated() {
        Component colored = SearchErrorChip.colored(Component.literal("Invalid regex: Unclosed character class"));
        int rgb = colored.getStyle().getColor().getValue();
        assertEquals(Colors.SEARCH_ERROR & 0xFFFFFF, rgb);
        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;
        assertTrue(red > green && red > blue);
        assertTrue(green > 0x40);
        assertTrue(red < 0xFF);
    }
}
