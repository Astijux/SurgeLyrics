package dev.surgelyrics.core;

/** The numbers Surge actually enforces: either a preset's, or the user's custom sliders. */
public record SurgeLimits(double mob, double item, double deco, double blockEntity, double nametag,
                          double particleDist, int particleCap, boolean clouds, boolean weather) {

    public static SurgeLimits of(SurgeProfile p, Settings s) {
        if (p == SurgeProfile.CUSTOM && s != null)
            return new SurgeLimits(s.cMob, s.cItem, s.cDeco, s.cBlockEntity, s.cNametag, s.cParticleDist,
                    s.cParticleCap, s.cClouds, s.cWeather);
        return new SurgeLimits(p.mob, p.item, p.deco, p.blockEntity, p.nametag, p.particleDist, p.particleCap,
                p.clouds, p.weather);
    }

    /** Copy a preset into the custom sliders, so "Custom" can start from any profile. */
    public static void copyToCustom(SurgeProfile p, Settings s) {
        SurgeLimits l = of(p == SurgeProfile.CUSTOM ? SurgeProfile.BALANCED : p, null);
        s.cMob = (int) l.mob; s.cItem = (int) l.item; s.cDeco = (int) l.deco; s.cBlockEntity = (int) l.blockEntity;
        s.cNametag = (int) l.nametag; s.cParticleDist = (int) l.particleDist; s.cParticleCap = l.particleCap;
        s.cClouds = l.clouds; s.cWeather = l.weather;
    }
}
