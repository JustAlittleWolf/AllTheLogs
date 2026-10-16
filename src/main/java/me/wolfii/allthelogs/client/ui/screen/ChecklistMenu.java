package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.CheckboxComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.Sizing;
import me.wolfii.allthelogs.client.playtime.PlaytimeFilter;
import me.wolfii.allthelogs.client.ui.text.TextFit;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Searchable multi-select list as wide as its button. A null selection means every row is checked.
 * All and None check or uncheck the rows that match the search, or every row when the search is empty.
 */
final class ChecklistMenu {
    private static final int ROW_HEIGHT = 18;
    private static final int PADDING = 3;
    private static final int GAP = 2;
    private static final int HEADER_HEIGHT = 20 + GAP + 20 + GAP;
    /** Checkbox square, its gap to the label, and the scrollbar beside the list. */
    private static final int CHECKBOX_CHROME = 17 + 4 + 6;

    private final StackLayout overlays;
    private final IntSupplier screenWidth;
    private final IntSupplier screenHeight;
    private ParentUIComponent menu;

    ChecklistMenu(StackLayout overlays, IntSupplier screenWidth, IntSupplier screenHeight) {
        this.overlays = overlays;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    boolean isOpen() {
        return menu != null && overlays.children().contains(menu);
    }

    void close() {
        if (isOpen()) overlays.removeChild(menu);
        menu = null;
    }

    /**
     * @param name  plain display text for a value, used for the label and the search
     * @param apply receives the new selection after every change
     */
    void show(ButtonComponent anchor, List<String> values, Supplier<Set<String>> selection,
              Function<String, String> name, Consumer<Set<String>> apply) {
        close();
        int width = anchor.width();
        int spaceBelow = screenHeight.getAsInt() - anchor.y() - anchor.height() - 8;
        int spaceAbove = anchor.y() - 8;
        int listRoom = Math.max(ROW_HEIGHT * 2, Math.min(200, Math.max(spaceBelow, spaceAbove) - HEADER_HEIGHT - PADDING * 2));
        int listHeight = Math.min(listRoom, Math.max(1, values.size()) * ROW_HEIGHT + 2);

        FlowLayout rows = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        rows.gap(1);
        ScrollContainer<FlowLayout> list = UIContainers.verticalScroll(Sizing.fill(), Sizing.fixed(listHeight), rows);
        list.scrollbar(OverflowScrollbar.vanillaFlat());
        list.padding(Insets.right(list.scrollbarThiccness() + 1));

        TextBoxComponent search = UIComponents.textBox(Sizing.fill());
        search.setMaxLength(64);
        search.setHint(Component.translatable("allthelogs.playtime.filter.search"));
        int labelWidth = width - PADDING * 2 - CHECKBOX_CHROME;
        Runnable refill = () -> fill(rows, matching(values, name, search.getValue()), values, selection, name, apply,
            labelWidth);
        search.onChanged().subscribe(text -> refill.run());

        FlowLayout bulk = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        bulk.gap(GAP);
        bulk.child(bulkButton("allthelogs.playtime.filter.all", () -> {
            apply.accept(PlaytimeFilter.select(selection.get(), matching(values, name, search.getValue()), true, values));
            refill.run();
        }));
        bulk.child(bulkButton("allthelogs.playtime.filter.none", () -> {
            apply.accept(PlaytimeFilter.select(selection.get(), matching(values, name, search.getValue()), false, values));
            refill.run();
        }));

        FlowLayout popup = UIContainers.verticalFlow(Sizing.fixed(width), Sizing.content());
        popup.gap(GAP).padding(Insets.of(PADDING));
        popup.surface(PanelSurfaces.menu());
        popup.child(search);
        popup.child(bulk);
        popup.child(list);
        refill.run();
        AnchoredPopup.place(popup, anchor.x(), anchor.y(), anchor.height(), width,
            HEADER_HEIGHT + listHeight + PADDING * 2, screenWidth.getAsInt(), screenHeight.getAsInt());
        menu = popup;
        overlays.child(popup);
    }

    private static void fill(FlowLayout rows, List<String> shown, List<String> values, Supplier<Set<String>> selection,
                             Function<String, String> name, Consumer<Set<String>> apply, int labelWidth) {
        rows.clearChildren();
        Set<String> selected = selection.get();
        for (String value : shown) {
            CheckboxComponent box = UIComponents.checkbox(Component.literal(TextFit.fit(name.apply(value), labelWidth)));
            box.checked(selected == null || selected.contains(value));
            box.horizontalSizing(Sizing.fill());
            box.onChanged(now -> apply.accept(PlaytimeFilter.select(selection.get(), List.of(value), now, values)));
            rows.child(box);
        }
    }

    private static List<String> matching(List<String> values, Function<String, String> name, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return values;
        return values.stream().filter(value -> name.apply(value).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    private static ButtonComponent bulkButton(String key, Runnable action) {
        ButtonComponent button = UIComponents.button(Component.translatable(key), ignored -> action.run());
        button.horizontalSizing(Sizing.expand());
        return button;
    }
}
