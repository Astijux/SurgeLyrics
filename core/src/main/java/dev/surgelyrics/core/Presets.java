package dev.surgelyrics.core;

import java.util.ArrayList;
import java.util.List;

/**
 * One-click packs. Each pack only sets presentation and HUD options, never your Spotify/Surge accounts or tokens.
 * Two kinds: "looks" (font, colours, effects, window theme) and "PvP loadouts" (which info widgets are on, plus lean visuals).
 * Indices 0..4 are the original five and never move, so older code and notes keep working.
 */
public final class Presets {
    private Presets() {}

    public static final String[] NAMES = {
            "Clean", "Neon", "Cinema", "Retro", "PvP",
            "Sunset", "Ocean", "Midnight", "Sakura", "Gold", "Hacker", "Vapor", "Minimal",
            "PvP Lite", "PvP Crystal", "PvP Sword", "PvP Archer", "PvP Full"};

    public static final String[] DESC = {
            "Calm white lyrics, no effects.",
            "Cyan and magenta glow with sparkles.",
            "Big gold serif, one full line at a time.",
            "Green arcade look with a 3D extrude.",
            "Small quiet lyrics and the core PvP info on.",
            "Warm orange to pink, soft reflection.",
            "Cool blues on a glass backdrop.",
            "Italic serif on deep indigo glass.",
            "Soft pink script with sparkles.",
            "Gold serif with a light 3D edge.",
            "Green terminal text, typed out.",
            "Pastel neon outline with reflection.",
            "Small, quiet, no bar. Stays out of the way.",
            "Just target, potion timers and CPS.",
            "Totems, crystals and pearls with a no-totem alert.",
            "Target, health, keys, reach, combo and armor.",
            "Crosshair target, arrows, speed and hold-to-zoom.",
            "Every PvP widget and alert switched on."};

    /** True for the PvP loadout packs (shown on their own page). */
    public static boolean isPvp(int i) { return i == 4 || i >= 13; }

    /** Indices of the look packs, in display order. */
    public static int[] looks() { return pick(false); }

    /** Indices of the PvP loadout packs, in display order. */
    public static int[] pvps() { return pick(true); }

    private static int[] pick(boolean pvp) {
        List<Integer> l = new ArrayList<>();
        for (int i = 0; i < NAMES.length; i++) if (isPvp(i) == pvp) l.add(i);
        int[] r = new int[l.size()];
        for (int i = 0; i < r.length; i++) r[i] = l.get(i);
        return r;
    }

    private static int[][] swatches;

    /** {primary, secondary} colours of a pack, for the little dots on its button. */
    public static synchronized int[] swatch(int i) {
        if (swatches == null) {
            swatches = new int[NAMES.length][];
            for (int k = 0; k < NAMES.length; k++) {
                Settings t = new Settings();
                apply(t, k);
                swatches[k] = new int[]{t.colour, t.colour2};
            }
        }
        return swatches[Math.max(0, Math.min(NAMES.length - 1, i))];
    }

