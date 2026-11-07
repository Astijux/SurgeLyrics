package dev.surgelyrics.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** All user settings. Loading never throws: bad or missing values fall back to defaults and are clamped. */
public final class Settings {
    // lyrics
    public boolean lyricsEnabled = true;
    public boolean showBar = true;
    public boolean glow = true;
    public boolean gradient = true;
    public int intensity = 1;          // 0 calm, 1 normal, 2 lively
    public int wordsPerLine = 4;       // 1..8
    public float staySeconds = 3.5f;   // 1..10
    public int sizePercent = 100;      // 50..200
    public int syncOffsetMs = 0;       // -3000..3000
    public int colour = 0xFF7DD3FC;
    public int colour2 = 0xFFC084FC;
    public int source = 0;             // 0 auto, 1 Windows media, 2 Spotify API
    public int redirectPort = 19542;
    public String spotifyClientId = "";
    // surge
    public String surgeProfile = "BALANCED";
    public boolean surgeAdaptive = true;
    public boolean surgeOverlay = false;
    public int targetFps = 90;         // 30..240

    public void clamp() {
        intensity = Math.max(0, Math.min(2, intensity));
        wordsPerLine = Math.max(1, Math.min(8, wordsPerLine));
        staySeconds = Math.max(1f, Math.min(10f, staySeconds));
        sizePercent = Math.max(50, Math.min(200, sizePercent));
        syncOffsetMs = Math.max(-3000, Math.min(3000, syncOffsetMs));
        targetFps = Math.max(30, Math.min(240, targetFps));
        source = Math.max(0, Math.min(2, source));
        redirectPort = (redirectPort < 1024 || redirectPort > 65535) ? 19542 : redirectPort;
        if (spotifyClientId == null) spotifyClientId = "";
        spotifyClientId = spotifyClientId.strip();
        if (SurgeProfile.byName(surgeProfile) == null) surgeProfile = "BALANCED";
    }

    public static Settings load(Path file) {
        Settings s = new Settings();
        try {
            if (Files.exists(file)) {
                Map<String, Object> m = Json.obj(Json.parse(Files.readString(file)));
                s.lyricsEnabled = Json.bool(m.get("lyricsEnabled"), s.lyricsEnabled);
                s.showBar = Json.bool(m.get("showBar"), s.showBar);
                s.glow = Json.bool(m.get("glow"), s.glow);
                s.gradient = Json.bool(m.get("gradient"), s.gradient);
                s.intensity = (int) Json.num(m.get("intensity"), s.intensity);
                s.wordsPerLine = (int) Json.num(m.get("wordsPerLine"), s.wordsPerLine);
                s.staySeconds = (float) Json.num(m.get("staySeconds"), s.staySeconds);
                s.sizePercent = (int) Json.num(m.get("sizePercent"), s.sizePercent);
                s.syncOffsetMs = (int) Json.num(m.get("syncOffsetMs"), s.syncOffsetMs);
                s.colour = (int) (long) Json.num(m.get("colour"), s.colour);
                s.colour2 = (int) (long) Json.num(m.get("colour2"), s.colour2);
                s.redirectPort = (int) Json.num(m.get("redirectPort"), s.redirectPort);
                s.source = (int) Json.num(m.get("source"), s.source);
                s.spotifyClientId = Json.str(m.get("spotifyClientId"), "");
                s.surgeProfile = Json.str(m.get("surgeProfile"), s.surgeProfile);
                s.surgeAdaptive = Json.bool(m.get("surgeAdaptive"), s.surgeAdaptive);
                s.surgeOverlay = Json.bool(m.get("surgeOverlay"), s.surgeOverlay);
                s.targetFps = (int) Json.num(m.get("targetFps"), s.targetFps);
            }
        } catch (Exception ignored) {
            // corrupt file: keep defaults; the next save() rewrites it cleanly
        }
        s.clamp();
        return s;
    }

    /** Atomic save: write a temp file then move it over the real one. */
    public void save(Path file) {
        clamp();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lyricsEnabled", lyricsEnabled); m.put("showBar", showBar); m.put("glow", glow);
        m.put("gradient", gradient); m.put("intensity", intensity); m.put("wordsPerLine", wordsPerLine);
        m.put("staySeconds", (double) staySeconds); m.put("sizePercent", sizePercent);
        m.put("syncOffsetMs", syncOffsetMs); m.put("colour", (long) colour & 0xFFFFFFFFL);
        m.put("colour2", (long) colour2 & 0xFFFFFFFFL); m.put("redirectPort", redirectPort); m.put("source", source);
        m.put("spotifyClientId", spotifyClientId); m.put("surgeProfile", surgeProfile);
        m.put("surgeAdaptive", surgeAdaptive); m.put("surgeOverlay", surgeOverlay);
        m.put("targetFps", targetFps);
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.writeString(tmp, Json.write(m));
            try { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException ignored) { }
    }
}
