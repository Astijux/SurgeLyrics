package dev.surgelyrics.core;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Spotify "currently playing" via the PKCE login flow (no client secret needed). */
public final class Spotify {
    public static final String SCOPE = "user-read-currently-playing user-read-playback-state";

    private final Settings settings;
    private final TokenStore tokens;
    private final Path tokenFile;
    private volatile Track track;
    private volatile String status = "Not connected";
    private volatile long pausedUntilNanos;
    private volatile HttpServer server;
    private ScheduledFuture<?> poller;

    public Spotify(Settings settings, Path tokenFile) {
        this.settings = settings; this.tokenFile = tokenFile; this.tokens = TokenStore.load(tokenFile);
        if (tokens.loggedIn()) status = "Connected";
    }

    public Track track() { return track; }
    public String status() { return status; }
    public boolean connected() { return tokens.loggedIn(); }
    public String redirectUri() { return "http://127.0.0.1:" + settings.redirectPort + "/callback"; }

    public void logout() {
        stopServer(); tokens.clear(); tokens.save(tokenFile); track = null; status = "Not connected";
    }

    // ---------- pure helpers (unit tested) ----------
    public static String randomB64(int bytes) {
        byte[] b = new byte[bytes]; new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public static String challenge(String verifier) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    public static Map<String, String> parseQuery(String q) {
        Map<String, String> m = new HashMap<>();
        if (q == null || q.isEmpty()) return m;
        for (String kv : q.split("&")) {
            int i = kv.indexOf('=');
            String k = i < 0 ? kv : kv.substring(0, i), v = i < 0 ? "" : kv.substring(i + 1);
            m.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return m;
    }

    static String form(Map<String, String> m) {
        StringJoiner j = new StringJoiner("&");
        m.forEach((k, v) -> j.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v, StandardCharsets.UTF_8)));
        return j.toString();
    }

