package dev.surgelyrics.core;

/** Spring motion for UI. Slightly under-damped on purpose, so things settle with a small liquid overshoot. */
public final class Motion {
    private Motion() {}

    public static final class Spring {
        public float pos, vel;
        private final float stiffness, damping;

        public Spring(float start, float stiffness, float damping) {
            this.pos = start; this.stiffness = stiffness; this.damping = damping;
        }

        /** Advance by dt seconds toward target. Safe for any dt (clamped and sub-stepped). */
        public float step(float dt, float target) {
            if (!(dt > 0)) return pos;
            dt = Math.min(dt, 0.05f);
            int n = Math.max(1, (int) Math.ceil(dt / 0.004f));
            float h = dt / n;
            for (int i = 0; i < n; i++) {
                vel += (stiffness * (target - pos) - damping * vel) * h;
                pos += vel * h;
            }
            if (Math.abs(target - pos) < 0.0005f && Math.abs(vel) < 0.0005f) { pos = target; vel = 0; }
            return pos;
        }
    }
}
