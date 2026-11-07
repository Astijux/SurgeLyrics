package dev.surgelyrics.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;

/** One shared HTTP client and one small bounded worker pool for the whole mod. */
public final class Net {
    private Net() {}

    public static final String USER_AGENT = "SurgeLyrics/1.0 (Minecraft mod)";

    private static final ThreadFactory DAEMON = r -> {
        Thread t = new Thread(r, "surgelyrics-worker");
        t.setDaemon(true);
        return t;
    };
    public static final ExecutorService WORKERS = new ThreadPoolExecutor(
            0, 2, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>(32), DAEMON,
            new ThreadPoolExecutor.DiscardOldestPolicy());
    public static final ScheduledExecutorService SCHED =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "surgelyrics-timer"); t.setDaemon(true); return t; });

    public static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Abstraction so tests (and other platforms) can swap the transport. */
    public interface Fetcher {
        /** @return response body, or null on 404. @throws IOException on any other failure. */
        String get(String url) throws IOException, InterruptedException;
    }

    public static final Fetcher DEFAULT = url -> {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8)).header("User-Agent", USER_AGENT).GET().build();
        HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 404) return null;
        if (r.statusCode() / 100 != 2) throw new IOException("HTTP " + r.statusCode());
        return r.body();
    };
}
