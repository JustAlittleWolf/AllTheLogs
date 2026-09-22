package me.wolfii.allthelogs.client.ui.widget;

import me.wolfii.allthelogs.client.timeline.ScrubJump;
import me.wolfii.allthelogs.client.timeline.ScrubberGeometry;

import java.util.Objects;

/**
 * State of a drag on the timeline scrubber: where the thumb is being held, and how often the store may be
 * queried to preview where it would land.
 * <p>
 * The thumb height is captured when the drag starts so that loading a different page mid-drag cannot resize
 * the thumb under the pointer. Two positions are tracked because a track that is too short for a draggable
 * thumb follows the pointer directly instead: {@link #thumbTopOffset} is set while a real thumb is dragged and
 * {@link #pointerOffset} while the pointer is followed. Exactly one of them is live at a time.
 */
final class ScrubDrag {
    static final int MIN_THUMB_HEIGHT = 16;
    private static final int PREVIEW_THROTTLE_MS = 100;

    private boolean dragging;
    private double thumbTopOffset = Double.NaN;
    private double pointerOffset = Double.NaN;
    private double grabOffset;
    private int capturedThumbHeight;
    private long lastPreviewAtMs;
    private boolean previewInFlight;
    private boolean previewShown;
    private boolean finishAfterPreview;
    private int previewEpoch;
    private ScrubJump lastSentJump;

    /**
     * Preview jumps while the thumb is held: at most one in-flight store query, at least
     * {@code throttleMs} between requests. A later position still fires after the wait so a
     * parked thumb can catch up.
     */
    static boolean shouldSendPreviewQuery(boolean inFlight, long nowMs, long lastQueryMs,
                                          int throttleMs, ScrubJump requested,
                                          ScrubJump lastSent) {
        if (inFlight || nowMs - lastQueryMs < throttleMs) return false;
        return !sameTarget(requested, lastSent);
    }

    /**
     * Releasing the thumb must not fetch or reposition when the page for that spot is already on screen or
     * still on its way. A different timestamp still commits, so a quick drag that never previewed still lands.
     */
    static boolean keepsHeldPage(boolean sameTarget, boolean previewInFlight, boolean previewShown) {
        return sameTarget && (previewInFlight || previewShown);
    }

    /**
     * Whether both jumps ask the store for the same page. Progress only positions that page in the viewport,
     * so a parked thumb whose pixel jitters without changing the timestamp or match rank must not count as a
     * new query — that used to cancel the in-flight preview before it ever reached the list.
     */
    static boolean sameTarget(ScrubJump left, ScrubJump right) {
        if (left == right) return true;
        if (left == null || right == null) return false;
        return left.skip() == right.skip() && Objects.equals(left.time(), right.time());
    }

    boolean dragging() {
        return dragging;
    }

    int capturedThumbHeight() {
        return capturedThumbHeight;
    }

    /**
     * Starts a drag, capturing the thumb height for its duration. A track with no thumb captures {@code 0}
     * and the pointer is followed directly.
     */
    void begin(int liveThumbHeight) {
        dragging = true;
        capturedThumbHeight = liveThumbHeight <= 0 ? 0 : Math.max(MIN_THUMB_HEIGHT, liveThumbHeight);
        grabOffset = 0;
        lastSentJump = null;
        lastPreviewAtMs = 0;
        previewInFlight = false;
        previewShown = false;
        finishAfterPreview = false;
        previewEpoch++;
    }

    /**
     * Identifies the drag that owns the current preview slot. Results from an older drag must not clear it.
     */
    int epoch() {
        return previewEpoch;
    }

    boolean previewInFlight() {
        return previewInFlight;
    }

    boolean previewShown() {
        return previewShown;
    }

    ScrubJump lastSentJump() {
        return lastSentJump;
    }

    /**
     * The rows for the current preview are on screen. A newer claim clears this so a late result for the
     * previous timestamp cannot be treated as the page the thumb is now holding.
     */
    void markPreviewShown() {
        previewShown = true;
    }

    /**
     * The button went up while this preview was still in flight. {@link #previewFinished(int)} then reports
     * that the thumb should go back to following the viewport, without a second query.
     */
    void finishAfterPreview() {
        finishAfterPreview = true;
    }

