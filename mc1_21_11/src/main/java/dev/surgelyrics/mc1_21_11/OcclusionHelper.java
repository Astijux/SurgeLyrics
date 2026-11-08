package dev.surgelyrics.mc1_21_11;

import dev.surgelyrics.core.Occlusion;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.HashMap;
import java.util.Map;

/**
 * "Is this completely hidden behind solid blocks?" with a short per-thing cache, so a base full of mobs and chests
 * costs a few hundred block lookups per second instead of drawing everything. Never hides anything it is unsure
 * about: big entities, close things, unloaded chunks and see-through blocks all count as visible.
 */
public final class OcclusionHelper {
    private OcclusionHelper() {}

    private static final Map<Integer, Long> ENTITIES = new HashMap<>();
    private static final Map<Long, Long> BLOCKS = new HashMap<>();
    private static final double NEAR_SQ = 6.0 * 6.0;

    private static Occlusion.Solid solid(ClientWorld world) {
        BlockPos.Mutable mp = new BlockPos.Mutable();
        return (x, y, z) -> world.getBlockState(mp.set(x, y, z)).isOpaque();      // VERIFY: BlockState.isOpaque() in yarn 1.21.11
    }

    /** @param cx,cy,cz the camera position (EntityRenderManager.shouldRender receives it directly) */
    public static boolean entityHidden(Entity e, double cx, double cy, double cz) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return false;
        long now = System.currentTimeMillis();
        Long cached = ENTITIES.get(e.getId());
        if (cached != null && (cached >> 1) > now) return (cached & 1L) == 1L;

        Box b = e.getBoundingBox();
        double w = b.maxX - b.minX, h = b.maxY - b.minY, d = b.maxZ - b.minZ;
        double mx = (b.minX + b.maxX) / 2, my = (b.minY + b.maxY) / 2, mz = (b.minZ + b.maxZ) / 2;
        double dx = mx - cx, dy = my - cy, dz = mz - cz;
        boolean hidden = false;
        if (Math.max(w, Math.max(h, d)) <= 5.0 && dx * dx + dy * dy + dz * dz > NEAR_SQ) {
            double[] pts = {
                    mx, my, mz,  mx, b.maxY - 0.1, mz,  mx, b.minY + 0.1, mz,
                    b.minX + 0.1, my, mz,  b.maxX - 0.1, my, mz,  mx, my, b.minZ + 0.1,  mx, my, b.maxZ - 0.1};
            hidden = Occlusion.hidden(solid(world), cx, cy, cz, pts);
        }
        ENTITIES.put(e.getId(), ((now + 120 + (e.getId() & 31)) << 1) | (hidden ? 1L : 0L));
        if (ENTITIES.size() > 4096) ENTITIES.clear();
        return hidden;
    }

    /** @param ex,ey,ez the viewer's eye position */
    public static boolean blockEntityHidden(BlockEntity be, double ex, double ey, double ez) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return false;
        BlockPos p = be.getPos();
        long key = p.asLong();
        long now = System.currentTimeMillis();
        Long cached = BLOCKS.get(key);
        if (cached != null && (cached >> 1) > now) return (cached & 1L) == 1L;

        double mx = p.getX() + 0.5, my = p.getY() + 0.5, mz = p.getZ() + 0.5;
        double dx = mx - ex, dy = my - ey, dz = mz - ez;
        boolean hidden = false;
        if (dx * dx + dy * dy + dz * dz > NEAR_SQ) {
            double[] pts = {
                    mx, my, mz,  mx, p.getY() + 0.9, mz,  mx, p.getY() + 0.1, mz,
                    mx - 0.4, my, mz,  mx + 0.4, my, mz,  mx, my, mz - 0.4,  mx, my, mz + 0.4};
            hidden = Occlusion.hidden(solid(world), ex, ey, ez, pts);
        }
        BLOCKS.put(key, ((now + 150 + (int) (key & 31)) << 1) | (hidden ? 1L : 0L));
        if (BLOCKS.size() > 8192) BLOCKS.clear();
        return hidden;
    }
}
