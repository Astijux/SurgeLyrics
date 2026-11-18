package dev.surgelyrics.mc1_21_11;

import dev.surgelyrics.core.FontStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

/**
 * Font safety net. A font is only used if its .json and .ttf are really present in the loaded resources;
 * otherwise lyrics fall back to the default font instead of drawing nothing, and the settings screen says so.
 */
final class Fonts {
    private Fonts() {}

    private static ResourceManager lastManager;
    private static final Map<FontStyle, Boolean> OK = new EnumMap<>(FontStyle.class);
    private static boolean logged;

    static synchronized boolean available(FontStyle f) {
        if (f.key == null) return true;
        ResourceManager rm = MinecraftClient.getInstance().getResourceManager();
        if (rm != lastManager) { OK.clear(); lastManager = rm; logged = false; }      // resources reloaded: check again
        return OK.computeIfAbsent(f, k -> present(rm, k));
    }

    private static boolean present(ResourceManager rm, FontStyle f) {
        try {
            return rm.getResource(Identifier.of("surgelyrics", "font/" + f.key + ".json")).isPresent()
                    && rm.getResource(Identifier.of("surgelyrics", "font/" + f.key + ".ttf")).isPresent();
        } catch (Throwable t) {
            return true;                                   // can't tell, so don't punish the font
        }
    }

    /** Font id to draw with, or null (game font) if the chosen font's files are missing. */
    static String id(int index) { return idOf(FontStyle.of(index)); }

    static String idOf(FontStyle f) { return available(f) ? f.fontId() : null; }

    static int missing() {
        int n = 0;
        for (FontStyle f : FontStyle.values()) if (!available(f)) n++;
        return n;
    }

    /** Writes the list of missing fonts to the log, once per resource load. */
    static synchronized void logOnce() {
        if (logged) return;
        available(FontStyle.DEFAULT);                      // makes sure the manager check ran
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (FontStyle f : FontStyle.values()) if (!available(f)) { n++; sb.append(f.key).append(' '); }
        logged = true;
        if (n > 0) Safe.note(n + " font(s) are missing from this build and will use the default font: " + sb.toString().trim()
                + ". If you uploaded the project to GitHub by hand, files past 100 per upload are silently dropped: upload surgelyrics.zip instead (see README).");
    }
}
