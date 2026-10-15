package me.wolfii.allthelogs.client.ui.screen;

import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import me.wolfii.allthelogs.client.playtime.PlaytimeFilter;
import me.wolfii.allthelogs.client.playtime.PlaytimeSeries;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Up to {@link me.wolfii.allthelogs.client.playtime.PlaytimeChartModel#LEGEND_LIMIT} swatches, most playtime first.
 */
final class PlaytimeLegend {
    private PlaytimeLegend() {
    }

    static void fill(FlowLayout legend, List<PlaytimeSeries> series, List<Integer> indexes) {
        legend.clearChildren();
        String unknown = Component.translatable("allthelogs.playtime.server.unknown").getString();
        for (int index : indexes) {
            if (index < 0 || index >= series.size()) continue;
            PlaytimeSeries entry = series.get(index);
            FlowLayout chip = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
            chip.gap(3).verticalAlignment(VerticalAlignment.CENTER);
            BoxComponent swatch = UIComponents.box(Sizing.fixed(10), Sizing.fixed(10));
            swatch.fill(true).color(Color.ofRgb(entry.color() & 0xFFFFFF));
            chip.child(swatch);
            chip.child(UIComponents.label(Component.literal(entry.label(unknown))));
            legend.child(chip);
        }
    }

    static Component named(String value) {
        if (PlaytimeFilter.UNKNOWN_SERVER.equals(value)) {
            return Component.translatable("allthelogs.playtime.server.unknown");
        }
        return Component.literal(value);
    }

    static Component selection(String key, java.util.Set<String> selected, List<String> all) {
        if (selected == null) return Component.translatable(key, Component.translatable("allthelogs.playtime.filter.all"));
        if (selected.isEmpty()) return Component.translatable(key, Component.translatable("allthelogs.playtime.filter.none"));
        if (selected.size() == 1) return Component.translatable(key, named(selected.iterator().next()));
        return Component.translatable(key, Component.translatable("allthelogs.playtime.filter.count", selected.size()));
    }
}
