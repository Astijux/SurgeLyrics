package dev.surgelyrics.mc1_21_11;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.surgelyrics.core.Json;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

import java.util.*;
import java.util.function.Supplier;

/**
 * Builds styled Text (custom font, per-character colours) by going through the vanilla text-component JSON
 * codec. That keeps this independent of the Style/font API, which changed in recent versions. Results are cached.
 */
final class FancyText {
    private FancyText() {}

    private static final Map<String, Text> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Text> e) { return size() > 384; }
    };

    /** @param fontId "namespace:name" or null for the game font; @param rgb per-code-point colours, or null to use the draw colour */
    static synchronized Text build(String s, String fontId, int[] rgb) {
        if (fontId == null && rgb == null) return Text.literal(s);
        StringBuilder k = new StringBuilder().append(fontId).append('|').append(s);
        if (rgb != null) for (int c : rgb) k.append('|').append(Integer.toHexString(c & 0xFFFFFF));
        String key = k.toString();
        Text hit = CACHE.get(key);
        if (hit != null) return hit;
        Text made = make(s, fontId, rgb);
        CACHE.put(key, made);
        return made;
    }

    /**
     * Cached by a short variant key (mode, colours, time step ...) so a frame that matches the previous one costs
     * one map lookup and allocates no colour array. The supplier only runs on a miss.
     */
    static synchronized Text buildCached(String s, String fontId, String variant, Supplier<int[]> colours) {
        String key = fontId + "#" + variant + "#" + s;
        Text hit = CACHE.get(key);
        if (hit != null) return hit;
        Text made = make(s, fontId, colours.get());
        CACHE.put(key, made);
        return made;
    }

    private static Text make(String s, String fontId, int[] rgb) {
        if (fontId == null && rgb == null) return Text.literal(s);
        Text built;
        try {
            List<Object> parts = new ArrayList<>();
            parts.add("");
            int[] cps = s.codePoints().toArray();
            int i = 0;
            while (i < cps.length) {
                int j = i + 1;
                if (rgb != null) while (j < cps.length && j < rgb.length && (rgb[j] & 0xFFFFFF) == (rgb[i] & 0xFFFFFF)) j++;
                else j = cps.length;
                Map<String, Object> seg = new LinkedHashMap<>();
                seg.put("text", new String(cps, i, j - i));
                if (rgb != null && i < rgb.length) seg.put("color", String.format("#%06x", rgb[i] & 0xFFFFFF));
                if (fontId != null) seg.put("font", fontId);
                parts.add(seg);
                i = j;
            }
            built = TextCodecs.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Json.write(parts)))
                    .result().orElseGet(() -> Text.literal(s));
        } catch (Throwable t) {
            built = Text.literal(s);       // never crash the HUD over a font problem
        }
        return built;
    }
}
