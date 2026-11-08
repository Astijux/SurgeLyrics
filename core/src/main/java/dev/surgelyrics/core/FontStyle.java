package dev.surgelyrics.core;

/** Lyric fonts. DEFAULT is the game font; the rest are bundled OFL fonts, referenced by font id "surgelyrics:<key>". */
public enum FontStyle {
    DEFAULT("Default", null),
    SCRIPT("Script", "script"),
    ITALIC("Serif Italic", "italic"),
    ELEGANT("Elegant", "elegant"),
    DECO("Deco", "deco"),
    MODERN("Modern", "modern"),
    TECH("Tech", "tech"),
    PIXEL("Pixel", "pixel");

    public final String label;
    public final String key;

    FontStyle(String label, String key) { this.label = label; this.key = key; }

    /** Namespaced font id, or null for the game font. */
    public String fontId() { return key == null ? null : "surgelyrics:" + key; }

    public static FontStyle of(int i) {
        FontStyle[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
