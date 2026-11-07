package dev.surgelyrics.core;

/**
 * Pure decision logic for Surge. No Minecraft types, so it is shared by every version adapter
 * and unit-tested.
 */
public final class SurgeGovernor {
    private SurgeProfile profile = SurgeProfile.BALANCED;
    private boolean adaptive = true;
    private int targetFps = 90;
    private int low, high;
    private double scale = 1.0;           // 0.5 .. 1.0 multiplier applied to every range

    public void configure(SurgeProfile p, boolean adaptive, int targetFps) {
        this.profile = p; this.adaptive = adaptive; this.targetFps = targetFps;
        if (!adaptive || p == SurgeProfile.VANILLA) { scale = 1.0; low = high = 0; }
    }

    public SurgeProfile profile() { return profile; }
    public boolean active() { return profile != SurgeProfile.VANILLA; }
    public double scale() { return scale; }

    /** Call once per second with the measured FPS. Eases ranges in when slow, back out when fast. */
    public void onSecond(int fps) {
        if (!adaptive || !active() || fps <= 0) return;
        if (fps < targetFps * 0.85) { low++; high = 0; }
        else if (fps > targetFps * 1.15) { high++; low = 0; }
        else { low = high = 0; }
        if (low >= 2) { scale = Math.max(0.5, scale - 0.1); low = 0; }
        if (high >= 5) { scale = Math.min(1.0, scale + 0.1); high = 0; }
    }

    /** True if something at distance sqrt(distSq) should be skipped. */
    public boolean cull(double distSq, double rangeBlocks) {
        if (!active() || rangeBlocks <= 0) return false;
        double r = rangeBlocks * scale;
        return distSq > r * r;
    }

    public boolean cullEntity(double distSq) { return cull(distSq, profile.entityBlocks); }
    public boolean cullBlockEntity(double distSq) { return cull(distSq, profile.blockEntityBlocks); }
    public boolean cullDecoration(double distSq) { return cull(distSq, profile.decorationBlocks); }
    public boolean cullNametag(double distSq) { return cull(distSq, profile.nametagBlocks); }
    public boolean hideClouds() { return active() && profile.hideClouds; }
    public boolean hideWeather() { return active() && profile.hideWeather; }

    /** Particle cap per tick (0 = unlimited). */
    public int particleBudget() {
        if (!active() || profile.particleBudget <= 0) return 0;
        return Math.max(40, (int) (profile.particleBudget * scale));
    }
}
