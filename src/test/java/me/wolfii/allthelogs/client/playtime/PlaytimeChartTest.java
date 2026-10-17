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
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals("2d 23h 59m", PlaytimeDuration.summary(3 * 86_400_000L - 60_000));
        assertEquals("3d", PlaytimeDuration.summary(3 * 86_400_000L));
        assertEquals("4d", PlaytimeDuration.summary(3 * 86_400_000L + 12 * 3_600_000L));
        assertEquals("200d", PlaytimeDuration.summary(199 * 86_400_000L + 13 * 3_600_000L));
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
            PlaytimeBreakdown.VERSION_SERVER, PlaytimeStyle.BARS, PlaytimeGrain.DAYS, null);

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
        List<PlaytimeHover.Line> lines = chart.hover(0, "Unknown", "Total", -1);
        assertEquals(List.of("Total", "", "1.21", "1.20.4"),
            lines.stream().skip(1).map(PlaytimeHover.Line::label).toList());
        assertEquals(List.of("", "4h", "", "3h", "1h"), lines.stream().map(PlaytimeHover.Line::value).toList());
        assertFalse(lines.get(1).bold());

        List<PlaytimeHover.Line> focused = chart.hover(0, "Unknown", "Total", 0);
        assertEquals(List.of("Total", "1.20.4", "", "1.21"),
            focused.stream().skip(1).map(PlaytimeHover.Line::label).toList());
        assertTrue(focused.get(2).bold());
    }

    @Test
    void playerGroupingsLabelAndStackVersionThenServerThenPlayer() {
        PlaytimeSeries alex = PlaytimeSeries.of(PlaytimeBreakdown.SERVER_PLAYER, "1.21", "hypixel.net", "Alex");
        PlaytimeSeries unknown = PlaytimeSeries.of(PlaytimeBreakdown.SERVER_PLAYER, "1.21", "hypixel.net", null);
        PlaytimeSeries other = PlaytimeSeries.of(PlaytimeBreakdown.SERVER_PLAYER, "1.21", "a.net", "Zed");
        assertEquals("hypixel.net - Alex", alex.label("Unknown"));
        assertEquals("hypixel.net - Unknown", unknown.label("Unknown"));
        assertEquals("1.21 - Alex",
            PlaytimeSeries.of(PlaytimeBreakdown.VERSION_PLAYER, "1.21", "hypixel.net", "Alex").label("Unknown"));
        assertEquals(List.of(other, alex, unknown),
            java.util.stream.Stream.of(unknown, alex, other).sorted(PlaytimeSeries.STACK_ORDER).toList());

        var current = me.wolfii.allthelogs.client.search.SearchFilter.defaults();
        var filter = PlaytimeMessageFilter.category(current, at(1, 0), at(2, 0), PlaytimeBreakdown.SERVER_PLAYER, alex);
        assertEquals(java.util.Set.of("hypixel.net"), filter.serversOrWorlds());
        assertNull(filter.versions());
    }

    @Test
    void serverFilterListsUnknownFirstThenNames() {
        List<PlaytimeSpan> spans = List.of(
            span(1, 1, "1.21", "zebra.net"),
            span(2, 1, "1.21", null),
            span(3, 1, "1.21", "alpha.net"),
            span(4, 1, "1.21", "  ")
        );
        assertEquals(List.of(PlaytimeFilter.UNKNOWN_SERVER, "alpha.net", "zebra.net"),
            PlaytimeChartModel.serversIn(spans));
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
    void axisUsesDaysOnlyFromThreeDays() {
        assertEquals("6h 36m", PlaytimeDuration.axis(6 * 3_600_000L + 36 * 60_000L));
        assertEquals("2d\n6h 5m", PlaytimeDuration.axis(2 * 86_400_000L + 6 * 3_600_000L + 5 * 60_000L));
        assertEquals("3d", PlaytimeDuration.axis(3 * 86_400_000L));
        assertEquals("3d", PlaytimeDuration.axis(3 * 86_400_000L + 3_600_000L));
        assertEquals("5d", PlaytimeDuration.axis(4 * 86_400_000L + 13 * 3_600_000L));
    }

    @Test
    void autoKeepsBarsNarrowForTheChartWidth() {
        LocalDateTime start = at(1, 0);
        assertEquals(PlaytimeGrain.MONTHS, PlaytimeGrain.resolve(PlaytimeGrain.AUTO, start, start.plusDays(7 * 365), 1000));
        assertEquals(PlaytimeGrain.WEEKS, PlaytimeGrain.resolve(PlaytimeGrain.AUTO, start, start.plusDays(600), 1000));
        assertEquals(PlaytimeGrain.DAYS, PlaytimeGrain.resolve(PlaytimeGrain.AUTO, start, start.plusDays(90), 1000));
        assertEquals(PlaytimeGrain.DAYS, PlaytimeGrain.resolve(PlaytimeGrain.AUTO, start, start.plusDays(20), 1000));
    }

    @Test
    void longHoverCardsSumTheSeriesThatDoNotFit() {
        List<PlaytimeSpan> spans = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            spans.add(new PlaytimeSpan(at(1, 0), at(1, 0).plusMinutes(index + 1), "1." + index, null));
        }
        PlaytimeChartModel.Result chart = PlaytimeChartModel.build(spans, PlaytimeFilter.all(),
            PlaytimeBreakdown.VERSION, PlaytimeStyle.BARS, PlaytimeGrain.DAYS, null);
        List<PlaytimeHover.Line> lines = chart.hover(0, "Unknown", "Total", -1);
        assertEquals(PlaytimeHover.MAX_LINES, lines.size());
        PlaytimeHover.Line more = lines.getLast();
        int shown = PlaytimeHover.MAX_LINES - 4;
        assertEquals(20 - shown, more.hidden());
        int hiddenMinutes = 0;
        for (int minutes = 1; minutes <= 20 - shown; minutes++) hiddenMinutes += minutes;
        assertEquals(PlaytimeDuration.format(hiddenMinutes * 60_000L), more.value());
    }

    @Test
    void changingTheFilterZoomsAllTheWayOut() {
        PlaytimeSession.clear();
        try {
            PlaytimeSession.window(new PlaytimeWindow(at(1, 0), at(2, 0)));
            PlaytimeSession.filter(PlaytimeFilter.all());
            assertEquals(new PlaytimeWindow(at(1, 0), at(2, 0)), PlaytimeSession.window());
            PlaytimeSession.filter(PlaytimeFilter.all().withVersions(Set.of()));
            assertNull(PlaytimeSession.window());
        } finally {
            PlaytimeSession.clear();
        }
    }

    @Test
    void theNarrowestViewShowsTwentyBarsOfTheChosenInterval() {
        assertEquals(20 * 86_400L, PlaytimeGrain.AUTO.minViewSeconds());
        assertEquals(20 * 7 * 86_400L, PlaytimeGrain.WEEKS.minViewSeconds());
        LocalDateTime start = at(1, 0);
        PlaytimeWindow domain = new PlaytimeWindow(start, start.plusYears(30));
        PlaytimeWindow deep = PlaytimeWindow.zoom(domain, domain, 0.5, 100, PlaytimeGrain.YEARS.minViewSeconds());
        assertTrue(java.time.Duration.between(deep.start(), deep.end()).toDays() >= 20 * 365);

        PlaytimeWindow month = new PlaytimeWindow(start.plusYears(10), start.plusYears(10).plusDays(30));
        PlaytimeWindow widened = PlaytimeWindow.widen(domain, month, PlaytimeGrain.YEARS.minViewSeconds());
        assertTrue(java.time.Duration.between(widened.start(), widened.end()).toDays() >= 20 * 365);
        assertTrue(!widened.start().isBefore(domain.start()) && !widened.end().isAfter(domain.end()));
    }

    @Test
    void zoomStopsAtTwentyDays() {
        PlaytimeWindow domain = new PlaytimeWindow(at(1, 0), at(1, 0).plusDays(100));
        PlaytimeWindow deep = PlaytimeWindow.zoom(domain, domain, 0.5, 40);
        assertEquals(20, java.time.Duration.between(deep.start(), deep.end()).toDays());
    }

    @Test
    void timedLayoutMovesBarsByLessThanABarAndClipsTheEdges() {
        long day = 86_400;
        long[] edges = {0, day, 2 * day, 3 * day};
        BarLayout start = BarLayout.timed(edges, day / 2, day / 2 + 2 * day, 0, 200);
        assertEquals(0, start.barLeft(0));
        assertEquals(50, start.barRight(0));
        assertEquals(150, start.barRight(1));
        assertEquals(200, start.barRight(2));
        BarLayout panned = BarLayout.timed(edges, day / 2 + day / 10, day / 2 + day / 10 + 2 * day, 0, 200);
        assertEquals(40, panned.barRight(0));
    }

    @Test
    void weekLabelsShowOnlyTheFirstDay() {
        LocalDateTime monday = LocalDateTime.of(2024, 1, 1, 0, 0);
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.UK);
            assertEquals("1 Jan 2024", PlaytimeChartModel.axisLabel(monday, PlaytimeGrain.WEEKS));
            assertEquals("1 Jan 2024", PlaytimeChartModel.axisLabel(monday, PlaytimeGrain.DAYS));
            assertEquals("Jan 2024", PlaytimeChartModel.axisLabel(monday, PlaytimeGrain.MONTHS));
            assertEquals("2024", PlaytimeChartModel.axisLabel(monday, PlaytimeGrain.YEARS));
            assertEquals("1 – 7 Jan 2024", PlaytimeChartModel.spanLabel(monday, monday.plusWeeks(1), PlaytimeGrain.WEEKS));
            LocalDateTime late = LocalDateTime.of(2024, 1, 29, 0, 0);
            assertEquals("29 Jan – 4 Feb 2024", PlaytimeChartModel.spanLabel(late, late.plusWeeks(1), PlaytimeGrain.WEEKS));
            LocalDateTime turn = LocalDateTime.of(2024, 12, 30, 0, 0);
            assertEquals("30 Dec 2024 – 5 Jan 2025", PlaytimeChartModel.spanLabel(turn, turn.plusWeeks(1), PlaytimeGrain.WEEKS));
            assertEquals("Mon 1 Jan 2024", PlaytimeChartModel.spanLabel(monday, monday.plusDays(1), PlaytimeGrain.DAYS));
            assertEquals("Jan 2024", PlaytimeChartModel.spanLabel(monday, monday.plusMonths(1), PlaytimeGrain.MONTHS));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void emptyDaysAndWeeksStillGetABar() {
        List<PlaytimeSpan> spans = List.of(span(1, 1, "1.21", null), span(29, 1, "1.21", null));
        assertEquals(29, PlaytimeChartModel.build(spans, PlaytimeFilter.all(), PlaytimeBreakdown.VERSION,
            PlaytimeStyle.BARS, PlaytimeGrain.DAYS, null).columns().size());
        assertEquals(5, PlaytimeChartModel.build(spans, PlaytimeFilter.all(), PlaytimeBreakdown.VERSION,
            PlaytimeStyle.BARS, PlaytimeGrain.WEEKS, null).columns().size());
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
        assertEquals(3_600_000L, days.heatMillis(DayOfWeek.SUNDAY.getValue() - 1, 23, -1));
        assertEquals(3_600_000L, days.heatMillis(DayOfWeek.MONDAY.getValue() - 1, 0, -1));
        assertEquals(2 * 3_600_000L, days.heatMillis(PlaytimeWeekday.ALL_DAYS, 0, -1));

        PlaytimeWeekday.Result fine = PlaytimeWeekday.build(List.of(overnight), PlaytimeFilter.all(),
            PlaytimeBreakdown.SERVER, null, null, 15);
        assertEquals(96, fine.slots());
        assertEquals(15 * 60_000L, fine.heatMillis(DayOfWeek.SUNDAY.getValue() - 1, 95, -1));
        assertEquals(0L, fine.heatMillis(DayOfWeek.SUNDAY.getValue() - 1, 91, -1));
        assertEquals("23:45 - 24:00", fine.slotLabel(95));
        assertEquals(2 * 3_600_000L, fine.totalMillis());

        List<PlaytimeHover.Line> single = days.dayHover(DayOfWeek.TUESDAY.getValue() - 1, "Total", "Unknown", -1);
        assertEquals(2, single.size());
        assertEquals("b", single.get(1).label());
        assertEquals("3h", single.get(1).value());
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
        assertNull(period.versions());
        assertNull(period.serversOrWorlds());

        PlaytimeSeries both = PlaytimeSeries.of(PlaytimeBreakdown.VERSION_SERVER, "1.21", "hypixel.net");
        var category = PlaytimeMessageFilter.category(current, at(1, 0), at(2, 0), PlaytimeBreakdown.VERSION_SERVER, both);
        assertEquals(java.util.Set.of("1.21"), category.versions());
        assertEquals(java.util.Set.of("hypixel.net"), category.serversOrWorlds());
    }
}
