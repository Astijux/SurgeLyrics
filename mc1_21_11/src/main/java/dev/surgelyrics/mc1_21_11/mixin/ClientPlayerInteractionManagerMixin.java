package dev.surgelyrics.mc1_21_11.mixin;

import dev.surgelyrics.core.Reach;
import dev.surgelyrics.mc1_21_11.Runtime;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    // Feeds the reach and combo widgets. VERIFY: attackEntity(PlayerEntity, Entity) in yarn 1.21.11.
    @Inject(method = "attackEntity", at = @At("HEAD"), require = 0)
    private void surgelyrics$attack(PlayerEntity player, Entity target, CallbackInfo ci) {
        try {
            Vec3d eye = player.getEyePos();
            Box b = target.getBoundingBox();
            double reach = Reach.distance(eye.x, eye.y, eye.z, b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ);
            Runtime.COMBAT.hit(System.currentTimeMillis(), reach);
        } catch (RuntimeException ignored) { /* a stats widget must never break combat */ }
    }
}
