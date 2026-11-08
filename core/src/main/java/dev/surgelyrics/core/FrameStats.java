package dev.surgelyrics.core;

import java.util.Arrays;

/** Measures average FPS and the "1% low" (the stutter number) over one-second windows. */
public final class FrameStats {
    public record Snapshot(int avg, int low1) {}

    private long[] deltas = new long[1024];
    private int n;
    private long last, windowStart;

    /** Call once per rendered frame. @return a snapshot once per second, otherwise null. */
    public Snapshot frame(long nowNanos) {
        if (last != 0) {
            if (n == deltas.length) deltas = Arrays.copyOf(deltas, n * 2);
            deltas[n++] = nowNanos - last;
        }
        last = nowNanos;
        if (windowStart == 0) windowStart = nowNanos;
        if (nowNanos - windowStart < 1_000_000_000L || n == 0) return null;
        long[] sorted = Arrays.copyOf(deltas, n);
        Arrays.sort(sorted);
        int idx = Math.min(n - 1, (int) Math.ceil(n * 0.99) - 1);
        long worst = Math.max(1, sorted[idx]);
        double secs = (nowNanos - windowStart) / 1e9;
        Snapshot s = new Snapshot((int) Math.round(n / secs), (int) Math.round(1e9 / worst));
        n = 0;
        windowStart = nowNanos;
        return s;
    }
}
