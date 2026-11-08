package dev.surgelyrics.core;

import java.util.ArrayList;
import java.util.List;

/** Rounded rectangles as the fewest possible plain rectangles (consecutive rows with the same inset are merged). */
public final class RoundRects {
    private RoundRects() {}

    /** Horizontal inset of a rounded rect's corner at row dy (0 = no inset). */
    public static int inset(int r, int h, int dy) {
        if (r <= 0 || h <= 0) return 0;
        int d = Math.min(dy, h - 1 - dy);
        if (d >= r) return 0;
        double t = r - d - 0.5;
        return (int) Math.ceil(r - Math.sqrt(Math.max(0, r * (double) r - t * t)));
    }

    /** @return rectangles {x1, y1, x2, y2} relative to the shape's top-left; ~2r+1 of them instead of h. */
    public static List<int[]> rects(int w, int h, int r) {
        List<int[]> out = new ArrayList<>();
        if (w <= 0 || h <= 0) return out;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        int start = 0, cur = inset(r, h, 0);
        for (int dy = 1; dy <= h; dy++) {
            int in = dy < h ? inset(r, h, dy) : -1;
            if (in != cur) {
                out.add(new int[]{cur, start, w - cur, dy});
                start = dy;
                cur = in;
            }
        }
        return out;
    }
}
