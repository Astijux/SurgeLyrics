package dev.surgelyrics.core;

/** Small deterministic effects for the lyrics: twinkling glints and a per-letter wave. Pure maths. */
public final class Flair {
    private Flair() {}

    /** Stable pseudo-random number in [0, 1) from an integer seed. */
    public static float hash01(int n) {
        n ^= n >>> 16; n *= 0x7feb352d;
        n ^= n >>> 15; n *= 0x846ca68b;
        n ^= n >>> 16;
        return (n & 0xFFFFFF) / (float) 0x1000000;
    }

    /**
     * Glint i at time t (seconds): {x in -0.5..0.5, y in -0.6..0.6 (fractions of the text box), brightness 0..1}.
     * Each glint lives about two seconds, fades in and out, then reappears somewhere else.
     */
    public static float[] glint(int i, float t) {
        float life = 1.4f + hash01(i * 7 + 3) * 1.2f;
        float tt = t / life + hash01(i * 13 + 5);
        int cycle = (int) Math.floor(tt);
        float ph = tt - cycle;
        float x = hash01(i * 31 + cycle * 17) - 0.5f;
        float y = (hash01(i * 57 + cycle * 29) - 0.5f) * 1.2f;
        float a = (float) Math.sin(Math.PI * ph);
        return new float[]{x, y, a * a};
    }

    /** Vertical offset in pixels for letter i of a travelling wave. */
    public static float wave(int i, float t, float amp) {
        return amp * (float) Math.sin(t * 4.2f - i * 0.5f);
    }
}
