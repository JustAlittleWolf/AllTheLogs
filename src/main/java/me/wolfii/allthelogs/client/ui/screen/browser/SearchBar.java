package me.wolfii.allthelogs.client.ui.screen.browser;

import me.wolfii.allthelogs.client.search.SearchDecorations;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import me.wolfii.allthelogs.client.ui.widget.DecoratedSearchField;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The search box of the messages and DB Cleanup screens. The regex and case marks sit around the text,
 * regex syntax is highlighted, and an invalid regex turns the text red, all following {@code filter}.
 */
final class SearchBar {
    private static final int MAX_LENGTH = 256;

    private final DecoratedSearchField field = new DecoratedSearchField();
    private final Supplier<SearchFilter> filter;
    private boolean syncing;

    /**
     * @param onText receives the text the user typed, not text put back by {@link #sync()}
     */
    SearchBar(Supplier<SearchFilter> filter, Component hint, Consumer<String> onText) {
        this.filter = filter;
        field.setMaxLength(MAX_LENGTH);
        field.setValue(filter.get().text());
        field.setHint(hint);
        field.addFormatter((visible, start) -> SearchDecorations.format(filter.get(), visible, start));
        field.onChanged().subscribe(text -> {
            if (!syncing) onText.accept(text == null ? "" : text);
        });
        sync();
    }

    DecoratedSearchField field() {
        return field;
    }

    /**
     * Brings the marks, the text, and its color in line with the filter, keeping the cursor where it can.
     */
    void sync() {
        SearchFilter current = filter.get();
        field.setDecorations(SearchDecorations.prefix(current), SearchDecorations.suffix(current));
        String shown = current.text();
        if (!shown.equals(field.getValue())) {
            int cursor = Math.min(field.getCursorPosition(), shown.length());
            setQuietly(shown);
            field.setCursorPosition(cursor);
        }
        refreshColor();
    }

    void refreshColor() {
        field.setTextColor(filter.get().invalidRegex() ? Colors.SEARCH_INVALID : Colors.SEARCH_TEXT);
    }

    /**
     * The box is built before it is laid out, so text set then is applied at width 0 and scrolled out of
     * sight. Puts it back once the box has a width, including text cut off by the edit box's default length.
     */
    void reveal() {
        field.setMaxLength(MAX_LENGTH);
        String shown = filter.get().text();
        if (!shown.equals(field.getValue())) setQuietly(shown);
        field.revealValue();
    }

    private void setQuietly(String text) {
        syncing = true;
        try {
            field.setValue(text);
        } finally {
            syncing = false;
        }
    }
}
