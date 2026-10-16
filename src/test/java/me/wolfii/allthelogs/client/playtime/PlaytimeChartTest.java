package me.wolfii.allthelogs.client.playtime;

import me.wolfii.allthelogs.data.playtime.PlaytimeSpan;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
        assertEquals(PlaytimeGrain.DAYS, PlaytimeGrain.resolve(PlaytimeGrain.AUTO, at(1, 0), at(1, 0).plusDays(45)));
        assertEquals(PlaytimeGrain.WEEKS, PlaytimeGrain.resolve(PlaytimeGrain.AUTO, at(1, 0), at(1, 0).plusDays(46)));
        assertEquals(PlaytimeGrain.MONTHS, PlaytimeGrain.resolve(PlaytimeGrain.AUTO, at(1, 0), at(1, 0).plusDays(367)));
        assertEquals(PlaytimeGrain.YEARS, PlaytimeGrain.resolve(PlaytimeGrain.AUTO, at(1, 0), at(1, 0).plusDays(3_661)));
        assertEquals(PlaytimeGrain.DAYS, PlaytimeGrain.resolve(PlaytimeGrain.DAYS, at(1, 0), at(1, 0).plusDays(800)));
    }

    @Test
    void barsStackOldestVersionThenServerAndLegendListsEverySeriesLongestFirst() {
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
        assertEquals(11, legend.legend().size());
        assertEquals("1.10", legend.series().get(legend.legend().getFirst().series()).version());
        assertEquals(11 * 60_000L, legend.legend().getFirst().millis());
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
    void hoverPutsTheTotalFirstAndSortsCategoriesByPlaytime() {
        PlaytimeChartModel.Result chart = PlaytimeChartModel.build(
            List.of(span(1, 1, "1.20.4", "a"), span(1, 3, "1.21", "b")),
            PlaytimeFilter.all(), PlaytimeBreakdown.VERSION, PlaytimeStyle.BARS, PlaytimeGrain.DAYS, null);
        List<PlaytimeHover.Line> lines = chart.hover(0, "Unknown");
        assertEquals(List.of("2024-01-01", "4h", "", "1.21", "1.20.4"),
            lines.stream().map(PlaytimeHover.Line::label).toList());
        assertEquals(List.of("", "", "", "3h", "1h"), lines.stream().map(PlaytimeHover.Line::value).toList());
        assertTrue(lines.get(1).bold());
    }

    @Test
    void serverNamesLoseFormattingAndLongNamesAreCut() {
        PlaytimeSeries series = PlaytimeSeries.of(PlaytimeBreakdown.SERVER, "1.21", "\u00a76Gold \u00a7lWorld");
        assertEquals("Gold World", series.label("Unknown"));
        String cut = PlaytimeSeries.of(PlaytimeBreakdown.SERVER, "1.21", "x".repeat(40)).shortLabel("Unknown");
        assertEquals(PlaytimeSeries.MAX_LABEL_LENGTH, cut.length());
        assertTrue(cut.endsWith("\u2026"));
    }

    @Test
    void colorsFollowFirstAppearanceAndStayWhenFiltered() {
        List<PlaytimeSpan> spans = List.of(span(1, 1, "1.20.4", "a"), span(2, 1, "1.21", "b"), span(3, 1, "1.21.1", null));
        PlaytimeChartModel.Result all = PlaytimeChartModel.build(spans, PlaytimeFilter.all(),
            PlaytimeBreakdown.VERSION, PlaytimeStyle.BARS, PlaytimeGrain.DAYS, null);
        assertEquals(PlaytimeColors.ofRank(0), all.series().get(0).color());
        assertEquals(PlaytimeColors.ofRank(1), all.series().get(1).color());
        PlaytimeChartModel.Result only = PlaytimeChartModel.build(spans,
            PlaytimeFilter.all().withVersions(Set.of("1.21")),
            PlaytimeBreakdown.VERSION, PlaytimeStyle.BARS, PlaytimeGrain.DAYS, null);
        assertEquals(PlaytimeColors.ofRank(1), only.series().getFirst().color());
        for (int rank = 0; rank < 8; rank++) {
            double gap = Math.abs(hue(PlaytimeColors.ofRank(rank)) - hue(PlaytimeColors.ofRank(rank + 1)));
            assertTrue(Math.min(gap, 1 - gap) > 0.2, "neighboring ranks need distinct hues");
        }

        PlaytimeChartModel.Result servers = PlaytimeChartModel.build(spans, PlaytimeFilter.all(),
            PlaytimeBreakdown.SERVER, PlaytimeStyle.BARS, PlaytimeGrain.DAYS, null);
        assertEquals(PlaytimeColors.UNKNOWN, servers.series().getLast().color());
    }

    private static double hue(int argb) {
        return java.awt.Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null)[0];
    }

    @Test
    void barLayoutKeepsEqualWidthsOnlyWhenLittleSpaceIsLeft() {
        BarLayout even = BarLayout.of(3, 10, 100);
        assertEquals(33, even.barRight(0) - even.barLeft(0));
        assertEquals(10, even.left());
        assertEquals(0, even.columnAt(10));
        assertEquals(2, even.columnAt(10 + 98));
        assertEquals(-1, even.columnAt(10 + 99));

        BarLayout uneven = BarLayout.of(60, 0, 100);
        assertEquals(0, uneven.left());
        assertEquals(100, uneven.right());
        for (int column = 0; column < 60; column++) {
            int width = uneven.barRight(column) - uneven.barLeft(column);
            assertTrue(width == 1 || width == 2);
        }

        BarLayout crowded = BarLayout.of(4, 0, 2);
        int[] drawn = crowded.drawnAt(new double[]{1, 5, 2, 3});
        assertEquals(2, drawn.length);
        assertEquals(1, drawn[0]);
        assertEquals(3, drawn[1]);
    }

    @Test
    void axisLabelsUseClockTimeOnceDaysAreReached() {
        assertEquals("6h 36m", PlaytimeDuration.axis(6 * 3_600_000L + 36 * 60_000L));
        assertEquals("2d 06:05", PlaytimeDuration.axis(2 * 86_400_000L + 6 * 3_600_000L + 5 * 60_000L));
    }

    @Test
    void neighboringColorGroupsGetDifferentPatterns() {
        assertEquals(PlaytimePattern.SOLID, PlaytimeColors.pattern(0));
        assertEquals(PlaytimePattern.SOLID, PlaytimeColors.pattern(2));
        assertTrue(PlaytimeColors.pattern(2) != PlaytimeColors.pattern(5));
        assertTrue(PlaytimeColors.pattern(5) != PlaytimeColors.pattern(8));
    }

    @Test
    void yearsBucketByCalendarYear() {
        List<PlaytimeSpan> spans = List.of(span(1, 1, "1.21", null),
            new PlaytimeSpan(LocalDateTime.of(2025, 6, 1, 0, 0), LocalDateTime.of(2025, 6, 1, 2, 0), "1.21", null));
        assertTrue(PlaytimeChartModel.spansSeveralYears(spans));
        PlaytimeChartModel.Result years = PlaytimeChartModel.build(spans, PlaytimeFilter.all(),
            PlaytimeBreakdown.VERSION, PlaytimeStyle.BARS, PlaytimeGrain.YEARS, null);
        assertEquals(2, years.columns().size());
        assertEquals("2024", PlaytimeChartModel.spanLabel(years.columns().getFirst().start(),
            years.columns().getFirst().end(), PlaytimeGrain.YEARS));
        PlaytimeChartModel.Result single = PlaytimeChartModel.build(List.of(span(1, 1, "1.21", null)),
            PlaytimeFilter.all(), PlaytimeBreakdown.VERSION, PlaytimeStyle.BARS, PlaytimeGrain.YEARS, null);
        assertEquals(PlaytimeGrain.MONTHS, single.grain());
    }

    @Test
    void hitPicksTheUpperSectionOfAStack() {
        PlaytimeChartModel.Result chart = PlaytimeChartModel.build(
            List.of(span(1, 3, "1.20.4", null), span(1, 1, "1.21", null)),
            PlaytimeFilter.all(), PlaytimeBreakdown.VERSION, PlaytimeStyle.BARS, PlaytimeGrain.DAYS, null);
        PlaytimeChartModel.Plot plot = new PlaytimeChartModel.Plot(0, 0, 100, 100);
        PlaytimeChartModel.Hit top = chart.hit(0, 1, plot);
        PlaytimeChartModel.Hit bottom = chart.hit(0, 90, plot);
        assertEquals(1, top.series());
        assertEquals(0, bottom.series());
        assertNull(chart.hit(0, -1, plot));
        assertNull(chart.hit(1, 50, plot));
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
    void weekdaySplitsMidnightAndFillsTheHourHeatMap() {
        PlaytimeSpan overnight = new PlaytimeSpan(
            LocalDateTime.of(2024, 1, 7, 23, 0),
            LocalDateTime.of(2024, 1, 8, 1, 0),
            "1.20.4", "a");
        assertEquals(DayOfWeek.SUNDAY, overnight.start().getDayOfWeek());
        PlaytimeWeekday.Result days = PlaytimeWeekday.build(List.of(overnight, span(2, 3, "1.21", "b")),
            PlaytimeFilter.all(), PlaytimeBreakdown.SERVER, null, null);
        assertEquals(5 * 3_600_000L, days.totalMillis());
        assertEquals(3_600_000L, days.bars().get(DayOfWeek.SUNDAY.getValue() - 1).totalMillis());
        assertEquals(3_600_000L, days.bars().get(DayOfWeek.MONDAY.getValue() - 1).totalMillis());
        assertEquals(3 * 3_600_000L, days.bars().get(DayOfWeek.TUESDAY.getValue() - 1).totalMillis());
        assertEquals(List.of("a", "b"), days.series().stream().map(PlaytimeSeries::serverKey).toList());
        assertEquals(3_600_000L, days.heatMillis(DayOfWeek.SUNDAY.getValue() - 1, 23));
        assertEquals(3_600_000L, days.heatMillis(DayOfWeek.MONDAY.getValue() - 1, 0));
        assertEquals(2 * 3_600_000L, days.heatMillis(PlaytimeWeekday.ALL_DAYS, 0));

        List<PlaytimeHover.Line> hover = days.dayHover(DayOfWeek.TUESDAY.getValue() - 1, "Unknown");
        assertEquals("3h", hover.get(1).label());
        assertEquals("", hover.get(2).label());
        assertEquals("b", hover.get(3).label());
        assertEquals("3h", hover.get(3).value());
    }

    @Test
    void weekdayPeriodClipsSpans() {
        PlaytimeWeekday.Result clipped = PlaytimeWeekday.build(List.of(span(1, 4, "1.21", null), span(3, 2, "1.21", null)),
            PlaytimeFilter.all(), PlaytimeBreakdown.VERSION, at(1, 3), at(2, 0));
        assertEquals(3_600_000L, clipped.totalMillis());
    }

    @Test
    void weekdayNamesComeFromTheSystemLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("Montag", PlaytimeWeekday.dayName(0));
            assertEquals("Mon", PlaytimeWeekday.shortDayName(0));
            assertEquals("Mit", PlaytimeWeekday.shortDayName(2));
            Locale.setDefault(Locale.JAPAN);
            assertEquals("月曜日", PlaytimeWeekday.dayName(0));
            assertEquals("月曜日", PlaytimeWeekday.shortDayName(0));
        } finally {
            Locale.setDefault(previous);
        }
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
