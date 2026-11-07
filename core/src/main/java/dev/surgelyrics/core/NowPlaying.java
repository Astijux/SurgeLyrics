package dev.surgelyrics.core;

/** Chooses between Windows media and the Spotify API according to the Source setting. */
public final class NowPlaying {
    private final Settings settings;
    private final Spotify spotify;
    private final WindowsMedia windows;
    private volatile String lastSource = "";

    public NowPlaying(Settings settings, Spotify spotify, WindowsMedia windows) {
        this.settings = settings; this.spotify = spotify; this.windows = windows;
    }

    /** Start or stop the Windows reader to match the current Source setting. Call after changing it. */
    public void sync() {
        if (WindowsMedia.supported() && settings.source != 2) windows.start(); else windows.stop();
    }

    public Track track() {
        int src = settings.source;
        Track w = src == 2 ? null : windows.track();
        Track s = src == 1 ? null : spotify.track();
        Track pick;
        if (w != null && s != null) pick = (w.playing() || !s.playing()) ? w : s;   // prefer whichever is actually playing
        else pick = w != null ? w : s;
        lastSource = pick == null ? "" : (pick == w ? "Windows" : "Spotify");
        return pick;
    }

    public String source() { return lastSource; }

    public String describe() {
        Track t = track();
        if (t == null) return "Nothing playing";
        return t.title() + (t.artist().isEmpty() ? "" : " - " + t.artist()) + "  (" + lastSource + ")";
    }

    public String windowsStatus() { return WindowsMedia.supported() ? windows.status() : "Only available on Windows"; }
}
