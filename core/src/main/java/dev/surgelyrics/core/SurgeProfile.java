package dev.surgelyrics.core;

/** Performance presets. Distances are in blocks; 0 means vanilla / no limit. CUSTOM reads the user's own sliders. */
public enum SurgeProfile {
    //              label         mob  item deco blockEnt nametag partDist partCap clouds weather
    VANILLA   ("Vanilla",          0,    0,   0,    0,      0,      0,      0,  false, false),
    BALANCED  ("Balanced",        64,   40,  40,   48,     48,     48,    600,  false, false),
    AGGRESSIVE("Aggressive",      40,   24,  24,   32,     24,     32,    300,  true,  false),
    POTATO    ("Potato",          24,   12,  12,   20,     12,     20,    120,  true,  true),
    EXTREME   ("Extreme",         16,    8,   8,   12,      8,     12,     60,  true,  true),
    PVP       ("PvP",             96,   24,  24,   24,     64,     24,    200,  true,  true),
    CUSTOM    ("Custom",          64,   40,  40,   48,     48,     48,    600,  false, false);

    public final String label;
    public final double mob, item, deco, blockEntity, nametag, particleDist;
    public final int particleCap;
    public final boolean clouds, weather;

    SurgeProfile(String label, double mob, double item, double deco, double blockEntity, double nametag,
                 double particleDist, int particleCap, boolean clouds, boolean weather) {
        this.label = label; this.mob = mob; this.item = item; this.deco = deco; this.blockEntity = blockEntity;
        this.nametag = nametag; this.particleDist = particleDist; this.particleCap = particleCap;
        this.clouds = clouds; this.weather = weather;
    }

    public SurgeProfile next() { return values()[(ordinal() + 1) % values().length]; }

    public static SurgeProfile byName(String n) {
        if (n == null) return null;
        for (SurgeProfile p : values()) if (p.name().equalsIgnoreCase(n.strip())) return p;
        return null;
    }
}
