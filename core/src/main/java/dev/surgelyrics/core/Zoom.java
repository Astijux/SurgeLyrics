package dev.surgelyrics.core;

/** Hold-to-zoom field of view. */
public final class Zoom {
    private Zoom() {}

    /** @param amount 0..1 how far the zoom has eased in; @param factor 2..10 zoom at full amount */
    public static float fov(float baseFov, float amount, int factor) {
        float divisor = 1f + (Math.max(1, factor) - 1f) * LyricsView.clamp01(amount);
        return Math.max(5f, baseFov / divisor);
    }
}
