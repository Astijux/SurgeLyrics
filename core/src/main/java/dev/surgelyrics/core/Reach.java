package dev.surgelyrics.core;

/** Distance from the eye to the nearest point of a target's hitbox. */
public final class Reach {
    private Reach() {}

    public static double distance(double ex, double ey, double ez,
                                  double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double dx = Math.max(Math.max(minX - ex, 0), ex - maxX);
        double dy = Math.max(Math.max(minY - ey, 0), ey - maxY);
        double dz = Math.max(Math.max(minZ - ez, 0), ez - maxZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
