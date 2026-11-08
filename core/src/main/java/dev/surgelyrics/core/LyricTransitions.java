package dev.surgelyrics.core;

/**
 * Motion for a lyric line coming in and the old one going out. Pure maths: given a style and progress 0..1 it
 * returns an offset (pixels), scale, opacity and, for the word style, how much of the line is revealed.
 */
public final class LyricTransitions {
    private LyricTransitions() {}

    public static final String[] STYLES = {"Fade", "Slide", "Zoom", "Bounce", "Words"};

    public record Pose(float dy, float scale, float alpha, float reveal) {}
    public static final Pose IDENTITY = new Pose(0f, 1f, 1f, 1f);

    public static Pose in(int style, float p) {
        p = LyricsView.clamp01(p);
        switch (style) {
            case 1: { float e = easeOutCubic(p); return new Pose((1f - e) * 12f, 1f, e, 1f); }
            case 2: return new Pose(0f, 0.78f + 0.22f * easeOutBack(p), LyricsView.clamp01(p * 2.2f), 1f);
            case 3: return new Pose(-16f * (1f - easeOutBounce(p)), 1f, LyricsView.clamp01(p * 3f), 1f);
            case 4: return new Pose((1f - easeOutCubic(p)) * 4f, 1f, 1f, easeOutCubic(p));
            default: return new Pose(0f, 1f, easeOutCubic(p), 1f);
        }
    }

    public static Pose out(int style, float p) {
        p = LyricsView.clamp01(p);
        switch (style) {
            case 1: { float e = p * p * p; return new Pose(-e * 12f, 1f, 1f - e, 1f); }
            case 2: return new Pose(0f, 1f + 0.16f * p, 1f - p, 1f);
            case 3: return new Pose(5f * p, 1f, 1f - LyricsView.clamp01(p * 2f), 1f);
            case 4: return new Pose(-3f * p, 1f, 1f - p, 1f);
            default: return new Pose(0f, 1f, 1f - p, 1f);
        }
    }

    public static float easeOutCubic(float t) { float u = 1f - LyricsView.clamp01(t); return 1f - u * u * u; }

    public static float easeOutBack(float t) {
        t = LyricsView.clamp01(t);
        float c1 = 1.70158f, c3 = c1 + 1f, u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    public static float easeOutBounce(float t) {
        t = LyricsView.clamp01(t);
        float n = 7.5625f, d = 2.75f;
        if (t < 1f / d) return n * t * t;
        if (t < 2f / d) { t -= 1.5f / d; return n * t * t + 0.75f; }
        if (t < 2.5f / d) { t -= 2.25f / d; return n * t * t + 0.9375f; }
        t -= 2.625f / d;
        return n * t * t + 0.984375f;
    }
}
