package dev.surgelyrics.core;

/** Turns a raw output-level reading (0..1) into a smooth level and a beat pulse for the HUD to react to. */
public final class AudioReact {
    private float level, avg, beat, cooldown;

    /** @param raw peak level 0..1 @param dt seconds since the last call */
    public void update(float raw, float dt) {
        if (!(dt > 0)) return;
        dt = Math.min(dt, 0.25f);
        raw = Float.isNaN(raw) ? 0f : LyricsView.clamp01(raw);
        float up = 1f - (float) Math.exp(-dt * 30f), down = 1f - (float) Math.exp(-dt * 6f);
        level += (raw - level) * (raw > level ? up : down);
        avg += (raw - avg) * (1f - (float) Math.exp(-dt * 1.5f));
        cooldown -= dt;
        if (cooldown <= 0 && raw > 0.08f && raw > avg * 1.35f + 0.03f) { beat = 1f; cooldown = 0.18f; }
        beat = Math.max(0f, beat - dt * 4.5f);
    }

    public float level() { return level; }
    public float beat() { return beat; }
}
