package dev.surgelyrics.core;

/** Body tints for the glass surfaces (settings window and HUD panels). */
public final class Themes {
    private Themes() {}

    public record Theme(String name, int body) {}

    public static final Theme[] ALL = {
            new Theme("Midnight", 0x141A2A), new Theme("Ocean", 0x0F2A3A), new Theme("Plum", 0x2A163A),
            new Theme("Forest", 0x12291F), new Theme("Rose", 0x3A1822), new Theme("Slate", 0x22262E)};

    public static Theme of(int i) { return ALL[Math.max(0, Math.min(ALL.length - 1, i))]; }

    /** Glass body colour (ARGB) for a theme at 0..255 alpha, scaled by the user's glass opacity (25..100). */
    public static int body(int theme, int alpha, int opacityPercent) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * Math.max(0, Math.min(100, opacityPercent)) / 100f)));
        return (a << 24) | (of(theme).body & 0xFFFFFF);
    }
}
