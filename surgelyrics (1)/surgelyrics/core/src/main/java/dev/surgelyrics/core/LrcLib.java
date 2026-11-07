package dev.surgelyrics.core;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Fetches synced lyrics from LRCLIB with an LRU cache and in-flight de-duplication. */
public final class LrcLib {
    public static final List<Lrc.Line> NONE = List.of();

    private final Net.Fetcher http;
    private final String base;
    private final Map<String, List<Lrc.Line>> cache;
    private final Map<String, CompletableFuture<List<Lrc.Line>>> inflight = new ConcurrentHashMap<>();
    private final Map<String, Long> failedAt = new ConcurrentHashMap<>();
    private static final long RETRY_AFTER_NANOS = 30_000_000_000L;

    public LrcLib() { this(Net.DEFAULT, "https://lrclib.net/api"); }

    public LrcLib(Net.Fetcher http, String base) {
        this.http = http;
        this.base = base;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(64, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<String, List<Lrc.Line>> e) { return size() > 64; }
        });
    }

    /** Cached lines for a track, or null if not loaded yet. */
    public List<Lrc.Line> cached(Track t) { return cache.get(t.id()); }

    public CompletableFuture<List<Lrc.Line>> request(Track t) {
        List<Lrc.Line> hit = cache.get(t.id());
        if (hit != null) return CompletableFuture.completedFuture(hit);
        Long failed = failedAt.get(t.id());
        if (failed != null && System.nanoTime() - failed < RETRY_AFTER_NANOS)
            return CompletableFuture.completedFuture(NONE);       // recent network failure: wait before retrying
        return inflight.computeIfAbsent(t.id(), id -> {
            CompletableFuture<List<Lrc.Line>> f = CompletableFuture.supplyAsync(() -> fetchSafe(t), Net.WORKERS);
            f.whenComplete((lines, err) -> {
                if (lines != null) { cache.put(id, lines); failedAt.remove(id); }
                else failedAt.put(id, System.nanoTime());
                inflight.remove(id);
            });
            return f.thenApply(l -> l == null ? NONE : l);
        });
    }

    private List<Lrc.Line> fetchSafe(Track t) {
        try { return fetch(t); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); return null; }
        catch (Exception e) { return null; }   // null = failed (not cached, retried after a cooldown)
    }

    List<Lrc.Line> fetch(Track t) throws Exception {
        String q = "track_name=" + enc(t.title()) + "&artist_name=" + enc(t.artist());
        if (t.album() != null && !t.album().isBlank()) q += "&album_name=" + enc(t.album());
        if (t.durationMs() > 0) q += "&duration=" + Math.round(t.durationMs() / 1000.0);

        String body = http.get(base + "/get?" + q);
        if (body != null) {
            List<Lrc.Line> l = Lrc.parse(Json.str(Json.obj(Json.parse(body)).get("syncedLyrics"), ""));
            if (!l.isEmpty()) return l;
        }
        String sb = http.get(base + "/search?track_name=" + enc(t.title()) + "&artist_name=" + enc(t.artist()));
        if (sb == null) return NONE;
        Map<String, Object> best = null;
        double bestDiff = Double.MAX_VALUE;
        for (Object o : Json.arr(Json.parse(sb))) {
            Map<String, Object> m = Json.obj(o);
            if (Json.str(m.get("syncedLyrics"), "").isBlank()) continue;
            double diff = t.durationMs() > 0
                    ? Math.abs(Json.num(m.get("duration"), 0) * 1000 - t.durationMs()) : 0;
            if (diff < bestDiff) { bestDiff = diff; best = m; }
        }
        return best == null ? NONE : Lrc.parse(Json.str(best.get("syncedLyrics"), ""));
    }

    static String enc(String s) { return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8); }
}
