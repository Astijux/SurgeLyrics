package dev.surgelyrics.core;

/** Immutable now-playing snapshot with a locally interpolated clock. */
public record Track(String id, String title, String artist, String album,
                    long durationMs, long baseMs, long baseNanos, boolean playing) {

    public static Track of(String id, String title, String artist, String album,
                           long durationMs, long positionMs, boolean playing) {
        return new Track(id, title, artist, album, durationMs, positionMs, System.nanoTime(), playing);
    }

    public long positionMs() { return positionMs(System.nanoTime()); }

    public long positionMs(long nowNanos) {
        long p = baseMs;
        if (playing) p += Math.max(0, (nowNanos - baseNanos) / 1_000_000L);
        if (durationMs > 0) p = Math.min(p, durationMs);
        return Math.max(0, p);
    }

    /** Keep the existing snapshot when the new one agrees with it, so the clock doesn't jitter every poll. */
    public static Track merge(Track prev, Track next) {
        if (prev == null || next == null) return next;
        if (prev.sameSong(next) && prev.playing() == next.playing()
                && Math.abs(prev.positionMs() - next.positionMs()) < 400) return prev;
        return next;
    }

    public boolean sameSong(Track o) { return o != null && id.equals(o.id); }
}
