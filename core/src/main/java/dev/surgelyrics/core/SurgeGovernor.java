package dev.surgelyrics.core;

/**
 * Pure decision logic for Surge. No Minecraft types, so every version adapter shares it and it is unit-tested.
 * Adaptive easing tightens every range when FPS (or the 1% low) is poor and relaxes it again when there's headroom.
 */
public final class SurgeGovernor {
    private SurgeProfile profile = SurgeProfile.BALANCED;
    private SurgeLimits limits = SurgeLimits.of(SurgeProfile.BALANCED, null);
    private boolean adaptive = true, lighten = true;
    private int targetFps = 90, low, high;
    private double scale = 1.0;                      // 0.5 .. 1.0 multiplier on every range

    // counters (render thread only) and the last full second, for the overlay
    private int cEnt, cBlk, cPart, cTag, cOcc;
    private int lEnt, lBlk, lPart, lTag, lOcc;

    public void configure(SurgeProfile p, SurgeLimits limits, boolean adaptive, int targetFps, boolean lightenUi) {
        this.profile = p; this.limits = limits; this.adaptive = adaptive; this.targetFps = targetFps; this.lighten = lightenUi;
        if (!adaptive || p == SurgeProfile.VANILLA) { scale = 1.0; low = high = 0; }
    }

    public SurgeProfile profile() { return profile; }
    public SurgeLimits limits() { return limits; }
    public boolean active() { return profile != SurgeProfile.VANILLA; }
    public double scale() { return scale; }

    /** Call once per second with the measured numbers. */
    public void onSecond(int avgFps, int low1Fps) {
        lEnt = cEnt; lBlk = cBlk; lPart = cPart; lTag = cTag; lOcc = cOcc;
        cEnt = cBlk = cPart = cTag = cOcc = 0;
        if (!adaptive || !active() || avgFps <= 0) return;
        boolean bad = avgFps < targetFps * 0.85 || (low1Fps > 0 && low1Fps < targetFps * 0.35);
        boolean good = avgFps > targetFps * 1.15 && low1Fps > targetFps * 0.6;
        if (bad) { low++; high = 0; } else if (good) { high++; low = 0; } else { low = high = 0; }
        if (low >= 2) { scale = Math.max(0.5, scale - 0.1); low = 0; }
        if (high >= 5) { scale = Math.min(1.0, scale + 0.1); high = 0; }
    }

    public void onSecond(int fps) { onSecond(fps, fps); }

    /** True if something at distance sqrt(distSq) should be skipped. */
    public boolean cull(double distSq, double rangeBlocks) {
        if (!active() || rangeBlocks <= 0) return false;
        double r = rangeBlocks * scale;
        return distSq > r * r;
    }

    public boolean cullMob(double d) { boolean c = cull(d, limits.mob()); if (c) cEnt++; return c; }
    public boolean cullItem(double d) { boolean c = cull(d, limits.item()); if (c) cEnt++; return c; }
    public boolean cullDecoration(double d) { boolean c = cull(d, limits.deco()); if (c) cEnt++; return c; }
    public boolean cullBlockEntity(double d) { boolean c = cull(d, limits.blockEntity()); if (c) cBlk++; return c; }
    public boolean cullNametag(double d) { boolean c = cull(d, limits.nametag()); if (c) cTag++; return c; }
    public boolean cullParticle(double d) { boolean c = cull(d, limits.particleDist()); if (c) cPart++; return c; }
    public void countParticleCapped() { cPart++; }
    public void countOccluded() { cOcc++; }

    public boolean hideClouds() { return active() && limits.clouds(); }
    public boolean hideWeather() { return active() && limits.weather(); }

    /** Particle cap per tick (0 = unlimited). */
    public int particleBudget() {
        if (!active() || limits.particleCap() <= 0) return 0;
        return Math.max(40, (int) (limits.particleCap() * scale));
    }

    /** True when the game is struggling and optional eye-candy (glow, 3D text layers) should be skipped. */
    public boolean lightenUi() { return lighten && active() && scale < 0.8; }

    public String statsLine() { return "culled/s  mobs " + lEnt + "  blocks " + lBlk + "  particles " + lPart + "  names " + lTag + "  behind walls " + lOcc; }
}
