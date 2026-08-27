package me.wolfii.allthelogs.client.ui.widget;

import me.wolfii.allthelogs.client.list.MessageListLayout;

import java.util.function.BooleanSupplier;

/**
 * Middle-click auto-scroll. The list keeps scrolling while the pointer stays away from where the button went
 * down, at a speed proportional to that distance.
 * <p>
 * Two gestures end it. A quick click starts auto-scroll and leaves it running until the next click; holding
 * the button for {@link #HOLD_MS} instead makes releasing it stop the scroll, which is what browsers do.
 */
final class AutoScroller {
    private static final int DEADZONE_PX = 8;
    private static final int HOLD_MS = 250;
    /** Pointer distance, in pixels past the deadzone, that scrolls one row per frame. */
    private static final double PIXELS_PER_ROW = 28.0;
    private static final double ROWS_PER_FRAME = 8;

    private boolean active;
    private boolean buttonDown;
    private boolean holdLatched;
    private long buttonDownAtMs;
    private double originY;

    /**
     * Once the middle button has been held for {@link #HOLD_MS}, releasing it should stop auto-scroll.
     */
    static boolean latchMiddleHold(boolean alreadyLatched, boolean buttonDown, long heldMs) {
        return alreadyLatched || (buttonDown && heldMs >= HOLD_MS);
    }

    boolean active() {
        return active;
    }

    void start(double localY) {
        active = true;
        buttonDown = true;
        holdLatched = false;
        buttonDownAtMs = System.currentTimeMillis();
        originY = localY;
    }

    void stop() {
        active = false;
        buttonDown = false;
        holdLatched = false;
    }

    /**
     * Scroll offset to add this frame, or {@code 0} while idle or inside the deadzone. Also ends the gesture
     * when a held middle button has been released.
     *
     * @param localY        pointer y relative to the top of the list
     * @param delta         frame time from the UI, in ticks
     * @param middlePressed whether the middle mouse button is still down
     */
    double scrollDelta(double localY, float delta, BooleanSupplier middlePressed) {
        if (!active) return 0;
        if (buttonDown) {
            boolean pressed = middlePressed.getAsBoolean();
            holdLatched = latchMiddleHold(holdLatched, pressed, System.currentTimeMillis() - buttonDownAtMs);
            if (!pressed) {
                buttonDown = false;
                if (holdLatched) {
                    stop();
                    return 0;
                }
            }
        }
        double offset = localY - originY;
        if (Math.abs(offset) <= DEADZONE_PX) return 0;
        double pastDeadzone = offset - Math.copySign(DEADZONE_PX, offset);
        return pastDeadzone / PIXELS_PER_ROW * MessageListLayout.ROW_HEIGHT * Math.max(0.05, delta) * ROWS_PER_FRAME;
    }
}
