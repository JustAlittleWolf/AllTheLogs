package me.wolfii.allthelogs.client.ui.screen.browser;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
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
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Deletes stored messages, or cuts the search text out of them, for everything the filters match. It starts
 * with the messages screen's filters. Before anything is changed it shows how many messages are affected,
 * the oldest and newest of them, and a few picked at random, and the action needs a second click. Once
 * messages changed, the messages screen starts over, see {@link BrowserSession#reset()}.
 */
public final class CleanupScreen extends BaseOwoScreen<StackLayout> {
    private static final int SAMPLE_SIZE = 6;
    private static final int MAX_PREVIEW_CHARS = 300;
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
    private TextBoxComponent textBox;
    private ButtonComponent modeButton;
    private FlowLayout preview;
    private LabelComponent status;
    private ButtonComponent applyButton;
    private CleanupPreview current;
    private @Nullable Component notice;
    private boolean armed;
    private boolean busy;
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
        FlowLayout page = UIContainers.verticalFlow(Sizing.fill(), Sizing.fill());
        page.gap(6).padding(Insets.of(8));
        page.surface(Surface.blur(5, 10).and(PanelSurfaces.overlay()));
        page.horizontalAlignment(HorizontalAlignment.LEFT);
        page.verticalAlignment(VerticalAlignment.TOP);

        FlowLayout top = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        top.gap(4).verticalAlignment(VerticalAlignment.CENTER);
        textBox = UIComponents.textBox(Sizing.expand(), filter.text());
        textBox.setMaxLength(1024);
        textBox.setHint(Component.translatable("allthelogs.cleanup.text_hint"));
        textBox.onChanged().subscribe(text -> update(filter.withText(text)));
        top.child(textBox);
        modeButton = UIComponents.button(modeLabel(), pressed -> toggleMode());
        int modeWidth = Math.max(Minecraft.getInstance().font.width(modeLabel(Mode.DELETE)),
            Minecraft.getInstance().font.width(modeLabel(Mode.REMOVE_TEXT)));
        modeButton.horizontalSizing(Sizing.fixed(modeWidth + 12));
        top.child(modeButton);
        page.child(top);

        FlowLayout body = UIContainers.horizontalFlow(Sizing.fill(), Sizing.expand());
        body.gap(6);
        preview = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        preview.gap(2).padding(Insets.of(4));
        ScrollContainer<FlowLayout> previewScroll = UIContainers.verticalScroll(Sizing.expand(), Sizing.fill(), preview);
        previewScroll.scrollbar(OverflowScrollbar.vanillaFlat());
        previewScroll.surface(Surface.flat(Colors.LIST_BACKGROUND));
        body.child(previewScroll);
        page.child(body);
        filters = new FilterOverlay(body, overlays, () -> this.width, () -> this.height,
            () -> filter, catalog, this::update);

        FlowLayout bottom = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        bottom.gap(4).verticalAlignment(VerticalAlignment.CENTER);
        status = UIComponents.label(Component.empty());
        status.horizontalSizing(Sizing.expand());
        bottom.child(status);
        applyButton = UIComponents.button(Component.empty(), pressed -> apply());
        bottom.child(applyButton);
        bottom.child(DoneButton.of(this));
        page.child(bottom);

        root.child(page);
        filters.openDetached();
        refresh();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (this.minecraft == null || this.minecraft.level != null) return;
        this.extractPanorama(graphics, delta);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && busy) return true;
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (busy) return;
        Minecraft.getInstance().gui.setScreen(parent);
    }

    private void update(SearchFilter next) {
        filter = next;
        notice = null;
        if (textBox != null && !textBox.getValue().equals(next.text())) textBox.setValue(next.text());
        refresh();
    }

    private void toggleMode() {
        mode = mode == Mode.DELETE ? Mode.REMOVE_TEXT : Mode.DELETE;
        notice = null;
        modeButton.setMessage(modeLabel());
        refresh();
    }

    /**
     * Reloads the preview for the current filter and mode. A newer request makes older answers stale.
     */
    private void refresh() {
        armed = false;
        current = null;
        int request = ++requestToken;
        Component blocked = blockedReason();
        if (blocked != null) {
            showPreview(null);
            status.text(blocked);
            syncApply();
            return;
        }
        status.text(Component.translatable("allthelogs.cleanup.counting"));
        syncApply();
        AllTheLogsClient.worker().previewCleanup(filter.toSummaryQuery(), SAMPLE_SIZE).whenComplete(
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
                status.text(notice == null ? summary(result)
                    : Component.empty().append(notice).append("  ").append(summary(result)));
                syncApply();
            }));
    }

    private @Nullable Component blockedReason() {
        var worker = AllTheLogsClient.worker();
        if (worker == null || !worker.isOpen()) return Component.translatable("allthelogs.meta.unavailable");
        if (filter.invalidRegex()) return Component.translatable("allthelogs.cleanup.invalid_regex");
        if (mode == Mode.REMOVE_TEXT && !filter.hasText()) return Component.translatable("allthelogs.cleanup.need_text");
        return null;
    }

    private Component summary(CleanupPreview result) {
        if (result.matches() == 0) return Component.translatable("allthelogs.cleanup.none");
        String count = String.format("%,d", result.matches());
        if (mode == Mode.REMOVE_TEXT) return Component.translatable("allthelogs.cleanup.summary.remove", count);
        if (!filter.isNarrowed()) return Component.translatable("allthelogs.cleanup.summary.everything", count);
        return Component.translatable("allthelogs.cleanup.summary.delete", count);
    }

    private void showPreview(@Nullable CleanupPreview result) {
        preview.clearChildren();
        if (result == null || result.matches() == 0) return;
        Pattern removed = mode == Mode.REMOVE_TEXT ? filter.removalPattern().orElse(null) : null;
        section("allthelogs.cleanup.first", List.of(result.first()), removed);
        section("allthelogs.cleanup.last", List.of(result.last()), removed);
        section("allthelogs.cleanup.sample", result.sample(), removed);
    }

    private void section(String key, List<ChatEntry> entries, @Nullable Pattern removed) {
        preview.child(UIComponents.label(Component.translatable(key).withStyle(ChatFormatting.GRAY)));
        for (ChatEntry entry : entries) {
            LabelComponent line = UIComponents.label(line(entry, removed));
            line.horizontalSizing(Sizing.fill());
            line.margins(Insets.left(6));
            preview.child(line);
        }
    }

    /**
     * Timestamp and message. In remove mode the parts that would be cut are red and struck through.
     */
    private static Component line(ChatEntry entry, @Nullable Pattern removed) {
        MutableComponent line = Component.literal(TIME.format(entry.timestamp()) + "  ")
            .withColor(Colors.TIMESTAMP & 0xFFFFFF);
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
        if (applyButton == null) return;
        long matches = current == null ? 0 : current.matches();
        String count = String.format("%,d", matches);
        Component label;
        if (busy) label = Component.translatable("allthelogs.cleanup.working");
        else if (armed) label = Component.translatable("allthelogs.cleanup.confirm").withStyle(ChatFormatting.RED);
        else if (mode == Mode.REMOVE_TEXT) label = Component.translatable("allthelogs.cleanup.apply.remove", count);
        else label = Component.translatable("allthelogs.cleanup.apply.delete", count);
        applyButton.setMessage(label);
        applyButton.active = !busy && matches > 0;
    }

    private void apply() {
        if (busy || current == null || current.matches() == 0) return;
        if (!armed) {
            armed = true;
            syncApply();
            return;
        }
        Optional<Pattern> part = mode == Mode.REMOVE_TEXT ? filter.removalPattern() : Optional.empty();
        if (mode == Mode.REMOVE_TEXT && part.isEmpty()) return;
        busy = true;
        armed = false;
        syncApply();
        status.text(Component.translatable("allthelogs.cleanup.working"));
        AllTheLogsClient.worker().cleanUp(filter.toSummaryQuery(), part.orElse(null)).whenComplete(
            (outcome, error) -> Minecraft.getInstance().execute(() -> finished(outcome, error)));
    }

    private void finished(MessageCleanup.Outcome outcome, Throwable error) {
        busy = false;
        if (Minecraft.getInstance().gui.screen() != this) {
            if (error == null) BrowserSession.reset();
            return;
        }
        if (error != null) {
            AllTheLogsClient.LOGGER.warn("Could not clean up messages", error);
            status.text(Component.translatable("allthelogs.status.error"));
            syncApply();
            return;
        }
        BrowserSession.reset();
        notice = Component.translatable("allthelogs.cleanup.done",
            String.format("%,d", outcome.changed()), String.format("%,d", outcome.deleted())).withStyle(ChatFormatting.GREEN);
        refresh();
    }

    private Component modeLabel() {
        return modeLabel(mode);
    }

    private static Component modeLabel(Mode mode) {
        String key = mode == Mode.DELETE ? "allthelogs.cleanup.mode.delete" : "allthelogs.cleanup.mode.remove";
        return Component.translatable("allthelogs.cleanup.mode", Component.translatable(key));
    }
}
