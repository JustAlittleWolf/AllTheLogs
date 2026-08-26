package me.wolfii.allthelogs.client.ui;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.*;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.*;
import me.wolfii.allthelogs.client.AllTheLogsClient;
import me.wolfii.allthelogs.client.search.ChatQueryFactory;
import me.wolfii.allthelogs.client.search.DateParsers;
import me.wolfii.allthelogs.client.search.SearchFilter;
import me.wolfii.allthelogs.client.view.DisplayRow;
import me.wolfii.allthelogs.client.view.EntryClassifier;
import me.wolfii.allthelogs.client.view.ResultWindow;
import me.wolfii.allthelogs.data.ChatEntry;
import me.wolfii.allthelogs.data.ChatQuery;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Transparent log browser. Search, filter, virtualised history, and a timeline of every hit.
 */
public final class LogBrowserScreen extends BaseOwoScreen<FlowLayout> {
    private final AtomicInteger queryGeneration = new AtomicInteger();
    private SearchFilter filter;
    private TimelineLogList list;
    private LabelComponent status;
    private FlowLayout filterPanel;
    private boolean filterOpen;

    public LogBrowserScreen() {
        super(Component.translatable("allthelogs.screen.browser"));
        this.filter = AllTheLogsClient.settings().toFilter();
    }

