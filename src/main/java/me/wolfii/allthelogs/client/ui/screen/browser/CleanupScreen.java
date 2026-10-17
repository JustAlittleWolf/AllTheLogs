package me.wolfii.allthelogs.client.ui.screen.browser;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.config.BrowserSession;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.ui.popup.ModalDialog;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import me.wolfii.allthelogs.client.ui.theme.OverflowScrollbar;
import me.wolfii.allthelogs.client.ui.theme.PanelSurfaces;
import me.wolfii.allthelogs.client.ui.widget.DoneButton;
import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.parse.TextRemoval;
import me.wolfii.allthelogs.data.query.CleanupPreview;
import me.wolfii.allthelogs.data.query.MessageCleanup;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * DB Cleanup: deletes stored messages, or cuts the search text out of them, for everything the search and
 * filters match. It starts with the messages screen's filters. Before anything is changed it shows how many
 * messages are affected, the oldest and newest of them, and as many picked at random as fit, and the action
 * asks for confirmation. Without a search or a filter nothing is offered, so it never deletes everything.
 * Once messages changed, the messages screen starts over, see {@link BrowserSession#reset()}.
 */
public final class CleanupScreen extends BaseOwoScreen<StackLayout> {
    private static final int EDGE_SIZE = 3;
    private static final int MAX_PREVIEW_CHARS = 300;
    /** Height around the preview list: page padding, the search row, the bottom row, and the gaps. */
    private static final int PREVIEW_CHROME = 76;
    private static final int REMOVED = 0xFF6B6B;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private enum Mode {
        DELETE, REMOVE_TEXT
    }

    private final Screen parent;
    private final StoreCatalog catalog;
    private SearchFilter filter;
    private Mode mode = Mode.DELETE;
    private StackLayout overlays;
    private FilterOverlay filters;
    private SearchBar searchBar;
    private ModalDialog dialog;
    private ButtonComponent modeButton;
    private FlowLayout preview;
    private LabelComponent status;
    private ButtonComponent applyButton;
    private CleanupPreview current;
    private @Nullable Component notice;
    private int requestToken;

    /**
     * Starts with the filters the messages screen would open with, for opening without that screen.
     */
    public CleanupScreen(@Nullable Screen parent) {
        this(parent, BrowserSession.openingFilter(), StoreCatalog.load());
    }

    public CleanupScreen(@Nullable Screen parent, SearchFilter filter, StoreCatalog catalog) {
        super(Component.translatable("allthelogs.screen.cleanup"));
        this.parent = parent;
        this.filter = filter == null ? SearchFilter.defaults() : filter;
        this.catalog = catalog;
    }

    @Override
    protected @NotNull OwoUIAdapter<StackLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::stack);
    }

    @Override
    protected void build(StackLayout root) {
        overlays = root;
        root.allowOverflow(true);
        dialog = new ModalDialog(overlays, () -> this.width);
        FlowLayout page = UIContainers.verticalFlow(Sizing.fill(), Sizing.fill());
        page.gap(6).padding(Insets.of(8));
        page.surface(Surface.blur(5, 10).and(PanelSurfaces.overlay()));
        page.horizontalAlignment(HorizontalAlignment.LEFT);
        page.verticalAlignment(VerticalAlignment.TOP);

        FlowLayout top = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        top.gap(4).verticalAlignment(VerticalAlignment.CENTER);
        searchBar = new SearchBar(() -> filter, Component.translatable("allthelogs.cleanup.text_hint"),
            this::onTextChanged);
        top.child(searchBar.field());
        modeButton = UIComponents.button(modeLabel(mode), pressed -> toggleMode());
        modeButton.horizontalSizing(Sizing.fixed(widest(modeLabel(Mode.DELETE), modeLabel(Mode.REMOVE_TEXT))));
        top.child(modeButton);
        page.child(top);

        FlowLayout body = UIContainers.horizontalFlow(Sizing.fill(), Sizing.expand());
        body.gap(6);
        preview = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        preview.gap(2).padding(Insets.of(4));
        ScrollContainer<FlowLayout> previewScroll = OverflowScrollbar.vertical(Sizing.expand(), Sizing.fill(), preview);
        previewScroll.surface(Surface.flat(Colors.LIST_BACKGROUND));
        body.child(previewScroll);
        page.child(body);
        filters = new FilterOverlay(body, overlays, () -> this.width, () -> this.height,
            () -> filter, catalog, this::update);

        FlowLayout bottom = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        bottom.gap(4).verticalAlignment(VerticalAlignment.CENTER);
        status = UIComponents.label(Component.empty());
        status.horizontalSizing(Sizing.expand());
        status.maxWidth(Math.max(120, this.width / 2));
        bottom.child(status);
        applyButton = UIComponents.button(applyLabel(mode), pressed -> confirmApply());
        applyButton.horizontalSizing(Sizing.fixed(widest(applyLabel(Mode.DELETE), applyLabel(Mode.REMOVE_TEXT))));
        bottom.child(applyButton);
        bottom.child(DoneButton.of(this));
        page.child(bottom);

        root.child(page);
        filters.openDetached();
        refresh();
    }

    @Override
    protected void init() {
        super.init();
        if (searchBar != null) searchBar.reveal();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (this.minecraft == null || this.minecraft.level != null) return;
        this.extractPanorama(graphics, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (filters != null && (dialog == null || !dialog.isOpen())) {
            filters.closeListsOnClickOutside(event.x(), event.y());
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (dialog != null && dialog.isOpen()) {
            if (event.isEscape() && !dialog.working()) dialog.close();
            return true;
        }
        if (event.isEscape() && filters != null && filters.listOpen()) {
            filters.closeLists();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (dialog != null && dialog.working()) return;
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private void update(SearchFilter next) {
        filter = next;
        notice = null;
        if (searchBar != null) searchBar.sync();
        refresh();
    }

    /**
     * Counts again once typing pauses, like the messages screen's search, instead of on every key.
     */
    private void onTextChanged(String text) {
        filter = filter.withText(text);
        notice = null;
        searchBar.refreshColor();
        current = null;
        int request = ++requestToken;
        status.text(Component.translatable("allthelogs.cleanup.counting"));
        syncApply();
        CompletableFuture.delayedExecutor(LogBrowserScreen.SEARCH_DEBOUNCE_MS, TimeUnit.MILLISECONDS).execute(() ->
            Minecraft.getInstance().execute(() -> {
                if (request == requestToken && Minecraft.getInstance().gui.screen() == this) refresh();
            }));
    }

    private void toggleMode() {
        mode = mode == Mode.DELETE ? Mode.REMOVE_TEXT : Mode.DELETE;
        notice = null;
        modeButton.setMessage(modeLabel(mode));
        applyButton.setMessage(applyLabel(mode));
        refresh();
    }

    /**
     * Reloads the preview for the current filter and mode. A newer request makes older answers stale.
     */
    private void refresh() {
        current = null;
        int request = ++requestToken;
        Component blocked = blockedReason();
        if (blocked != null) {
            showPreview(null);
            status.text(withNotice(blocked));
            syncApply();
            return;
        }
        status.text(Component.translatable("allthelogs.cleanup.counting"));
        syncApply();
        AllTheLogsClient.worker().previewCleanup(filter.toSummaryQuery(), EDGE_SIZE, sampleSize()).whenComplete(
            (result, error) -> Minecraft.getInstance().execute(() -> {
                if (request != requestToken || Minecraft.getInstance().gui.screen() != this) return;
                if (error != null) {
                    AllTheLogsClient.LOGGER.warn("Could not preview message cleanup", error);
                    status.text(Component.translatable("allthelogs.status.error"));
                    showPreview(null);
                    return;
                }
                current = result;
                showPreview(result);
                status.text(withNotice(summary(result)));
                syncApply();
            }));
    }

    private @Nullable Component blockedReason() {
        var worker = AllTheLogsClient.worker();
        if (worker == null || !worker.isOpen()) return Component.translatable("allthelogs.meta.unavailable");
        if (filter.invalidRegex()) return Component.translatable("allthelogs.cleanup.invalid_regex");
        if (mode == Mode.REMOVE_TEXT && !filter.hasText()) return Component.translatable("allthelogs.cleanup.need_text");
        if (!filter.isNarrowed()) return Component.translatable("allthelogs.cleanup.need_filter");
        return null;
    }

    private Component withNotice(Component text) {
        return notice == null ? text : Component.empty().append(notice).append("  ").append(text);
    }

    private Component summary(CleanupPreview result) {
        if (result.matches() == 0) return Component.translatable("allthelogs.cleanup.none");
        String key = mode == Mode.REMOVE_TEXT ? "allthelogs.cleanup.summary.remove" : "allthelogs.cleanup.summary.delete";
        return Component.translatable(key, count(result.matches()));
    }

    /**
     * Random messages to ask for: as many as fit under the oldest and newest, one line each.
     */
    private int sampleSize() {
        int lines = (this.height - PREVIEW_CHROME) / (Minecraft.getInstance().font.lineHeight + 2);
        return Math.max(EDGE_SIZE, lines - EDGE_SIZE * 2 - 3);
    }

    private void showPreview(@Nullable CleanupPreview result) {
        preview.clearChildren();
        if (result == null || result.matches() == 0) return;
        Pattern removed = mode == Mode.REMOVE_TEXT ? filter.removalPattern().orElse(null) : null;
        section("allthelogs.cleanup.oldest", result.oldest(), removed);
        section("allthelogs.cleanup.newest", result.newest(), removed);
        section("allthelogs.cleanup.sample", result.sample(), removed);
    }

    private void section(String key, List<ChatEntry> entries, @Nullable Pattern removed) {
        if (entries.isEmpty()) return;
        preview.child(UIComponents.label(Component.translatable(key).withStyle(ChatFormatting.GRAY)));
        for (ChatEntry entry : entries) {
            LabelComponent line = UIComponents.label(line(entry, removed));
            line.horizontalSizing(Sizing.fill());
            line.margins(Insets.left(6));
            preview.child(line);
        }
    }

    /**
     * Gray timestamp and the message in normal text. In remove mode the parts that would be cut are red and
     * struck through.
     */
    private static Component line(ChatEntry entry, @Nullable Pattern removed) {
        MutableComponent line = Component.empty();
        line.append(Component.literal(TIME.format(entry.timestamp()) + "  ").withColor(Colors.TIMESTAMP & 0xFFFFFF));
        String message = entry.message();
        boolean cut = message.length() > MAX_PREVIEW_CHARS;
        if (cut) message = message.substring(0, MAX_PREVIEW_CHARS);
        int cursor = 0;
        if (removed != null) {
            Style strike = Style.EMPTY.withColor(REMOVED).withStrikethrough(true);
            for (int[] range : TextRemoval.ranges(message, removed)) {
                line.append(Component.literal(message.substring(cursor, range[0])));
                line.append(Component.literal(message.substring(range[0], range[1])).withStyle(strike));
                cursor = range[1];
            }
        }
        line.append(Component.literal(message.substring(cursor)));
        if (cut) line.append(Component.literal("…").withStyle(ChatFormatting.GRAY));
        return line;
    }

    private void syncApply() {
        if (applyButton != null) applyButton.active = current != null && current.matches() > 0;
    }

    private void confirmApply() {
        if (current == null || current.matches() == 0) return;
        String count = count(current.matches());
        boolean delete = mode == Mode.DELETE;
        dialog.confirm(
            Component.translatable(delete ? "allthelogs.cleanup.confirm.delete" : "allthelogs.cleanup.confirm.remove", count),
            Component.translatable(delete ? "allthelogs.cleanup.confirm.delete.body" : "allthelogs.cleanup.confirm.remove.body"),
            applyLabel(mode).copy().withStyle(ChatFormatting.RED),
            this::apply);
    }

    private void apply() {
        Optional<Pattern> part = mode == Mode.REMOVE_TEXT ? filter.removalPattern() : Optional.empty();
        if (mode == Mode.REMOVE_TEXT && part.isEmpty()) return;
        dialog.working(Component.translatable("allthelogs.cleanup.working"),
            Component.translatable("allthelogs.cleanup.working.detail"));
        AllTheLogsClient.worker().cleanUp(filter.toSummaryQuery(), part.orElse(null)).whenComplete(
            (outcome, error) -> Minecraft.getInstance().execute(() -> finished(outcome, error)));
    }

    private void finished(MessageCleanup.Outcome outcome, Throwable error) {
        dialog.close();
        if (error == null) BrowserSession.reset();
        if (Minecraft.getInstance().gui.screen() != this) return;
        if (error != null) {
            AllTheLogsClient.LOGGER.warn("Could not clean up messages", error);
            status.text(Component.translatable("allthelogs.status.error"));
            return;
        }
        notice = Component.translatable("allthelogs.cleanup.done",
            count(outcome.changed()), count(outcome.deleted())).withStyle(ChatFormatting.GREEN);
        refresh();
    }

    private static String count(long value) {
        return String.format("%,d", value);
    }

    private static int widest(Component... labels) {
        int width = 0;
        for (Component label : labels) width = Math.max(width, Minecraft.getInstance().font.width(label));
        return width + 12;
    }

    private static Component modeLabel(Mode mode) {
        String key = mode == Mode.DELETE ? "allthelogs.cleanup.mode.delete" : "allthelogs.cleanup.mode.remove";
        return Component.translatable("allthelogs.cleanup.mode", Component.translatable(key));
    }

    private static Component applyLabel(Mode mode) {
        return Component.translatable(mode == Mode.DELETE ? "allthelogs.cleanup.apply.delete" : "allthelogs.cleanup.apply.remove");
    }
}