    // ---------- login ----------
    public void startLogin(Consumer<String> openBrowser) {
        String id = settings.spotifyClientId;
        if (id.isEmpty()) { status = "Paste your Client ID first"; return; }
        stopServer();
        String verifier = randomB64(64), state = randomB64(16), redirect = redirectUri();
        try {
            HttpServer s = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), settings.redirectPort), 0);
            s.createContext("/callback", ex -> {
                Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
                boolean ok = state.equals(q.get("state")) && q.get("code") != null;
                byte[] body = (ok
                        ? "<html><body style='font-family:sans-serif'><h2>Connected.</h2>You can close this tab and return to Minecraft.</body></html>"
                        : "<html><body style='font-family:sans-serif'><h2>Login failed.</h2>Return to Minecraft and try again.</body></html>")
                        .getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                ex.sendResponseHeaders(200, body.length);
                try (var os = ex.getResponseBody()) { os.write(body); }
                if (ok) Net.WORKERS.execute(() -> exchange(q.get("code"), verifier, redirect));
                else status = "Spotify login was cancelled or did not match";
                Net.SCHED.schedule(this::stopServer, 1, TimeUnit.SECONDS);
            });
            s.start();
            server = s;
        } catch (IOException e) {
            status = "Could not start login: port " + settings.redirectPort + " is busy (change it in settings)";
            return;
        }
        // auto-close the listener if the user never finishes
        Net.SCHED.schedule(this::stopServer, 5, TimeUnit.MINUTES);
        status = "Waiting for your browser...";
        openBrowser.accept("https://accounts.spotify.com/authorize?" + form(new LinkedHashMap<>(Map.of(
                "client_id", id, "response_type", "code", "redirect_uri", redirect,
                "scope", SCOPE, "code_challenge_method", "S256",
                "code_challenge", challenge(verifier), "state", state))));
    }

    private void stopServer() {
        HttpServer s = server; server = null;
        if (s != null) s.stop(0);
    }

    private void exchange(String code, String verifier, String redirect) {
        try {
            HttpResponse<String> r = post(new LinkedHashMap<>(Map.of(
                    "grant_type", "authorization_code", "code", code, "redirect_uri", redirect,
                    "client_id", settings.spotifyClientId, "code_verifier", verifier)));
            if (readTokens(r)) status = "Connected";
            else status = "Spotify refused the login (check the Redirect URI)";
        } catch (Exception e) { status = "Login failed: " + e.getClass().getSimpleName(); }
    }

    private HttpResponse<String> post(Map<String, String> params) throws IOException, InterruptedException {
        return Net.HTTP.send(HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                .timeout(Duration.ofSeconds(8)).header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", Net.USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(form(params))).build(), HttpResponse.BodyHandlers.ofString());
    }

    private boolean readTokens(HttpResponse<String> r) {
        if (r.statusCode() / 100 != 2) return false;
        Map<String, Object> m = Json.obj(Json.parse(r.body()));
        String a = Json.str(m.get("access_token"), "");
        if (a.isEmpty()) return false;
        tokens.access = a;
        String rt = Json.str(m.get("refresh_token"), "");
        if (!rt.isEmpty()) tokens.refresh = rt;
        tokens.expiresAtMs = System.currentTimeMillis() + (long) (Json.num(m.get("expires_in"), 3600) * 1000) - 30_000;
        tokens.save(tokenFile);
        return true;
    }

    private boolean ensureToken() {
        if (!tokens.loggedIn()) return false;
        if (!tokens.access.isEmpty() && System.currentTimeMillis() < tokens.expiresAtMs) return true;
        try {
            HttpResponse<String> r = post(new LinkedHashMap<>(Map.of("grant_type", "refresh_token",
                    "refresh_token", tokens.refresh, "client_id", settings.spotifyClientId)));
            if (readTokens(r)) return true;
            if (r.statusCode() == 400 || r.statusCode() == 401) {       // revoked: genuinely logged out
                tokens.clear(); tokens.save(tokenFile); status = "Spotify login expired - connect again";
            } else status = "Spotify unreachable (HTTP " + r.statusCode() + ")";
        } catch (Exception e) { status = "Spotify unreachable"; }
        return false;
    }

    // ---------- polling ----------
    public synchronized void start() {
        if (poller != null) return;
        poller = Net.SCHED.scheduleWithFixedDelay(() -> {
            try { poll(); } catch (Throwable t) { status = "Spotify error: " + t.getClass().getSimpleName(); }
        }, 1, 2, TimeUnit.SECONDS);
    }

    public synchronized void stop() { if (poller != null) { poller.cancel(false); poller = null; } }

    void poll() throws Exception {
        if (System.nanoTime() < pausedUntilNanos || !ensureToken()) return;
        HttpResponse<String> r = Net.HTTP.send(HttpRequest.newBuilder(
                        URI.create("https://api.spotify.com/v1/me/player/currently-playing"))
                .timeout(Duration.ofSeconds(6)).header("Authorization", "Bearer " + tokens.access)
                .header("User-Agent", Net.USER_AGENT).GET().build(), HttpResponse.BodyHandlers.ofString());
        int c = r.statusCode();
        if (c == 429) {
            long wait = r.headers().firstValueAsLong("Retry-After").orElse(5);
            pausedUntilNanos = System.nanoTime() + wait * 1_000_000_000L; status = "Rate limited, waiting"; return;
        }
        if (c == 401) { tokens.expiresAtMs = 0; return; }
        if (c == 204 || r.body() == null || r.body().isBlank()) { track = null; status = "Nothing playing"; return; }
        if (c / 100 != 2) { status = "Spotify error " + c; return; }
        Map<String, Object> m = Json.obj(Json.parse(r.body()));
        Map<String, Object> item = Json.obj(m.get("item"));
        String id = Json.str(item.get("id"), "");
        if (id.isEmpty() || !"track".equals(Json.str(item.get("type"), "track"))) { track = null; status = "Nothing playing"; return; }
        StringJoiner artists = new StringJoiner(", ");
        for (Object a : Json.arr(item.get("artists"))) artists.add(Json.str(Json.obj(a).get("name"), ""));
        track = Track.of(id, Json.str(item.get("name"), "Unknown"), artists.toString(),
                Json.str(Json.obj(item.get("album")).get("name"), ""),
                (long) Json.num(item.get("duration_ms"), 0), (long) Json.num(m.get("progress_ms"), 0),
                Json.bool(m.get("is_playing"), false));
        status = "Connected";
    }
}