    /**
     * A later slice of a preview that is already visible should keep those rows still once the button is up.
     * While the thumb is held, the new slice is positioned under it instead.
     */
    boolean anchorsVisibleRows() {
        return previewShown && !dragging;
    }

    /**
     * Records where inside the thumb the pointer grabbed it, so the thumb does not jump on the first move.
     *
     * @param thumbTopLocal current thumb top, relative to the top of the track
     */
    void grab(double localY, int thumbTopLocal, int trackHeight) {
        grabOffset = ScrubberGeometry.thumbGrabOffset(localY, thumbTopLocal, capturedThumbHeight, trackHeight);
    }

    void endDrag() {
        dragging = false;
    }

    /**
     * Forgets the drag position so the thumb goes back to following the viewport. Ignored mid-drag.
     */
    void finish() {
        if (dragging) return;
        thumbTopOffset = Double.NaN;
        pointerOffset = Double.NaN;
        grabOffset = 0;
        capturedThumbHeight = 0;
        previewShown = false;
        finishAfterPreview = false;
    }

    /**
     * Moves the drag to {@code localY} and returns the 0–1 track progress it now points at.
     */
    double moveTo(double localY, int thumbHeight, int trackHeight) {
        if (thumbHeight <= 0 || thumbHeight >= trackHeight) {
            pointerOffset = Math.clamp(localY, 0, Math.max(0, trackHeight - 1));
            thumbTopOffset = Double.NaN;
            return pointerOffset / Math.max(1, trackHeight - 1);
        }
        thumbTopOffset = Math.clamp(localY - grabOffset, 0, trackHeight - thumbHeight);
        pointerOffset = Double.NaN;
        return ScrubberGeometry.progressFromThumb((int) Math.round(thumbTopOffset), trackHeight, thumbHeight);
    }

    /**
     * Track progress the drag is parked at, or {@link Double#NaN} when it holds no position.
     */
    double parkedProgress(int trackHeight) {
        if (!Double.isNaN(thumbTopOffset) && capturedThumbHeight > 0 && capturedThumbHeight < trackHeight) {
            return ScrubberGeometry.progressFromThumb(
                (int) Math.round(thumbTopOffset), trackHeight, capturedThumbHeight);
        }
        if (!Double.isNaN(pointerOffset)) {
            return pointerOffset / Math.max(1, trackHeight - 1);
        }
        return Double.NaN;
    }

    boolean holdsPosition() {
        return !Double.isNaN(thumbTopOffset) || !Double.isNaN(pointerOffset);
    }

    /**
     * Thumb top for the held position, relative to the top of the track. Only valid when
     * {@link #holdsPosition()}.
     */
    int heldThumbTopOffset(int trackHeight, int thumbHeight) {
        if (!Double.isNaN(thumbTopOffset)) {
            return (int) Math.round(Math.clamp(thumbTopOffset, 0, Math.max(0, trackHeight - thumbHeight)));
        }
        int centre = (int) Math.round(Math.clamp(pointerOffset, 0, Math.max(0, trackHeight - 1)));
        return Math.clamp(centre - Math.max(1, thumbHeight) / 2, 0, Math.max(0, trackHeight - thumbHeight));
    }

    /**
     * Frees the preview slot. Returns whether the button already went up and the thumb should now follow
     * the viewport. A result from an earlier drag ({@code epoch} no longer current) does neither.
     */
    boolean previewFinished(int epoch) {
        if (epoch != previewEpoch) return false;
        previewInFlight = false;
        boolean finish = finishAfterPreview;
        finishAfterPreview = false;
        return finish;
    }

    /**
     * Whether a preview query for {@code jump} should be sent now. Records it as in flight when it should.
     */
    boolean claimPreview(ScrubJump jump) {
        long now = System.currentTimeMillis();
        if (!shouldSendPreviewQuery(previewInFlight, now, lastPreviewAtMs, PREVIEW_THROTTLE_MS, jump, lastSentJump)) {
            return false;
        }
        lastPreviewAtMs = now;
        lastSentJump = jump;
        previewInFlight = true;
        previewShown = false;
        return true;
    }

    void markCommitted(ScrubJump jump) {
        lastSentJump = jump;
    }
}
