package me.wolfii.allthelogs.client.ui.widget.timeline;

import io.wispforest.owo.ui.core.OwoUIGraphics;
import me.wolfii.allthelogs.client.timeline.TimelineScale;
import me.wolfii.allthelogs.client.timeline.TimelineTicks;
import me.wolfii.allthelogs.client.ui.theme.Colors;
import me.wolfii.allthelogs.client.ui.widget.HoverChip;
import me.wolfii.allthelogs.data.query.MatchDay;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.DoubleFunction;

/**
 * Draws the scrubber on the right edge of the list: the track, its date ticks, the draggable thumb, and the
 * date label that follows the pointer while it is near the track.
 */
final class TimelineTrackPainter {
    static final int TRACK_WIDTH = 8;
    /** How far left of the track the pointer still counts as hovering it. */
    static final int HOVER_SLOP = 12;
    private static final int TRACK_INSET = 3;
    private static final int TICK_GAP_PX = 16;
    private static final int TICK_LABEL_OFFSET = 5;
    private static final float TICK_LABEL_SCALE = 0.75f;
    private static final int LABEL_PAD_X = 5;

    private TimelineTrackPainter() {
    }

    static int trackLeft(int x, int width) {
        return x + width - TRACK_INSET - TRACK_WIDTH;
    }

    /**
     * @param trackY      screen y where the scrubber starts. The tools button occupies the pixels above this
     * @param trackHeight height of the scrubber, shorter than the list when a button caps it
     * @param mouseX      pointer x in screen coordinates
     * @param mouseY      pointer y in screen coordinates
     * @param timeAt      maps a track-local y onto the timestamp it points at
     */
    static void draw(OwoUIGraphics graphics, ListView view, Track track, int trackY, int trackHeight,
                     int mouseX, int mouseY, DoubleFunction<LocalDateTime> timeAt) {
        int x = view.x();
        int trackX = trackLeft(x, view.width());
        graphics.fill(trackX, trackY, trackX + TRACK_WIDTH, trackY + trackHeight, Colors.TRACK);
        graphics.fill(trackX, trackY, trackX + 1, trackY + trackHeight, Colors.TRACK_BORDER);

        if (track.oldest() == null || track.newest() == null) return;

        drawDateTicks(graphics, track, trackX, trackY, trackHeight);
        if (track.thumbHeight() > 0) {
            graphics.fill(trackX + 1, track.thumbTop(), trackX + TRACK_WIDTH - 1,
                track.thumbTop() + track.thumbHeight(), Colors.THUMB);
        }

        boolean nearTrack = mouseX >= trackX - HOVER_SLOP && mouseX < x + view.width()
            && mouseY >= trackY && mouseY < trackY + trackHeight;
        if (!nearTrack && !track.dragging()) return;
        LocalDateTime hoverTime = timeAt.apply(mouseY - trackY);
        if (hoverTime == null) return;
        drawHoverLabel(graphics, view, trackX, trackY, trackHeight, mouseY,
            TimelineTicks.hoverLabel(hoverTime, track.days().size()));
    }

    private static void drawDateTicks(OwoUIGraphics graphics, Track track, int trackX, int trackY, int trackHeight) {
        List<MatchDay> days = track.days();
        for (TimelineTicks.DateTick tick
            : TimelineTicks.spacedTicks(track.oldest(), track.newest(), days, trackHeight, TICK_GAP_PX)) {
            double progress = TimelineScale.trackProgress(tick.at(), track.oldest(), track.newest(), days);
            int tickY = TimelineScale.yAtProgress(progress, trackY, trackHeight);
            graphics.fill(trackX + 2, tickY, trackX + TRACK_WIDTH - 2, tickY + 1, Colors.TICK_DOT);
            graphics.drawText(Component.literal(tick.label()), trackX - 3, tickY + TICK_LABEL_OFFSET,
                TICK_LABEL_SCALE, Colors.TICK_LABEL, OwoUIGraphics.TextAnchor.BOTTOM_RIGHT);
        }
    }

    private static void drawHoverLabel(OwoUIGraphics graphics, ListView view, int trackX, int trackY,
                                       int trackHeight, int mouseY, String label) {
        Font font = view.font();
        int labelWidth = font.width(label) + LABEL_PAD_X * 2;
        int labelHeight = Math.max(14, font.lineHeight + 6);
        int lowest = trackY + Math.max(0, trackHeight - labelHeight);
        int labelY = Math.clamp(mouseY - labelHeight / 2, trackY, lowest);
        int labelX = trackX - 6 - labelWidth;
        HoverChip.fill(graphics, labelX, labelY, labelWidth, labelHeight, Colors.HOVER_CHIP);
        int textY = labelY + Math.max(0, (labelHeight - font.lineHeight) / 2);
        graphics.drawText(Component.literal(label), labelX + LABEL_PAD_X, textY, 1.0f, Colors.TEXT);
    }

    /**
     * What the track shows this frame.
     *
     * @param oldest      earliest timestamp the track spans, or {@code null} when nothing is loaded
     * @param newest      latest timestamp the track spans, or {@code null} when nothing is loaded
     * @param days        occupied match days, which give the track its equal-share-per-day scale
     * @param thumbHeight height of the draggable thumb; {@code 0} hides it
     * @param thumbTop    screen y of the thumb top
     * @param dragging    whether the thumb is currently held, which keeps the hover label visible
     */
    record Track(LocalDateTime oldest, LocalDateTime newest, List<MatchDay> days,
                 int thumbHeight, int thumbTop, boolean dragging) {
    }
}
