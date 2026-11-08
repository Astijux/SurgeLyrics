package dev.surgelyrics.core;

import java.util.List;

/** Palettes, HSB conversion and per-character gradient colours. Pure maths, shared by all versions. */
public final class Colors {
    private Colors() {}

    public record Palette(String name, int c1, int c2) {}

    public static final List<Palette> PALETTES = List.of(
            new Palette("Glacier", 0xFF7DD3FC, 0xFFC084FC), new Palette("Sunset", 0xFFFB923C, 0xFFF472B6),
            new Palette("Aurora", 0xFF5EEAD4, 0xFF818CF8), new Palette("Neon", 0xFF22D3EE, 0xFFE879F9),
            new Palette("Forest", 0xFF86EFAC, 0xFFFDE68A), new Palette("Rose", 0xFFFDA4AF, 0xFFFFFFFF),
            new Palette("Ember", 0xFFFDE047, 0xFFEF4444), new Palette("Ocean", 0xFF38BDF8, 0xFF2563EB),
            new Palette("Candy", 0xFFF9A8D4, 0xFF93C5FD), new Palette("Mono", 0xFFFFFFFF, 0xFF9CA3AF),
            new Palette("Gold", 0xFFFDE68A, 0xFFD97706), new Palette("Toxic", 0xFFA3E635, 0xFF22D3EE));

    public static final String[] GRADIENT_MODES = {"Off", "Static", "Flow", "Rainbow"};

    /** @return {hue 0..1, saturation 0..1, brightness 0..1} */
    public static float[] toHsb(int argb) {
        float r = ((argb >> 16) & 255) / 255f, g = ((argb >> 8) & 255) / 255f, b = (argb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min, h;
        if (d == 0) h = 0;
        else if (max == r) h = (((g - b) / d) % 6 + 6) % 6 / 6f;
        else if (max == g) h = ((b - r) / d + 2) / 6f;
        else h = ((r - g) / d + 4) / 6f;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    public static int fromHsb(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        s = LyricsView.clamp01(s); v = LyricsView.clamp01(v);
        float c = v * s, x = c * (1 - Math.abs((h * 6) % 2 - 1)), m = v - c, r, g, b;
        switch ((int) (h * 6) % 6) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return 0xFF000000 | Math.round((r + m) * 255) << 16 | Math.round((g + m) * 255) << 8 | Math.round((b + m) * 255);
    }

    /** Colour of character i of n. mode: 0 off, 1 static, 2 flowing, 3 rainbow. time in seconds. */
    public static int gradient(int mode, int i, int n, float time, int c1, int c2, int speed) {
        float pos = n <= 1 ? 0 : i / (float) (n - 1);
        switch (mode) {
            case 1: return LyricsView.mix(c1, c2, pos);
            case 2: {
                float t = 0.5f + 0.5f * (float) Math.sin(2 * Math.PI * (pos * 0.9f - time * speed * 0.08f));
                return LyricsView.mix(c1, c2, t);
            }
            case 3: return fromHsb(pos + time * speed * 0.06f, 0.55f, 1f);
            default: return c1;
        }
    }

    /** Accepts "#abc", "abc", "#aabbcc" or "AABBCC" (case-insensitive). @return opaque ARGB, or null if invalid. */
    public static Integer parseHex(String text) {
        if (text == null) return null;
        String t = text.strip();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() == 3) t = "" + t.charAt(0) + t.charAt(0) + t.charAt(1) + t.charAt(1) + t.charAt(2) + t.charAt(2);
        if (t.length() != 6) return null;
        for (int i = 0; i < 6; i++) if (Character.digit(t.charAt(i), 16) < 0) return null;
        return 0xFF000000 | Integer.parseInt(t, 16);
    }

    public static String toHex(int argb) { return String.format("#%06X", argb & 0xFFFFFF); }

    /** Red -> yellow -> green for a 0..1 fraction (durability, health...). */
    public static int health(float pct) {
        pct = LyricsView.clamp01(pct);
        return pct > 0.5f ? LyricsView.mix(0xFFFACC15, 0xFF4ADE80, (pct - 0.5f) * 2f)
                          : LyricsView.mix(0xFFF87171, 0xFFFACC15, pct * 2f);
    }

    public static int pingColor(int ms) {
        return ms < 60 ? 0xFF4ADE80 : ms < 120 ? 0xFFFACC15 : ms < 200 ? 0xFFFB923C : 0xFFF87171;
    }

    /** Blend toward black by amount 0..1, keeping the colour's alpha. */
    public static int darken(int argb, float amount) {
        float k = 1f - LyricsView.clamp01(amount);
        return (argb & 0xFF000000) | Math.round(((argb >> 16) & 255) * k) << 16
                | Math.round(((argb >> 8) & 255) * k) << 8 | Math.round((argb & 255) * k);
    }
}
