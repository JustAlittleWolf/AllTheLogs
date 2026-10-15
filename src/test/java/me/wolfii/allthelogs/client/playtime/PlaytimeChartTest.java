package me.wolfii.allthelogs.client.playtime;

import me.wolfii.allthelogs.data.playtime.PlaytimeSpan;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaytimeChartTest {
    private static LocalDateTime at(int day, int hour) {
        return LocalDateTime.of(2024, 1, day, hour, 0);
    }

    private static PlaytimeSpan span(int day, int hours, String version, String server) {
        return new PlaytimeSpan(at(day, 0), at(day, hours), version, server);
    }

    @Test
    void durationDropsSecondsOnceAnHourIsReached() {
        assertEquals("0s", PlaytimeDuration.format(0));
        assertEquals("45s", PlaytimeDuration.format(45_000));
        assertEquals("1m 30s", PlaytimeDuration.format(90_000));
        assertEquals("1h 1m", PlaytimeDuration.format(3_660_000));
        assertEquals("1d 1h", PlaytimeDuration.format(90_000_000));
    }

    @Test
    void autoGrainFollowsTheVisibleSpan() {
        assertEquals(PlaytimeGrain.DAYS, PlaytimeGrain.resolve(PlaytimeGrain.AUTO, at(1, 0), at(1, 0).plusDays(62)));
        assertEquals(PlaytimeGrain.WEEKS, PlaytimeGrain.resolve(PlaytimeGrain.AUTO, at(1, 0), at(1, 0).plusDays(63)));
        assertEquals(PlaytimeGrain.MONTHS, PlaytimeGrain.resolve(PlaytimeGrain.AUTO, at(1, 0), at(1, 0).plusDays(731)));
        assertEquals(PlaytimeGrain.DAYS, PlaytimeGrain.resolve(PlaytimeGrain.DAYS, at(1, 0), at(1, 0).plusDays(800)));
    }

    @Test
    void barsStackOldestVersionThenServerAndLegendKeepsTheTenLongest() {
        List<PlaytimeSpan> spans = List.of(
            span(1, 1, "1.21", "b"),
            span(1, 3, "1.20.4", "a"),
            span(2, 2, "1.21", "a")
        );
        PlaytimeChartModel.Result chart = PlaytimeChartModel.build(spans, PlaytimeFilter.all(),
            PlaytimeBreakdown.COMBINED, PlaytimeStyle.BARS, PlaytimeGrain.DAYS, null);

        assertEquals(6 * 3_600_000L, chart.totalMillis());
        assertEquals(List.of("1.20.4", "1.21", "1.21"),
            chart.series().stream().map(PlaytimeSeries::version).toList());
        assertEquals(List.of("a", "a", "b"), chart.series().stream().map(PlaytimeSeries::serverKey).toList());
        assertEquals(2, chart.columns().size());
        assertEquals(3 * 3_600_000L, chart.columns().getFirst().millis()[0]);
        assertEquals(0L, chart.columns().getFirst().millis()[1]);
        assertEquals(3_600_000L, chart.columns().getFirst().millis()[2]);

        List<PlaytimeSpan> many = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            many.add(new PlaytimeSpan(at(1, 0), at(1, 0).plusMinutes(index + 1), "1." + index, "srv"));
        }
        PlaytimeChartModel.Result legend = PlaytimeChartModel.build(many, PlaytimeFilter.all(),
            PlaytimeBreakdown.VERSION, PlaytimeStyle.BARS, PlaytimeGrain.DAYS, null);
        assertEquals(10, legend.legend().size());
        assertEquals("1.10", legend.series().get(legend.legend().getFirst()).version());
    }

    @Test
    void filtersApplyTogetherAndCumulativeKeepsHistoryOutsideTheView() {
        List<PlaytimeSpan> spans = List.of(span(1, 1, "1.20.4", "a"), span(2, 2, "1.21", "b"));
        PlaytimeFilter filter = PlaytimeFilter.all().withVersions(java.util.Set.of("1.20.4")).withServers(java.util.Set.of("a"));
        PlaytimeChartModel.Result filtered = PlaytimeChartModel.build(spans, filter, PlaytimeBreakdown.VERSION,
            PlaytimeStyle.BARS, PlaytimeGrain.DAYS, null);
        assertEquals(3_600_000L, filtered.totalMillis());
        assertEquals(1, filtered.columns().size());

        PlaytimeWindow view = new PlaytimeWindow(at(2, 0), at(2, 2));
        PlaytimeChartModel.Result cumulative = PlaytimeChartModel.build(spans, PlaytimeFilter.all(),
            PlaytimeBreakdown.VERSION, PlaytimeStyle.CUMULATIVE, PlaytimeGrain.DAYS, view);
        assertEquals(1, cumulative.columns().size());
        assertEquals(3_600_000d, cumulative.columns().getFirst().display()[0]);
        assertEquals(2 * 3_600_000d, cumulative.columns().getFirst().display()[1]);
    }

    @Test
    void percentColumnIsEmptyWhenTheBucketHasNoPlaytime() {
        List<PlaytimeSpan> spans = List.of(span(1, 1, "1.20.4", null), span(3, 1, "1.21", null));
        PlaytimeChartModel.Result chart = PlaytimeChartModel.build(spans, PlaytimeFilter.all(),
            PlaytimeBreakdown.VERSION, PlaytimeStyle.PERCENT, PlaytimeGrain.DAYS, null);
        PlaytimeChartModel.Column empty = chart.columns().get(1);
        assertEquals(at(2, 0), empty.start());
        assertEquals(0L, empty.totalMillis());
        assertEquals(0d, empty.display()[0]);
        assertEquals(0d, empty.display()[1]);
        PlaytimeChartModel.Column third = chart.columns().get(2);
        assertEquals(0d, third.display()[0]);
        assertEquals(1d, third.display()[1]);
    }

    @Test
    void hoverListsTheBucketAndEveryCategoryInIt() {
        PlaytimeChartModel.Result chart = PlaytimeChartModel.build(
            List.of(span(1, 1, "1.20.4", "a"), span(1, 3, "1.21", "b")),
            PlaytimeFilter.all(), PlaytimeBreakdown.VERSION, PlaytimeStyle.BARS, PlaytimeGrain.DAYS, null);
        List<String> lines = chart.hover(0, "Unknown", "Total").stream().map(PlaytimeChartModel.HoverLine::text).toList();
        assertEquals("2024-01-01", lines.getFirst());
        assertTrue(lines.contains("1.20.4  1h"));
        assertTrue(lines.contains("1.21  3h"));
        assertEquals("Total  4h", lines.getLast());
    }

    @Test
    void hitPicksTheUpperSectionOfAStack() {
        PlaytimeChartModel.Result chart = PlaytimeChartModel.build(
            List.of(span(1, 3, "1.20.4", null), span(1, 1, "1.21", null)),
            PlaytimeFilter.all(), PlaytimeBreakdown.VERSION, PlaytimeStyle.BARS, PlaytimeGrain.DAYS, null);
        PlaytimeChartModel.Plot plot = new PlaytimeChartModel.Plot(0, 0, 100, 100);
        PlaytimeChartModel.Hit top = chart.hit(50, 1, plot);
        PlaytimeChartModel.Hit bottom = chart.hit(50, 90, plot);
        assertEquals(1, top.series());
        assertEquals(0, bottom.series());
        assertNull(chart.hit(50, -1, plot));
    }

    @Test
    void zoomStaysInsideTheDomainAndPanClamps() {
        PlaytimeWindow domain = new PlaytimeWindow(at(1, 0), at(31, 0));
        PlaytimeWindow zoomed = PlaytimeWindow.zoom(domain, domain, 0, 1);
        assertEquals(at(1, 0), zoomed.start());
        assertEquals(at(1, 0).plusDays(24), zoomed.end());
        PlaytimeWindow out = PlaytimeWindow.zoom(domain, domain, 0.5, -4);
        assertEquals(domain, out);
        PlaytimeWindow middle = new PlaytimeWindow(at(5, 0), at(15, 0));
        PlaytimeWindow panned = PlaytimeWindow.pan(domain, middle, 0.5);
        assertEquals(at(10, 0), panned.start());
        assertEquals(at(20, 0), panned.end());
        PlaytimeWindow stuck = PlaytimeWindow.pan(domain, middle, -5);
        assertEquals(domain.start(), stuck.start());
    }

    @Test
    void weekdaySplitsMidnightAndReportsMostAndLeast() {
        PlaytimeSpan overnight = new PlaytimeSpan(
            LocalDateTime.of(2024, 1, 7, 23, 0),
            LocalDateTime.of(2024, 1, 8, 1, 0),
            "1.20.4", "a");
        assertEquals(DayOfWeek.SUNDAY, overnight.start().getDayOfWeek());
        PlaytimeWeekday.Result days = PlaytimeWeekday.build(List.of(overnight, span(2, 3, "1.21", "b")),
            PlaytimeFilter.all(), PlaytimeBreakdown.SERVER);
        assertEquals(5 * 3_600_000L, days.totalMillis());
        assertEquals(3_600_000L, days.bars().get(DayOfWeek.SUNDAY.getValue() - 1).totalMillis());
        assertEquals(3_600_000L, days.bars().get(DayOfWeek.MONDAY.getValue() - 1).totalMillis());
        assertEquals(3 * 3_600_000L, days.bars().get(DayOfWeek.TUESDAY.getValue() - 1).totalMillis());
        assertEquals(DayOfWeek.TUESDAY, days.bars().get(days.mostDay()).day());
        assertEquals(DayOfWeek.WEDNESDAY, days.bars().get(days.leastDay()).day());
        assertEquals(List.of("a", "b"), days.series().stream().map(PlaytimeSeries::serverKey).toList());
    }

    @Test
    void messageFilterSetsThePeriodAndTheActiveCategory() {
        var current = me.wolfii.allthelogs.client.search.SearchFilter.defaults()
            .withText("hi").withVersion("old").withServerOrWorld("old-server");
        var period = PlaytimeMessageFilter.period(current, at(1, 0), at(2, 0));
        assertEquals("hi", period.text());
        assertEquals(at(1, 0), period.startingAt());
        assertEquals(at(2, 0), period.upUntil());
        assertNull(period.version());
        assertNull(period.serverOrWorld());

        PlaytimeSeries both = PlaytimeSeries.of(PlaytimeBreakdown.COMBINED, "1.21", "hypixel.net");
        var category = PlaytimeMessageFilter.category(current, at(1, 0), at(2, 0), PlaytimeBreakdown.COMBINED, both);
        assertEquals("1.21", category.version());
        assertEquals("hypixel.net", category.serverOrWorld());
    }
}
