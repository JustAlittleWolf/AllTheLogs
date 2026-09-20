package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.component.CheckboxComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import me.wolfii.allthelogs.client.search.DateParser;
import me.wolfii.allthelogs.client.search.RegexFlags;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Side-expandable filter panel for {@link LogBrowserScreen}. It stays open while the user searches.
 */
final class FilterOverlay {
    static final int PANEL_WIDTH = 240;
    private static boolean sessionOpen;

    private final FlowLayout host;
    private final Supplier<SearchFilter> filter;
    private final Consumer<SearchFilter> onChange;
    private final VersionMenu versionsMenu;
    private ParentUIComponent filterPanel;
    private CheckboxComponent regexBox;
    private CheckboxComponent caseBox;
    private TextBoxComponent flagsBox;
    private TextBoxComponent contextBox;
    private TextBoxComponent serverBox;
    private TextBoxComponent fromBox;
    private TextBoxComponent untilBox;
    private boolean syncing;

    FilterOverlay(FlowLayout host, StackLayout overlays, IntSupplier screenWidth, IntSupplier screenHeight,
                  Supplier<SearchFilter> filter, Supplier<List<String>> versions,
                  Consumer<SearchFilter> onChange) {
        this.host = host;
        this.filter = filter;
        this.onChange = onChange;
        this.versionsMenu = new VersionMenu(overlays, screenWidth, screenHeight, filter, versions, onChange);
    }

    boolean open() {
        return filterPanel != null && host.children().contains(filterPanel);
    }

    void restore() {
        if (sessionOpen) openPanel();
    }

    void toggle() {
        if (open()) {
            close();
            return;
        }
        openPanel();
    }

    void close() {
        versionsMenu.close();
        if (filterPanel != null) {
            host.removeChild(filterPanel);
            filterPanel = null;
        }
        sessionOpen = false;
    }

    /**
     * Whether {@code component} is inside the open filter panel.
     */
    boolean owns(UIComponent component) {
        if (!open() || component == null) return false;
        for (UIComponent current = component; current != null; current = current.parent()) {
            if (current == filterPanel) return true;
        }
        return false;
    }

    void syncFromFilter() {
        SearchFilter current = filter.get();
        syncing = true;
        try {
            if (regexBox != null) regexBox.checked(current.regex());
            if (caseBox != null) caseBox.checked(current.caseSensitive());
            if (flagsBox != null && !flagsBox.getValue().equals(current.regexFlags())) {
                flagsBox.setValue(current.regexFlags());
            }
            if (contextBox != null && !contextBox.getValue().equals(String.valueOf(current.contextLines()))) {
                contextBox.setValue(String.valueOf(current.contextLines()));
            }
            String from = DateParser.format(current.startingAt());
            if (fromBox != null && !fromBox.getValue().equals(from)) fromBox.setValue(from);
            String until = DateParser.formatUntil(current.upUntil());
            if (untilBox != null && !untilBox.getValue().equals(until)) untilBox.setValue(until);
            String server = current.serverOrWorld() == null ? "" : current.serverOrWorld();
            if (serverBox != null && !serverBox.getValue().equals(server)) serverBox.setValue(server);
            versionsMenu.syncButton();
        } finally {
            syncing = false;
        }
    }

    void syncVersionButton() {
        versionsMenu.syncButton();
    }

    private void openPanel() {
        if (open()) return;
        filterPanel = buildFilterPanel();
        host.child(filterPanel);
        sessionOpen = true;
    }

