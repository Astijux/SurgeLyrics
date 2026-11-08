package dev.surgelyrics.mc1_21_11.mixin;

import dev.surgelyrics.core.SurgeGovernor;
import dev.surgelyrics.mc1_21_11.Runtime;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderManager.class)
public class EntityRenderManagerMixin {
    // VERIFY: target name/signature against yarn for 1.21.11 (Surge 1.0.0 hooked the same method).
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void surgelyrics$cull(Entity entity, Frustum frustum, double x, double y, double z,
                                  CallbackInfoReturnable<Boolean> cir) {
        SurgeGovernor g = Runtime.SURGE;
        if (!g.active()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerEntity me = mc.player;
        // Never hide: you, the camera, other players, glowing things, bosses, your mount/riders and your own pets.
        if (entity == me || entity == mc.getCameraEntity() || entity instanceof PlayerEntity || entity.isGlowing()) return;
        if (entity instanceof EnderDragonEntity || entity instanceof WitherEntity) return;
        if (me != null) {
            if (entity == me.getVehicle() || entity.hasPassenger(me)) return;
            if (entity instanceof TameableEntity pet && pet.isOwner(me)) return;
        }
        double d = entity.squaredDistanceTo(x, y, z);
        boolean far;
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrbEntity) far = g.cullItem(d);
        else if (entity instanceof AbstractDecorationEntity || entity instanceof ArmorStandEntity) far = g.cullDecoration(d);
        else far = g.cullMob(d);
        if (far) cir.setReturnValue(false);
    }
}
