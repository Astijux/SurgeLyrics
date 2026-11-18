package dev.surgelyrics.core;

/** Warning intensities for low health and low armor. Pure maths; the adapter just draws the result. */
public final class Warn {
    private Warn() {}

    /** 0 when health is above the threshold; otherwise a pulsing 0..1 that gets stronger and faster as health drops. */
    public static float health(float hp, int thresholdHp, float timeSec) {
        if (thresholdHp <= 0 || hp > thresholdHp || hp <= 0) return 0f;
        float severity = LyricsView.clamp01(1f - hp / thresholdHp);
        float pulse = 0.5f + 0.5f * (float) Math.sin(timeSec * (3f + 5f * severity));
        return LyricsView.clamp01((0.35f + 0.65f * severity) * pulse);
    }

    /** 0 while the item is healthy; a 0..1 flash once the remaining fraction is at or under the threshold percent. */
    public static float armorFlash(float fraction, int thresholdPct, float timeSec) {
        if (fraction < 0f || fraction * 100f > thresholdPct + 0.01f) return 0f;      // tolerance: 0.15f * 100 is slightly over 15
        return 0.5f + 0.5f * (float) Math.sin(timeSec * 8f);
    }
}