    private ParentUIComponent buildFilterPanel() {
        SearchFilter current = filter.get();
        FlowLayout content = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        content.padding(Insets.of(8));
        content.gap(4);

        regexBox = checkbox("allthelogs.filter.regex", current.regex(), value ->
            emit(filter.get().withRegex(value)));
        content.child(regexBox);
        caseBox = checkbox("allthelogs.filter.case_sensitive", current.caseSensitive(), value ->
            emit(filter.get().withCaseSensitive(value)));
        content.child(caseBox);
        content.child(flagsField(current.regexFlags()));
        content.child(contextField(current));

        content.child(dateField("allthelogs.filter.from", DateParser.format(current.startingAt()),
            DateParser::parse, parsed -> emit(filter.get().withStartingAt(parsed)), true));
        content.child(dateField("allthelogs.filter.until", DateParser.formatUntil(current.upUntil()),
            DateParser::parseUntil, parsed -> emit(filter.get().withUpUntil(parsed)), false));
        content.child(UIComponents.label(Component.translatable("allthelogs.filter.date_hint"))
            .color(Color.ofRgb(0x888888)));
        content.child(versionsMenu.row());
        content.child(serverField(current.serverOrWorld()));

        ScrollContainer<FlowLayout> panel = UIContainers.verticalScroll(
            Sizing.fixed(PANEL_WIDTH), Sizing.fill(), content);
        panel.scrollbar(OverflowScrollbar.vanillaFlat());
        panel.surface(PanelSurfaces.card());
        panel.verticalSizing(Sizing.fill());
        return panel;
    }

    private FlowLayout flagsField(String flags) {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        row.child(UIComponents.label(Component.translatable("allthelogs.filter.regex_flags")));
        flagsBox = UIComponents.textBox(Sizing.fill(), flags);
        flagsBox.setMaxLength(RegexFlags.VALID.length());
        flagsBox.setFilter(RegexFlags::isLegal);
        flagsBox.onChanged().subscribe(text -> {
            if (!syncing) emit(filter.get().withRegexFlags(text));
        });
        row.child(flagsBox);
        return row;
    }

    private FlowLayout serverField(String value) {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        row.child(UIComponents.label(Component.translatable("allthelogs.filter.server")));
        serverBox = UIComponents.textBox(Sizing.fill(), value == null ? "" : value);
        serverBox.setMaxLength(256);
        serverBox.setHint(Component.translatable("allthelogs.filter.server_hint"));
        serverBox.onChanged().subscribe(text -> {
            if (!syncing) emit(filter.get().withServerOrWorld(text));
        });
        row.child(serverBox);
        return row;
    }

    private FlowLayout contextField(SearchFilter current) {
        return labeledField("allthelogs.filter.context", String.valueOf(current.contextLines()), text -> {
            try {
                emit(filter.get().withContextLines(Integer.parseInt(text.trim())));
            } catch (RuntimeException ignored) {
            }
        }, true);
    }

    private CheckboxComponent checkbox(String key, boolean checked, Consumer<Boolean> onChanged) {
        CheckboxComponent box = UIComponents.checkbox(Component.translatable(key));
        box.checked(checked);
        box.onChanged(value -> {
            if (!syncing) onChanged.accept(value);
        });
        return box;
    }

    private FlowLayout dateField(String key, String value, Function<String, Optional<LocalDateTime>> parse,
                                 Consumer<LocalDateTime> onParsed, boolean from) {
        FlowLayout row = labeledField(key, value, text -> {
            if (!DateParser.isBlankOrValid(text)) return;
            onParsed.accept(parse.apply(text).orElse(null));
        }, false);
        if (from) fromBox = lastBox(row);
        else untilBox = lastBox(row);
        return row;
    }

    private FlowLayout labeledField(String key, String value, Consumer<String> onFieldChange, boolean context) {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        row.child(UIComponents.label(Component.translatable(key)));
        TextBoxComponent box = UIComponents.textBox(Sizing.fill(), value);
        box.setMaxLength(32);
        box.onChanged().subscribe(text -> {
            if (!syncing) onFieldChange.accept(text);
        });
        row.child(box);
        if (context) contextBox = box;
        return row;
    }

    private static TextBoxComponent lastBox(FlowLayout row) {
        List<UIComponent> children = row.children();
        return (TextBoxComponent) children.getLast();
    }

    private void emit(SearchFilter next) {
        if (syncing) return;
        onChange.accept(next);
        syncFromFilter();
    }
}