    public static void apply(Settings s, int preset) {
        switch (preset) {
            case 0 -> {                                   // clean, calm, readable
                s.font = FontStyle.MODERN.ordinal(); s.colour = 0xFFFFFFFF; s.colour2 = 0xFF9CA3AF; s.gradientMode = 1;
                s.glowStrength = 25; s.depthStyle = 0; s.backdrop = 0; s.reflection = 0; s.sparkles = false; s.underline = false; s.waveAmp = 0;
                s.transition = 0; s.intensity = 0; s.uiTheme = 5; s.sizePercent = 100; s.opacity = 100;
            }
            case 1 -> {                                   // neon
                s.font = FontStyle.TECH.ordinal(); s.colour = 0xFF22D3EE; s.colour2 = 0xFFE879F9; s.gradientMode = 2; s.gradientSpeed = 5;
                s.glowStrength = 85; s.depthStyle = 2; s.backdrop = 0; s.reflection = 30; s.sparkles = true; s.underline = false; s.waveAmp = 0;
                s.transition = 2; s.intensity = 1; s.uiTheme = 2; s.sizePercent = 110;
            }
            case 2 -> {                                   // cinema
                s.font = FontStyle.ELEGANT.ordinal(); s.colour = 0xFFFDE68A; s.colour2 = 0xFFFFFFFF; s.gradientMode = 4; s.gradientSpeed = 3;
                s.glowStrength = 40; s.depthStyle = 0; s.backdrop = 2; s.reflection = 40; s.sparkles = false; s.underline = true; s.waveAmp = 0;
                s.transition = 1; s.intensity = 0; s.uiTheme = 5; s.sizePercent = 120; s.displayMode = 1;
            }
            case 3 -> {                                   // retro arcade
                s.font = FontStyle.MONOBOLD.ordinal(); s.colour = 0xFFA3E635; s.colour2 = 0xFF22D3EE; s.gradientMode = 6; s.gradientSpeed = 3;
                s.glowStrength = 0; s.depthStyle = 1; s.depthAmount = 3; s.lightAngle = 45; s.backdrop = 3; s.reflection = 0; s.sparkles = false;
                s.underline = true; s.waveAmp = 2; s.transition = 3; s.intensity = 2; s.uiTheme = 3; s.sizePercent = 100;
            }
            case 4 -> {                                   // competitive: small quiet lyrics, PvP info on, lean visuals
                lean(s);
                widgetsOff(s);
                s.target.on = true; s.cps.on = true; s.keys.on = true; s.armor.on = true; s.effects.on = true; s.items.on = true;
                s.reach.on = true; s.combo.on = true; s.lowHealthWarn = true; s.lowArmorFlash = true;
                s.surgeProfile = "PVP"; s.surgeAdaptive = true; s.occludeMobs = true; s.occludeBlocks = true;
            }
            case 5 -> look(s, FontStyle.DISPLAY, 0xFFFB923C, 0xFFF472B6, 2, 3, 60, 0, 0, 25, false, false, 1, 1, 4, 105, 0);        // sunset
            case 6 -> look(s, FontStyle.GROTESK, 0xFF38BDF8, 0xFF2563EB, 2, 3, 45, 0, 1, 35, false, false, 0, 1, 1, 105, 0);        // ocean
            case 7 -> look(s, FontStyle.ITALIC, 0xFFC7D2FE, 0xFF818CF8, 4, 3, 50, 0, 2, 20, false, false, 1, 0, 9, 110, 1);         // midnight
            case 8 -> look(s, FontStyle.SCRIPT, 0xFFF9A8D4, 0xFFFFFFFF, 2, 3, 55, 0, 0, 20, true, false, 0, 1, 4, 115, 0);          // sakura
            case 9 -> {                                                                                                               // gold
                look(s, FontStyle.CLASSIC, 0xFFFDE68A, 0xFFD97706, 4, 2, 35, 1, 2, 25, false, true, 1, 0, 7, 110, 1);
                s.depthAmount = 2; s.lightAngle = 45;
            }
            case 10 -> look(s, FontStyle.CODE, 0xFF4ADE80, 0xFF16A34A, 5, 4, 30, 0, 3, 0, false, false, 4, 0, 11, 95, 2);          // hacker
            case 11 -> look(s, FontStyle.TECHNO, 0xFFF0ABFC, 0xFF67E8F9, 2, 4, 80, 2, 0, 40, true, false, 2, 1, 2, 110, 0);        // vapor
            case 12 -> {                                                                                                              // minimal
                look(s, FontStyle.NARROW, 0xFFFFFFFF, 0xFFE5E7EB, 0, 3, 0, 0, 0, 0, false, false, 0, 0, 5, 90, 1);
                s.showBar = false; s.opacity = 85;
            }
            case 13 -> {                                                                                                              // PvP Lite
                lean(s); widgetsOff(s);
                s.target.on = true; s.effects.on = true; s.cps.on = true; s.lowHealthWarn = true;
                s.surgeProfile = "PVP"; s.surgeAdaptive = true;
            }
            case 14 -> {                                                                                                              // PvP Crystal
                lean(s); widgetsOff(s);
                s.target.on = true; s.effects.on = true; s.items.on = true; s.health.on = true; s.cps.on = true;
                s.itemTotem = true; s.itemGapple = true; s.itemPearl = true; s.itemCrystal = true; s.itemXp = true; s.itemArrow = false;
                s.itemsHideZero = false; s.lowHealthWarn = true; s.lowTotemWarn = true; s.armor.on = true; s.lowArmorFlash = true;
                s.surgeProfile = "PVP"; s.surgeAdaptive = true;
            }
            case 15 -> {                                                                                                              // PvP Sword
                lean(s); widgetsOff(s);
                s.target.on = true; s.health.on = true; s.keys.on = true; s.reach.on = true; s.combo.on = true; s.armor.on = true;
                s.cps.on = true; s.stance.on = true; s.lowHealthWarn = true; s.lowArmorFlash = true; s.targetArmor = true; s.targetDistance = true;
                s.surgeProfile = "PVP"; s.surgeAdaptive = true;
            }
            case 16 -> {                                                                                                              // PvP Archer
                lean(s); widgetsOff(s);
                s.target.on = true; s.items.on = true; s.speed.on = true; s.coords.on = true; s.effects.on = true; s.health.on = true;
                s.targetSource = 2; s.itemArrow = true; s.itemPearl = true; s.itemTotem = false; s.itemGapple = false; s.itemCrystal = false;
                s.zoom = true; s.zoomFactor = 6; s.lowHealthWarn = true;
                s.surgeProfile = "PVP"; s.surgeAdaptive = true;
            }
            default -> {                                                                                                              // PvP Full
                lean(s); widgetsOff(s);
                for (HudWidget w : s.pvpWidgets()) w.on = true;
                s.keys.on = true; s.armor.on = true; s.reach.on = true; s.combo.on = true; s.timer.on = false;
                s.itemTotem = true; s.itemGapple = true; s.itemPearl = true; s.itemCrystal = true; s.itemXp = false; s.itemArrow = true;
                s.lowHealthWarn = true; s.lowArmorFlash = true; s.lowTotemWarn = true; s.zoom = true;
                s.surgeProfile = "PVP"; s.surgeAdaptive = true;
            }
        }
        s.clamp();
    }

