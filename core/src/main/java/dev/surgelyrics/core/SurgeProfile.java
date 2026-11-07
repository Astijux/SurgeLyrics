package dev.surgelyrics.core;

/** Performance profiles. Distances are in blocks; 0 means "vanilla / no limit". */
public enum SurgeProfile {
    //            label        entity decor blockEnt particles nametag clouds weather minView
    VANILLA   ("Vanilla",          0,    0,     0,     0,        0, false, false,  0),
    BALANCED  ("Balanced",        64,   40,    48,   600,       48, false, false,  0),
    AGGRESSIVE("Aggressive",      40,   24,    32,   300,       24, true,  false,  0),
    POTATO    ("Potato",          24,   12,    20,   120,       12, true,  true,   0);

    public final String label;
    public final double entityBlocks, decorationBlocks, blockEntityBlocks, nametagBlocks;
    public final int particleBudget, minViewDistance;
    public final boolean hideClouds, hideWeather;

    SurgeProfile(String label, double e, double d, double b, int p, double n, boolean c, boolean w, int v) {
        this.label = label; entityBlocks = e; decorationBlocks = d; blockEntityBlocks = b;
        particleBudget = p; nametagBlocks = n; hideClouds = c; hideWeather = w; minViewDistance = v;
    }

    public SurgeProfile next() { return values()[(ordinal() + 1) % values().length]; }

    public static SurgeProfile byName(String n) {
        if (n == null) return null;
        for (SurgeProfile p : values()) if (p.name().equalsIgnoreCase(n.strip())) return p;
        return null;
    }
}
