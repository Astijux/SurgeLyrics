package dev.surgelyrics.mc1_21_11;

import dev.surgelyrics.core.LyricsView;
import dev.surgelyrics.core.RoundRects;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.List;

/**
 * Liquid-glass look drawn with plain rectangles (inspired by quickLiquid's rim light, sheen and pill shapes).
 * The game already blurs the world behind menus; we add a tinted body, a curved rim that is bright at the
 * top-left and dim at the bottom-right, and a soft specular sheen across the upper half.
 */
final class Glass {
    private Glass() {}

    /** Global opacity for everything drawn this frame (used for the open animation). */
    static float alpha = 1f;

    /** HUD mode: a handful of big rectangles per panel instead of one per pixel row (large draw-call saving). */
    static boolean fast = false;

    /** When non-zero, the rim light is tinted toward this colour (used for the main window). */
    static int rimTint = 0;

    static int a(int argb) { return LyricsView.withAlpha(argb, alpha); }
    static int white(float a) { return LyricsView.withAlpha(0xFFFFFFFF, a); }
    private static int rimCol(float a) {
        return rimTint == 0 ? white(a) : LyricsView.withAlpha(LyricsView.mix(0xFFFFFFFF, 0xFF000000 | rimTint, 0.38f), a);
    }

    /** Horizontal inset of a rounded rect's corner at row dy. */
    static int inset(int r, int h, int dy) { return RoundRects.inset(r, h, dy); }

    /** Solid rounded rect, drawn as merged rectangles (about 2r+1 fills instead of h). */
    static void rrect(DrawContext c, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        int col = a(color);
        for (int[] q : RoundRects.rects(w, h, r)) c.fill(x + q[0], y + q[1], x + q[2], y + q[3], col);
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
        if (fast) { panelFast(c, x, y, w, h, r, fill, rim, sheen); return; }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        int ri = Math.max(0, r - 1);
        int body = a(fill);
        int sheenRows = Math.max(1, (int) (h * 0.5f));
        for (int dy = 0; dy < h; dy++) {
            int lo = inset(r, h, dy);
            int l = x + lo, rr = x + w - lo;
            float t = dy / (float) (h - 1);
            int rimL = a(rimCol(rim * (0.90f - 0.70f * t)));
            if (dy == 0 || dy == h - 1) { c.fill(l, y + dy, rr, y + dy + 1, rimL); continue; }
            int io = inset(ri, h - 2, dy - 1);
            int il = Math.max(l, x + 1 + io), ir = Math.min(rr, x + w - 1 - io);
            if (l < il) c.fill(l, y + dy, il, y + dy + 1, rimL);
            if (ir < rr) c.fill(ir, y + dy, rr, y + dy + 1, a(rimCol(rim * (0.50f - 0.38f * t))));
            if (il < ir) {
                c.fill(il, y + dy, ir, y + dy + 1, body);
                if (sheen > 0 && dy < sheenRows) {
                    float k = 1f - dy / (float) sheenRows;
                    c.fill(il, y + dy, ir, y + dy + 1, a(white(sheen * k * k)));
                }
            }
        }
    }

    /** Same look with a constant rim and a two-step sheen: roughly 15 fills per panel instead of 2 per row. */
    private static void panelFast(DrawContext c, int x, int y, int w, int h, int r, int fill, float rim, float sheen) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        int body = a(fill);
        List<int[]> rs = RoundRects.rects(w, h, r);
        for (int[] q : rs) c.fill(x + q[0], y + q[1], x + q[2], y + q[3], body);
        int top = a(rimCol(rim * 0.85f)), side = a(rimCol(rim * 0.45f)), bot = a(rimCol(rim * 0.22f));
        int in0 = RoundRects.inset(r, h, 0);
        c.fill(x + in0, y, x + w - in0, y + 1, top);
        c.fill(x + in0, y + h - 1, x + w - in0, y + h, bot);
        for (int[] q : rs) {
            int y1 = Math.max(q[1], 1), y2 = Math.min(q[3], h - 1);
            if (y1 >= y2) continue;
            c.fill(x + q[0], y + y1, x + q[0] + 1, y + y2, side);
            c.fill(x + q[2] - 1, y + y1, x + q[2], y + y2, side);
        }
        if (sheen > 0 && h > 6) {
            int inset = Math.max(2, r / 2 + 1), band = Math.max(2, h / 3);
            c.fill(x + inset, y + 1, x + w - inset, y + 1 + band, a(white(sheen * 0.45f)));
            c.fill(x + inset, y + 1, x + w - inset, y + 1 + band / 2, a(white(sheen * 0.45f)));
        }
    }

    /** A slow diagonal light sweep across a rounded surface. phase 0..1 moves it from left to right. */
    static void sweep(DrawContext c, int x, int y, int w, int h, int r, float phase) {
        int bandW = 44;
        float center = phase * (w + 2 * bandW) - bandW;
        for (int dy = 1; dy < h - 1; dy++) {
            int in = inset(r, h, dy) + 1;
            int lo = x + in, hi = x + w - in;
            int cx = x + Math.round(center + dy * 0.5f);
            int a0 = Math.max(lo, cx - bandW / 2), a1 = Math.min(hi, cx + bandW / 2);
            if (a0 < a1) c.fill(a0, y + dy, a1, y + dy + 1, a(white(0.035f)));
            int b0 = Math.max(lo, cx - 8), b1 = Math.min(hi, cx + 8);
            if (b0 < b1) c.fill(b0, y + dy, b1, y + dy + 1, a(white(0.045f)));
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