    private static ChatQuery.Sort opposite(ChatQuery.Sort sort) {
        return sort == ChatQuery.Sort.ASCENDING ? ChatQuery.Sort.DESCENDING : ChatQuery.Sort.ASCENDING;
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override
    protected void build(FlowLayout root) {
        root.gap(6);
        root.surface(Surface.VANILLA_TRANSLUCENT)
            .padding(Insets.of(8))
            .horizontalAlignment(HorizontalAlignment.LEFT)
            .verticalAlignment(VerticalAlignment.TOP);

        root.child(buildToolbar(root));

        list = new TimelineLogList();
        list.onApproachEdge(this::loadMore);
        list.onJump(this::jumpTo);
        root.child(list.verticalSizing(Sizing.expand()));

        FlowLayout bottom = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        bottom.gap(8).verticalAlignment(VerticalAlignment.CENTER);
        bottom.child(UIComponents.button(Component.translatable("allthelogs.import.button"),
            button -> Minecraft.getInstance().gui.setScreen(new ImportScreen(this))));
        status = UIComponents.label(Component.empty());
        bottom.child(status);
        root.child(bottom);

        reload(true);
    }

    private FlowLayout buildToolbar(FlowLayout root) {
        FlowLayout bar = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        bar.gap(4).verticalAlignment(VerticalAlignment.CENTER);

        TextBoxComponent search = UIComponents.textBox(Sizing.expand(), filter.text());
        search.setHint(Component.translatable("allthelogs.search.placeholder"));
        search.setMaxLength(256);
        search.onChanged().subscribe(this::onSearchChanged);
        bar.child(search);

        bar.child(UIComponents.button(Component.translatable("allthelogs.filter"), button -> toggleFilter(root, button)));
        return bar;
    }

    private void onSearchChanged(String text) {
        filter = filter.withText(text).withoutOffset();
        int generation = queryGeneration.incrementAndGet();
        CompletableFuture.delayedExecutor(200, TimeUnit.MILLISECONDS).execute(() -> {
            if (generation == queryGeneration.get()) {
                Minecraft.getInstance().execute(() -> reload(true));
            }
        });
    }

    private void toggleFilter(FlowLayout root, ButtonComponent button) {
        if (filterOpen) {
            closeFilter(root);
            return;
        }
        filterOpen = true;
        filterPanel = buildFilterPanel(root);
        filterPanel.positioning(Positioning.absolute(
            Math.max(8, this.width - 248),
            button.y() + button.height() + 4));
        root.child(filterPanel);
    }

    private void closeFilter(FlowLayout root) {
        if (filterPanel != null) {
            root.removeChild(filterPanel);
            filterPanel = null;
        }
        filterOpen = false;
    }

    private FlowLayout buildFilterPanel(FlowLayout root) {
        FlowLayout panel = UIContainers.verticalFlow(Sizing.fixed(230), Sizing.content());
        panel.padding(Insets.of(8));
        panel.gap(4);
        panel.surface(Surface.flat(0xE0101010).and(Surface.outline(0xFF3C3C3C)));

        CheckboxComponent regex = UIComponents.checkbox(Component.translatable("allthelogs.filter.regex"));
        regex.checked(filter.regex());
        regex.onChanged(value -> {
            filter = filter.withRegex(value).withoutOffset();
            persistAndReload();
        });
        panel.child(regex);

        CheckboxComponent caseSensitive = UIComponents.checkbox(Component.translatable("allthelogs.filter.case_sensitive"));
        caseSensitive.checked(filter.caseSensitive());
        caseSensitive.onChanged(value -> {
            filter = filter.withCaseSensitive(value).withoutOffset();
            persistAndReload();
        });
        panel.child(caseSensitive);

        panel.child(labeledField("allthelogs.filter.context", String.valueOf(filter.contextLines()), text -> {
            try {
                filter = filter.withContextLines(Integer.parseInt(text.trim())).withoutOffset();
                persistAndReload();
            } catch (RuntimeException ignored) {
            }
        }));

        panel.child(labeledField("allthelogs.filter.limit", String.valueOf(filter.limit()), text -> {
            try {
                int limit = Integer.parseInt(text.trim());
                if (limit > 0) {
                    filter = filter.withLimit(limit).withoutOffset();
                    persistAndReload();
                }
            } catch (RuntimeException ignored) {
            }
        }));

        panel.child(UIComponents.label(Component.translatable("allthelogs.filter.sort")));
        FlowLayout sortRow = UIContainers.horizontalFlow(Sizing.fill(), Sizing.content());
        sortRow.gap(4);
        sortRow.child(UIComponents.button(Component.translatable("allthelogs.filter.sort.ascending"), b -> {
            filter = filter.withSort(ChatQuery.Sort.ASCENDING).withoutOffset();
            persistAndReload();
        }));
        sortRow.child(UIComponents.button(Component.translatable("allthelogs.filter.sort.descending"), b -> {
            filter = filter.withSort(ChatQuery.Sort.DESCENDING).withoutOffset();
            persistAndReload();
        }));
        panel.child(sortRow);

        panel.child(labeledField("allthelogs.filter.from",
            filter.startingAt() == null ? "" : filter.startingAt().toString().replace('T', ' '),
            text -> {
                if (!DateParsers.isBlankOrValid(text)) return;
                filter = filter.withStartingAt(DateParsers.parse(text).orElse(null)).withoutOffset();
                persistAndReload();
            }));
        panel.child(labeledField("allthelogs.filter.until",
            filter.upUntil() == null ? "" : filter.upUntil().toString().replace('T', ' '),
            text -> {
                if (!DateParsers.isBlankOrValid(text)) return;
                filter = filter.withUpUntil(DateParsers.parse(text).orElse(null)).withoutOffset();
                persistAndReload();
            }));
        panel.child(UIComponents.label(Component.translatable("allthelogs.filter.date_hint")).color(io.wispforest.owo.ui.core.Color.ofRgb(0x888888)));

        return panel;
    }

    private FlowLayout labeledField(String key, String value, java.util.function.Consumer<String> onChange) {
        FlowLayout row = UIContainers.verticalFlow(Sizing.fill(), Sizing.content());
        row.gap(2);
        row.child(UIComponents.label(Component.translatable(key)));
        TextBoxComponent box = UIComponents.textBox(Sizing.fill(), value);
        box.setMaxLength(32);
        box.onChanged().subscribe(text -> onChange.accept(text));
        row.child(box);
        return row;
    }

    private void persistAndReload() {
        AllTheLogsClient.settings().apply(filter);
        AllTheLogsClient.saveSettings();
        reload(true);
    }

    private void reload(boolean resetTimeline) {
        if (list == null) return;
        int generation = queryGeneration.incrementAndGet();
        list.setLoading(true);
        status.text(Component.translatable("allthelogs.status.loading"));
        SearchFilter page = filter.withoutOffset();
        AllTheLogsClient.worker().query(ChatQueryFactory.toQuery(page)).whenComplete((entries, error) -> {
            Minecraft.getInstance().execute(() -> {
                if (generation != queryGeneration.get()) return;
                list.setLoading(false);
                if (error != null) {
                    AllTheLogsClient.LOGGER.warn("AllTheLogs query failed", error);
                    status.text(Component.translatable("allthelogs.status.error"));
                    return;
                }
                List<DisplayRow> rows = EntryClassifier.classify(entries, page);
                boolean hasAfter = ResultWindow.matchCount(rows) >= page.limit() && page.limit() > 0;
                list.reset(rows, false, hasAfter);
                status.text(Component.literal(rows.size() + " lines"));
            });
        });
        if (resetTimeline) {
            AllTheLogsClient.worker().query(ChatQueryFactory.toTimelineQuery(page)).whenComplete((entries, error) -> {
                Minecraft.getInstance().execute(() -> {
                    if (error != null || list == null) return;
                    List<LocalDateTime> times = new ArrayList<>(entries.size());
                    for (ChatEntry entry : entries) {
                        times.add(entry.timestamp());
                    }
                    list.setMarkers(times);
                });
            });
        }
    }

    private void loadMore(TimelineLogList.Edge edge) {
        if (list.loading() || list.window().rows().isEmpty()) return;
        list.setLoading(true);
        LocalDateTime cursor = edge == TimelineLogList.Edge.AFTER
            ? list.window().lastMatchTime()
            : list.window().firstMatchTime();
        if (cursor == null) {
            list.setLoading(false);
            return;
        }
        boolean flip = edge == TimelineLogList.Edge.BEFORE;
        SearchFilter page = filter.withOffset(cursor);
        if (flip) {
            page = page.withSort(opposite(filter.sort()));
        }
        DisplayRow.RowKey anchor = list.window().keyAtPixel(list.scrollY(), TimelineLogList.ROW_HEIGHT);
        int firstVisible = (int) Math.floor(list.scrollY() / TimelineLogList.ROW_HEIGHT);
        int lastVisible = firstVisible + Math.max(1, list.height() / TimelineLogList.ROW_HEIGHT);
        AllTheLogsClient.worker().query(ChatQueryFactory.toQuery(page)).whenComplete((entries, error) -> {
            Minecraft.getInstance().execute(() -> {
                list.setLoading(false);
                if (error != null) {
                    AllTheLogsClient.LOGGER.warn("AllTheLogs page query failed", error);
                    return;
                }
                List<DisplayRow> incoming = EntryClassifier.classify(entries, filter);
                if (flip) incoming = ResultWindow.reversed(incoming);
                boolean more = ResultWindow.matchCount(incoming) >= filter.limit() && filter.limit() > 0;
                List<DisplayRow> older = edge == TimelineLogList.Edge.AFTER ? list.window().rows() : incoming;
                List<DisplayRow> newer = edge == TimelineLogList.Edge.AFTER ? incoming : list.window().rows();
                List<DisplayRow> merged = ResultWindow.mergeUnique(older, newer);
                List<DisplayRow> trimmed = ResultWindow.trimToMatchLimit(
                    merged, (int) Math.max(1, filter.limit()), firstVisible, lastVisible);
                boolean hasBefore = list.window().hasBefore() || (edge == TimelineLogList.Edge.BEFORE && more);
                boolean hasAfter = list.window().hasAfter() || (edge == TimelineLogList.Edge.AFTER && more);
                if (edge == TimelineLogList.Edge.BEFORE && !more) hasBefore = false;
                if (edge == TimelineLogList.Edge.AFTER && !more) hasAfter = false;
                list.applyPage(trimmed, hasBefore, hasAfter, anchor);
            });
        });
    }

    private void jumpTo(LocalDateTime time) {
        SearchFilter page = filter.withOffset(time.minusNanos(1));
        list.setLoading(true);
        AllTheLogsClient.worker().query(ChatQueryFactory.toQuery(page)).whenComplete((entries, error) -> {
            Minecraft.getInstance().execute(() -> {
                list.setLoading(false);
                if (error != null) return;
                List<DisplayRow> rows = EntryClassifier.classify(entries, filter);
                boolean hasAfter = ResultWindow.matchCount(rows) >= filter.limit() && filter.limit() > 0;
                list.reset(rows, true, hasAfter);
            });
        });
    }
}