    /** A "look" pack: only lyric style, colours, effects and the window theme. */
    private static void look(Settings s, FontStyle f, int c1, int c2, int gradient, int gradientSpeed, int glow, int depth, int backdrop,
                             int reflection, boolean sparkles, boolean underline, int wave, int transition, int theme, int size, int displayMode) {
        s.font = f.ordinal(); s.colour = c1; s.colour2 = c2; s.gradientMode = gradient; s.gradientSpeed = gradientSpeed;
        s.glowStrength = glow; s.depthStyle = depth; s.backdrop = backdrop; s.reflection = reflection; s.sparkles = sparkles;
        s.underline = underline; s.waveAmp = wave; s.transition = transition; s.intensity = 1; s.uiTheme = theme;
        s.sizePercent = size; s.displayMode = displayMode; s.opacity = 100;
    }

    /** Small, quiet lyrics tucked at the top so the fight stays readable. */
    private static void lean(Settings s) {
        s.font = FontStyle.DEFAULT.ordinal(); s.colour = 0xFFFFFFFF; s.colour2 = 0xFF9CA3AF; s.gradientMode = 0;
        s.glowStrength = 0; s.depthStyle = 0; s.backdrop = 0; s.reflection = 0; s.sparkles = false; s.underline = false; s.waveAmp = 0;
        s.transition = 0; s.intensity = 0; s.uiTheme = 5; s.sizePercent = 70; s.anchor = 1; s.offX = 0; s.offY = 4; s.displayMode = 1;
        s.showBar = false; s.glassOpacity = 80; s.opacity = 100;
        s.occludeMobs = true; s.occludeBlocks = true;
    }

    /** Every information widget and alert off, so each loadout starts from a clean slate. */
    private static void widgetsOff(Settings s) {
        for (HudWidget w : s.pvpWidgets()) w.on = false;
        for (HudWidget w : s.widgets()) if (w != s.ping && w != s.timer && w != s.hotbar) w.on = false;
        s.lowHealthWarn = false; s.lowArmorFlash = false; s.lowTotemWarn = false;
    }
}
