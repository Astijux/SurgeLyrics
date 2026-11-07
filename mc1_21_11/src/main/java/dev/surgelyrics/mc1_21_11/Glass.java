package dev.surgelyrics.mc1_21_11;

import dev.surgelyrics.core.LyricsView;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Liquid-glass look drawn with plain rectangles (inspired by quickLiquid's rim light, sheen and pill shapes).
 * The game already blurs the world behind menus; we add a tinted body, a curved rim that is bright at the
 * top-left and dim at the bottom-right, and a soft specular sheen across the upper half.
 */
final class Glass {
    private Glass() {}

    /** Global opacity for everything drawn this frame (used for the open animation). */
    static float alpha = 1f;

    static int a(int argb) { return LyricsView.withAlpha(argb, alpha); }
    static int white(float a) { return LyricsView.withAlpha(0xFFFFFFFF, a); }

    /** Horizontal inset of a rounded rect's corner at row dy. */
    static int inset(int r, int h, int dy) {
        if (r <= 0 || h <= 0) return 0;
        int d = Math.min(dy, h - 1 - dy);
        if (d >= r) return 0;
        double t = r - d - 0.5;
        return (int) Math.ceil(r - Math.sqrt(Math.max(0, r * (double) r - t * t)));
    }

    /** Solid rounded rect. */
    static void rrect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        int col = a(color);
        for (int dy = 0; dy < h; dy++) {
            int in = inset(r, h, dy);
            c.fill(x + in, y + dy, x + w - in, y + dy + 1, col);
        }
    }

    /** Soft drop shadow made of a few expanding translucent layers. */
    static void shadow(DrawContext c, int x, int y, int w, int h, int r) {
        for (int i = 3; i >= 1; i--) rrect(c, x - i, y - i + 3, w + 2 * i, h + 2 * i, r + i, 0x0C000000);
    }

    /**
     * A glass surface.
     * @param fill  body colour (ARGB, usually translucent)
     * @param rim   rim light strength 0..1
     * @param sheen specular sheen strength 0..~0.3
     */
    static void panel(DrawContext c, int x, int y, int w, int h, int r, int fill, float rim, float sheen) {
        if (w <= 1 || h <= 1) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        int ri = Math.max(0, r - 1);
        int body = a(fill);
        int sheenRows = Math.max(1, (int) (h * 0.5f));
        for (int dy = 0; dy < h; dy++) {
            int lo = inset(r, h, dy);
            int l = x + lo, rr = x + w - lo;
            float t = dy / (float) (h - 1);
            int rimL = a(white(rim * (0.90f - 0.70f * t)));
            if (dy == 0 || dy == h - 1) { c.fill(l, y + dy, rr, y + dy + 1, rimL); continue; }
            int io = inset(ri, h - 2, dy - 1);
            int il = Math.max(l, x + 1 + io), ir = Math.min(rr, x + w - 1 - io);
            if (l < il) c.fill(l, y + dy, il, y + dy + 1, rimL);
            if (ir < rr) c.fill(ir, y + dy, rr, y + dy + 1, a(white(rim * (0.50f - 0.38f * t))));
            if (il < ir) {
                c.fill(il, y + dy, ir, y + dy + 1, body);
                if (sheen > 0 && dy < sheenRows) {
                    float k = 1f - dy / (float) sheenRows;
                    c.fill(il, y + dy, ir, y + dy + 1, a(white(sheen * k * k)));
                }
            }
        }
    }

    static void text(DrawContext c, TextRenderer f, String s, int x, int y, int color) {
        int col = a(color);
        if (((col >>> 24) & 0xFF) < 8) return;     // vanilla treats near-zero alpha as opaque, so skip instead
        c.drawText(f, s, x, y, col, true);
    }

    static void textRight(DrawContext c, TextRenderer f, String s, int rightX, int y, int color) {
        text(c, f, s, rightX - f.getWidth(s), y, color);
    }

    static void textCentered(DrawContext c, TextRenderer f, String s, int cx, int y, int color) {
        text(c, f, s, cx - f.getWidth(s) / 2, y, color);
    }

    static String fit(TextRenderer f, String s, int maxW) {
        if (f.getWidth(s) <= maxW) return s;
        int end = s.length();
        while (end > 1 && f.getWidth(s.substring(0, end) + "...") > maxW) end--;
        return s.substring(0, end) + "...";
    }
}
