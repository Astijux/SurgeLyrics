package dev.surgelyrics.mc1_21_11;

import dev.surgelyrics.core.*;
import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;

/** Single place that owns the shared objects, so mixins and screens never construct their own. */
public final class Runtime {
    private Runtime() {}

    public static final Path SETTINGS_FILE = FabricLoader.getInstance().getConfigDir().resolve("surgelyrics.json");
    public static final Path TOKEN_FILE = FabricLoader.getInstance().getConfigDir().resolve("surgelyrics-auth.json");

    public static final Settings SETTINGS = Settings.load(SETTINGS_FILE);
    public static final SurgeGovernor SURGE = new SurgeGovernor();
    public static final FrameStats FRAMES = new FrameStats();
    public static final CombatState COMBAT = new CombatState();
    public static final long LAUNCH_MS = System.currentTimeMillis();
    public static final LrcLib LYRICS = new LrcLib();
    public static final Spotify SPOTIFY = new Spotify(SETTINGS, TOKEN_FILE);
    public static final WindowsMedia WINDOWS = new WindowsMedia();
    public static final NowPlaying NOW = new NowPlaying(SETTINGS, SPOTIFY, WINDOWS);

    /** Safe to call any time: pushes current settings into the governor. */
    public static void applySurge() {
        SurgeProfile p = SurgeProfile.byName(SETTINGS.surgeProfile);
        if (p == null) p = SurgeProfile.BALANCED;
        SURGE.configure(p, SurgeLimits.of(p, SETTINGS), SETTINGS.surgeAdaptive, SETTINGS.targetFps, SETTINGS.lightenLyrics);
    }

    public static void save() { SETTINGS.save(SETTINGS_FILE); }
}
