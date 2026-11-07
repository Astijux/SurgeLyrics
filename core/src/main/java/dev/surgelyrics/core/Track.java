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

    public boolean sameSong(Track o) { return o != null && id.equals(o.id); }
}
