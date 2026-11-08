package dev.surgelyrics.core;

/**
 * Line-of-sight test through a voxel grid (Amanatides-Woo traversal). Used to skip drawing things that are
 * completely hidden behind opaque blocks. Pure maths: the adapter supplies the block lookup.
 */
public final class Occlusion {
    private Occlusion() {}

    public interface Solid { boolean at(int x, int y, int z); }

    private static final int MAX_STEPS = 320;

    /** True if an opaque block lies between the two points (the start and target voxels themselves are ignored). */
    public static boolean blocked(Solid w, double ox, double oy, double oz, double tx, double ty, double tz) {
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int ex = (int) Math.floor(tx), ey = (int) Math.floor(ty), ez = (int) Math.floor(tz);
        if (x == ex && y == ey && z == ez) return false;
        double dx = tx - ox, dy = ty - oy, dz = tz - oz;
        int sx = Integer.signum((int) Math.signum(dx)), sy = Integer.signum((int) Math.signum(dy)), sz = Integer.signum((int) Math.signum(dz));
        double tdx = dx == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dx);
        double tdy = dy == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dy);
        double tdz = dz == 0 ? Double.POSITIVE_INFINITY : Math.abs(1.0 / dz);
        double mx = dx > 0 ? (x + 1 - ox) * tdx : dx < 0 ? (ox - x) * tdx : Double.POSITIVE_INFINITY;
        double my = dy > 0 ? (y + 1 - oy) * tdy : dy < 0 ? (oy - y) * tdy : Double.POSITIVE_INFINITY;
        double mz = dz > 0 ? (z + 1 - oz) * tdz : dz < 0 ? (oz - z) * tdz : Double.POSITIVE_INFINITY;
        int budget = Math.min(MAX_STEPS, Math.abs(ex - x) + Math.abs(ey - y) + Math.abs(ez - z) + 1);
        for (int i = 0; i < budget; i++) {
            if (mx <= my && mx <= mz) { x += sx; mx += tdx; }
            else if (my <= mz) { y += sy; my += tdy; }
            else { z += sz; mz += tdz; }
            if (x == ex && y == ey && z == ez) return false;     // reached the target without hitting anything
            if (w.at(x, y, z)) return true;
        }
        return false;                                              // gave up: never hide on uncertainty
    }

    /** Hidden only if EVERY sample point (flat x,y,z triples) is blocked from the eye. */
    public static boolean hidden(Solid w, double ox, double oy, double oz, double[] pts) {
        if (pts.length < 3) return false;
        for (int i = 0; i + 2 < pts.length; i += 3)
            if (!blocked(w, ox, oy, oz, pts[i], pts[i + 1], pts[i + 2])) return false;
        return true;
    }
}
