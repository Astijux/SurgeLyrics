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
    PIXEL("Pixel", "pixel"),
    CRIMSON("Crimson Italic", "crimson"),
    CLASSIC("Classic", "classic"),
    DISPLAY("Display", "display"),
    YOUNG("Young Serif", "young"),
    SMALLCAPS("Small Caps", "smallcaps"),
    HEAVY("Heavy", "heavy"),
    IMPACT("Impact", "impact"),
    NARROW("Narrow", "narrow"),
    WIDE("Wide", "wide"),
    GROTESK("Grotesk", "grotesk"),
    WORK("Work Sans", "work"),
    TECHNO("Techno", "techno"),
    MONO("Mono", "mono"),
    CODE("Code", "code"),
    PIXELSANS("Pixel Sans", "pixelsans"),
    SANSITALIC("Sans Italic", "sansitalic"),
    BOLDITALIC("Bold Italic", "bolditalic"),
    CRIMSONBOLD("Crimson Bold", "crimsonbold"),
    WORKITALIC("Work Italic", "workitalic"),
    WORKBOLDITALIC("Work Bold Italic", "workbolditalic"),
    OUTFITBOLD("Outfit Bold", "outfitbold"),
    BRICOLAGEBOLD("Bricolage Bold", "bricolagebold"),
    JURALIGHT("Jura Light", "juralight"),
    SHOULDERS("Shoulders", "shoulders"),
    NATIONALPARK("National Park", "nationalpark"),
    DMMONO("DM Mono", "dmmono"),
    PLEXMONO("Plex Mono", "plexmono"),
    REDHAT("Red Hat Mono", "redhat"),
    MONOBOLD("Mono Bold", "monobold");

    public final String label;
    public final String key;

    FontStyle(String label, String key) { this.label = label; this.key = key; }

    /** Namespaced font id, or null for the game font. */
    public String fontId() { return key == null ? null : "surgelyrics:" + key; }

    public static final int PER_PAGE = 8;

    public static int pages() { return (values().length + PER_PAGE - 1) / PER_PAGE; }

    public static FontStyle of(int i) {
        FontStyle[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
