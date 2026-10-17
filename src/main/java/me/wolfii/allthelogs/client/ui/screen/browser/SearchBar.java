package me.wolfii.allthelogs.client.ui.screen.browser;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import me.wolfii.allthelogs.client.search.SearchDecorations;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import me.wolfii.allthelogs.client.ui.widget.DecoratedSearchField;
import me.wolfii.allthelogs.client.ui.widget.SearchErrorChip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The search box of the messages and DB Cleanup screens. The regex and case marks sit around the text,
 * regex syntax is highlighted, and an invalid regex turns the text red. The reason is a chip above the
 * field, as wide as the field, all following {@code filter}.
 */
final class SearchBar {
    /** Edit-box cap. Long chat patterns need more than the old 256, and 2^16 is still one string. */
    static final int MAX_LENGTH = 1 << 16;

    private final FlowLayout column = UIContainers.verticalFlow(Sizing.expand(), Sizing.content());
    private final SearchErrorChip errorChip = new SearchErrorChip();
    private final DecoratedSearchField field = new DecoratedSearchField();
    private final Supplier<SearchFilter> filter;
    private boolean syncing;

    /**
     * @param onText receives the text the user typed, not text put back by {@link #sync()}
     */
    SearchBar(Supplier<SearchFilter> filter, Component hint, Consumer<String> onText) {
        this.filter = filter;
        column.gap(SearchErrorChip.GAP);
        column.child(field);
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
     * The field, with the error chip above it when the regex cannot be searched.
     */
    UIComponent component() {
        return column;
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
        SearchFilter current = filter.get();
        Component error = regexError(current);
        field.setTextColor(error != null ? Colors.SEARCH_INVALID : Colors.SEARCH_TEXT);
        field.tooltip(List.<ClientTooltipComponent>of());
        presentError(error);
    }

    private void presentError(Component error) {
        errorChip.message(error);
        boolean show = errorChip.visible();
        boolean mounted = column.children().contains(errorChip);
        if (show && !mounted) column.child(0, errorChip);
        else if (!show && mounted) column.removeChild(errorChip);
    }

    /**
     * Why this regex cannot be searched, or {@code null} when the filter can be queried.
     */
    static Component regexError(SearchFilter filter) {
        if (filter == null) return null;
        String issue = filter.regexIssue();
        if (issue == null) return null;
        return Component.translatable("allthelogs.search.regex_error", issue);
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
