package dev.surgelyrics.core;

/** Small formatting helpers for the HUD widgets. */
public final class Fmt {
    private Fmt() {}

    /** 83 seconds -> "1:23", 3725 seconds -> "1:02:05". */
    public static String duration(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0 ? h + ":" + two(m) + ":" + two(sec) : m + ":" + two(sec);
    }

    private static String two(long v) { return v < 10 ? "0" + v : String.valueOf(v); }

    public static String fixed(double v, int decimals) {
        decimals = Math.max(0, Math.min(3, decimals));
        double p = Math.pow(10, decimals);
        double r = Math.round(v * p) / p;
        String s = Double.toString(r);
        int dot = s.indexOf('.');
        if (decimals == 0) return dot < 0 ? s : s.substring(0, dot);
        if (dot < 0) { s += "."; dot = s.length() - 1; }
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() - dot - 1 < decimals) sb.append('0');
        return sb.substring(0, dot + 1 + decimals);
    }

    /** 1 -> "I", 4 -> "IV", 10 -> "X"; anything outside 1..10 falls back to the plain number (0 or less gives ""). */
    public static String roman(int n) {
        if (n <= 0) return "";
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n <= 10 ? r[n] : String.valueOf(n);
    }

    /** Minecraft yaw to compass: yaw 0 faces south, 90 west, 180 north, -90 (270) east. */
    public static String facing(float yaw) {
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        float y = ((yaw % 360f) + 360f) % 360f;
        return names[Math.round(y / 45f) % 8];
    }
}
