package dev.surgelyrics.core;

import java.util.List;

/** Decides what to show and how opaque it is. Pure math: every Minecraft version draws the same view. */
public final class LyricsView {
    private LyricsView() {}

    /** @param before previous line (scroll mode); @param fill 0..1 how much of the line is sung (karaoke mode) */
    public record View(String text, float alpha, String next, float progress, String before, float fill) {}
    public static final View EMPTY = new View("", 0f, "", 0f, "", 0f);
    public static final String[] MODES = {"Chunk", "Line", "Type", "Sing", "Scroll"};

    /** Display modes: 0 chunks, 1 whole line, 2 typewriter, 3 karaoke fill, 4 scrolling stack. */
    public static View compute(List<Lrc.Line> lines, long posMs, int mode, int wordsPerLine, float staySeconds) {
        if (mode <= 0) return compute(lines, posMs, wordsPerLine, staySeconds);
        int idx = Lrc.indexAt(lines, posMs);
        if (idx < 0) return EMPTY;
        Lrc.Line cur = lines.get(idx);
        boolean hasNext = idx + 1 < lines.size();
        long nextStart = hasNext ? lines.get(idx + 1).timeMs() : Long.MAX_VALUE;
        long elapsed = Math.max(0, posMs - cur.timeMs());
        long stay = (long) (staySeconds * 1000);
        long span = Math.max(600, Math.min(hasNext ? nextStart - cur.timeMs() : stay, 8000));
        long gap = hasNext ? nextStart - cur.timeMs() : Long.MAX_VALUE;
        float out = gap > stay ? 1f - clamp01((elapsed - stay) / 400f) : 1f;
        float a = easeOutCubic(clamp01(elapsed / 250f)) * out;
        String text = cur.text().strip();
        float fill = 0f;
        if (mode == 2) {
            int n = text.length();
            int shown = (int) Math.ceil(n * clamp01(elapsed / (span * 0.75f)));
            text = text.substring(0, Math.max(1, Math.min(n, shown)));
            a = clamp01(elapsed / 120f) * out;
        } else if (mode == 3) {
            fill = clamp01(elapsed / (span * 0.9f));
        }
        String next = hasNext ? lines.get(idx + 1).text() : "";
        String before = idx > 0 ? lines.get(idx - 1).text() : "";
        float prog = hasNext ? clamp01(elapsed / (float) Math.max(1, nextStart - cur.timeMs())) : 1f;
        return new View(text, a, next, prog, before, fill);
    }

    public static View compute(List<Lrc.Line> lines, long posMs, int wordsPerLine, float staySeconds) {
        int idx = Lrc.indexAt(lines, posMs);
        if (idx < 0) return EMPTY;
        Lrc.Line cur = lines.get(idx);
        boolean hasNext = idx + 1 < lines.size();
        long nextStart = hasNext ? lines.get(idx + 1).timeMs() : Long.MAX_VALUE;
        long elapsed = Math.max(0, posMs - cur.timeMs());
        long stay = (long) (staySeconds * 1000);

        String[] words = cur.text().strip().split("\\s+");
        int wpl = Math.max(1, wordsPerLine);
        int chunks = (words.length + wpl - 1) / wpl;
        long span = Math.max(600, Math.min(hasNext ? nextStart - cur.timeMs() : stay, 8000));
        int ci = (int) Math.min(chunks - 1, elapsed * chunks / span);
        long chunkStart = ci * span / chunks;

        StringBuilder sb = new StringBuilder();
        for (int i = ci * wpl; i < Math.min(words.length, (ci + 1) * wpl); i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(words[i]);
        }

        float in = clamp01((elapsed - chunkStart) / 250f);
        float out = 1f;
        long gap = hasNext ? nextStart - cur.timeMs() : Long.MAX_VALUE;
        if (gap > stay) out = 1f - clamp01((elapsed - stay) / 400f);       // long instrumental gap: fade away
        float a = easeOutCubic(in) * out;
        String next = hasNext ? lines.get(idx + 1).text() : "";
        float prog = hasNext ? clamp01(elapsed / (float) Math.max(1, nextStart - cur.timeMs())) : 1f;
        return new View(sb.toString(), a, next, prog, idx > 0 ? lines.get(idx - 1).text() : "", 0f);
    }

    public static float clamp01(float v) { return v < 0 ? 0 : Math.min(v, 1); }
    public static float easeOutCubic(float t) { float u = 1 - clamp01(t); return 1 - u * u * u; }

    public static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + (s % 60 < 10 ? "0" : "") + (s % 60);
    }

    public static int withAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * clamp01(alpha));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    public static int mix(int c1, int c2, float t) {
        t = clamp01(t);
        int a = (int) (((c1 >>> 24) & 255) * (1 - t) + ((c2 >>> 24) & 255) * t);
        int r = (int) (((c1 >> 16) & 255) * (1 - t) + ((c2 >> 16) & 255) * t);
        int g = (int) (((c1 >> 8) & 255) * (1 - t) + ((c2 >> 8) & 255) * t);
        int b = (int) ((c1 & 255) * (1 - t) + (c2 & 255) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
