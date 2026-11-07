package dev.surgelyrics.mc1_21_11;

import dev.surgelyrics.core.LyricsView;
import dev.surgelyrics.core.Motion;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.HashMap;
import java.util.Map;

/** Immediate-mode glass widgets: each call draws the control and returns its (possibly new) value. */
final class GlassUi {
    DrawContext c;
    TextRenderer font;
    float mx, my, dt;
    boolean down, pressed, released, wasDown;
    String active;
    private final Map<String, Motion.Spring> springs = new HashMap<>();

    void begin(DrawContext c, TextRenderer font, float mx, float my, boolean down, float dt) {
        this.c = c; this.font = font; this.mx = mx; this.my = my; this.dt = dt; this.down = down;
        pressed = down && !wasDown;
        released = !down && wasDown;
        wasDown = down;
        if (released) active = null;
    }

    boolean hover(float x, float y, float w, float h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    private Motion.Spring sp(String id, float init) { return springs.computeIfAbsent(id, k -> new Motion.Spring(init, 260f, 24f)); }

    float anim(String id, float target, float init) { return sp(id, init).step(dt, target); }

    private static int withA(int rgb, int alpha) { return (alpha << 24) | (rgb & 0xFFFFFF); }

    void label(String s, int x, int y, int color) { Glass.text(c, font, s, x, y, color); }

    boolean toggle(String id, int x, int y, boolean v, int accent) {
        int w = 28, h = 14;
        boolean hov = hover(x, y, w, h);
        float p = sp(id, v ? 1 : 0).step(dt, v ? 1 : 0);
        int fill = LyricsView.mix(0x38FFFFFF, withA(accent, 0xE6), LyricsView.clamp01(p));
        Glass.panel(c, x, y, w, h, 7, fill, hov ? 0.95f : 0.65f, 0.18f);
        int kx = x + 2 + Math.round((w - h) * p);
        Glass.panel(c, kx, y + 2, h - 4, h - 4, (h - 4) / 2, 0xF4FFFFFF, 0.4f, 0f);
        return (pressed && hov) ? !v : v;
    }

    int segmented(String id, int x, int y, int w, String[] opts, int sel, int accent) {
        int h = 16, n = opts.length;
        float sw = w / (float) n;
        boolean hov = hover(x, y, w, h);
        Glass.panel(c, x, y, w, h, 8, 0x34000000, 0.35f, 0f);
        float ix = sp(id, sel).step(dt, sel);
        Glass.panel(c, x + Math.round(ix * sw) + 1, y + 1, Math.round(sw) - 2, h - 2, 7, withA(accent, 0x78), 0.95f, 0.30f);
        for (int i = 0; i < n; i++) {
            int cx = x + Math.round((i + 0.5f) * sw);
            Glass.textCentered(c, font, opts[i], cx, y + 4, i == sel ? 0xFFFFFFFF : 0xFFAEB6C8);
        }
        if (pressed && hov) return Math.max(0, Math.min(n - 1, (int) ((mx - x) / sw)));
        return sel;
    }

    float slider(String id, int x, int y, int w, float v, float min, float max, float step, int accent) {
        boolean hov = hover(x - 4, y, w + 8, 16);
        if (pressed && hov) active = id;
        boolean dragging = id.equals(active) && down;
        if (dragging) {
            float nt = LyricsView.clamp01((mx - x - 6) / (float) (w - 12));
            v = min + nt * (max - min);
            v = Math.round(v / step) * step;
            v = Math.max(min, Math.min(max, v));
        }
        float t = (v - min) / (max - min);
        Glass.panel(c, x, y + 6, w, 4, 2, 0x40000000, 0.3f, 0f);
        Glass.panel(c, x, y + 6, Math.max(4, Math.round((w - 12) * t) + 6), 4, 2, withA(accent, 0xD9), 0.6f, 0f);
        int kx = x + Math.round((w - 12) * t);
        Glass.panel(c, kx, y + 2, 12, 12, 6, 0xF2FFFFFF, (hov || dragging) ? 1f : 0.6f, 0.28f);
        return v;
    }

    int swatches(String id, int x, int y, int[] cols, int cur) {
        int d = 12, gap = 6, res = cur;
        for (int i = 0; i < cols.length; i++) {
            int cx = x + i * (d + gap);
            boolean sel = cols[i] == cur, hov = hover(cx - 1, y - 1, d + 2, d + 2);
            float s = sp(id + i, sel ? 1 : 0).step(dt, sel ? 1 : 0);
            int g = Math.round(s * 2f);
            Glass.panel(c, cx - g, y - g, d + 2 * g, d + 2 * g, (d + 2 * g) / 2, cols[i], sel ? 1f : (hov ? 0.85f : 0.5f), 0.32f);
            if (pressed && hov) res = cols[i];
        }
        return res;
    }

    boolean button(String id, int x, int y, int w, int h, String label, int accent, boolean primary) {
        boolean hov = hover(x, y, w, h), held = hov && down;
        float p = sp(id, 0).step(dt, held ? 1 : 0);
        int in = Math.round(p * 1.5f);
        int fill = primary ? withA(accent, hov ? 0xC8 : 0xA0) : (hov ? 0x58FFFFFF : 0x30FFFFFF);
        Glass.panel(c, x + in, y + in, w - 2 * in, h - 2 * in, h / 2, fill, 0.95f, 0.26f);
        Glass.textCentered(c, font, label, x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
        return pressed && hov;
    }
}
