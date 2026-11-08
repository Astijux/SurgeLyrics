package dev.surgelyrics.core;

/** Where lyrics go on screen: a 3x3 anchor grid plus pixel offsets. */
public final class Layout {
    private Layout() {}

    public static final String[] ANCHORS = {"Top left", "Top", "Top right", "Left", "Center", "Right", "Bottom left", "Bottom", "Bottom right"};
    public static final int MARGIN = 10;

    /** @return {centerX, centerY} of a box bw x bh, kept fully on screen. */
    public static int[] place(int anchor, int offX, int offY, int sw, int sh, int bw, int bh) {
        anchor = Math.max(0, Math.min(8, anchor));
        int col = anchor % 3, row = anchor / 3;
        int cx = col == 0 ? MARGIN + bw / 2 : col == 1 ? sw / 2 : sw - MARGIN - bw / 2;
        int cy = row == 0 ? MARGIN + bh / 2 : row == 1 ? sh / 2 : sh - MARGIN - bh / 2;
        cx += offX; cy += offY;
        cx = bw >= sw ? sw / 2 : Math.max(bw / 2, Math.min(sw - bw / 2, cx));
        cy = bh >= sh ? sh / 2 : Math.max(bh / 2, Math.min(sh - bh / 2, cy));
        return new int[]{cx, cy};
    }
}
